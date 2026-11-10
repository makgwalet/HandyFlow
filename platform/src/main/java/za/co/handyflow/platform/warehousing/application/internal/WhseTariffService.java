package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Storage tariffs and minimum charges as effective-dated versions. A change is a new version that starts today or later;
 * a past day can never be re-priced here (corrections go through invoice credits). Every new version is audited.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseTariffService implements WhseTariffBookSource {

    private final JdbcTemplate jdbc;

    /** Same billing zone the accrual job cuts days in. */
    @org.springframework.beans.factory.annotation.Value("${handyflow.warehousing.billing-zone:Africa/Johannesburg}")
    private String billingZone = "Africa/Johannesburg";

    public record TariffRow(UUID id, UUID clientId, UUID itemId, BigDecimal monthlyRate, LocalDate effectiveFrom, String note, Instant createdAt) {}
    public record TermsRow(UUID id, UUID clientId, BigDecimal minimumStorageCharge, int freeDays, BigDecimal locationFeePerMonth,
                           LocalDate effectiveFrom, String note, Instant createdAt) {}

    @Override
    public WhseTariffBook bookFor(UUID tenantId) {
        return book(TenantId.of(tenantId));
    }

    @Transactional(readOnly = true)
    WhseTariffBook book(TenantId tenantId) {
        List<WhseTariffBook.TariffVersion> tariffs = jdbc.query(
                "SELECT id, client_id, item_id, monthly_rate, effective_from, created_at FROM whse_storage_tariffs WHERE tenant_id = ?",
                (rs, i) -> new WhseTariffBook.TariffVersion(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class),
                        rs.getObject("item_id", UUID.class), rs.getBigDecimal("monthly_rate"),
                        rs.getDate("effective_from").toLocalDate(), rs.getTimestamp("created_at").toInstant()), tenantId.getValue());
        List<WhseTariffBook.TermsVersion> terms = jdbc.query(
                "SELECT id, client_id, minimum_storage_charge, free_days, location_fee_per_month, effective_from, created_at FROM whse_billing_terms WHERE tenant_id = ?",
                (rs, i) -> new WhseTariffBook.TermsVersion(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class),
                        rs.getBigDecimal("minimum_storage_charge"), rs.getInt("free_days"), rs.getBigDecimal("location_fee_per_month"),
                        rs.getDate("effective_from").toLocalDate(),
                        rs.getTimestamp("created_at").toInstant()), tenantId.getValue());
        return new WhseTariffBook(tariffs, terms);
    }

    /** Minimum storage charge per 30 days in force for the client on that day (0 when none). */
    @Transactional(readOnly = true)
    public BigDecimal minimumStorageCharge(TenantId tenantId, UUID clientId, LocalDate day) {
        return book(tenantId).minimumStorageCharge(clientId, day);
    }

    @Transactional(readOnly = true)
    public List<TariffRow> tariffs(TenantId tenantId, UUID clientId) {
        return jdbc.query("""
                SELECT id, client_id, item_id, monthly_rate, effective_from, note, created_at FROM whse_storage_tariffs
                 WHERE tenant_id = ? AND (? IS NULL OR client_id = ? OR client_id IS NULL)
                 ORDER BY effective_from DESC, created_at DESC LIMIT 500""",
                (rs, i) -> new TariffRow(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class), rs.getObject("item_id", UUID.class),
                        rs.getBigDecimal("monthly_rate"), rs.getDate("effective_from").toLocalDate(), rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId.getValue(), clientId, clientId);
    }

    @Transactional(readOnly = true)
    public List<TermsRow> terms(TenantId tenantId, UUID clientId) {
        return jdbc.query("""
                SELECT id, client_id, minimum_storage_charge, free_days, location_fee_per_month, effective_from, note, created_at FROM whse_billing_terms
                 WHERE tenant_id = ? AND (? IS NULL OR client_id = ? OR client_id IS NULL)
                 ORDER BY effective_from DESC, created_at DESC LIMIT 500""",
                (rs, i) -> new TermsRow(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class),
                        rs.getBigDecimal("minimum_storage_charge"), rs.getInt("free_days"), rs.getBigDecimal("location_fee_per_month"),
                        rs.getDate("effective_from").toLocalDate(), rs.getString("note"),
                        rs.getTimestamp("created_at").toInstant()),
                tenantId.getValue(), clientId, clientId);
    }

    @Transactional
    public UUID addTariff(TenantId tenantId, UUID clientId, UUID itemId, BigDecimal monthlyRate, LocalDate effectiveFrom, String note, UUID actor) {
        return addTariff(tenantId, clientId, itemId, monthlyRate, effectiveFrom, note, actor, null);
    }

    /** As above; {@code approvedBy} is the second person when the change went through approval (the version is credited to {@code actor}, who asked). */
    @Transactional
    public UUID addTariff(TenantId tenantId, UUID clientId, UUID itemId, BigDecimal monthlyRate, LocalDate effectiveFrom, String note, UUID actor, UUID approvedBy) {
        requireActor(actor);
        if (monthlyRate == null || monthlyRate.signum() < 0) throw new IllegalArgumentException("The monthly rate cannot be negative.");
        requireNotBackdated(effectiveFrom);
        if (itemId != null && clientId == null) throw new IllegalArgumentException("An item rate needs its client.");
        requireClient(tenantId, clientId);
        if (itemId != null) requireItemOfClient(tenantId, clientId, itemId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_storage_tariffs (id, tenant_id, client_id, item_id, monthly_rate, effective_from, note, created_by) VALUES (?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), clientId, itemId, monthlyRate, effectiveFrom, trim(note), actor);
        audit(tenantId, actor, "WHSE_STORAGE_TARIFF_ADDED", "WHSE_STORAGE_TARIFF", id,
                "{\"clientId\":" + q(clientId) + ",\"itemId\":" + q(itemId) + ",\"monthlyRate\":" + monthlyRate.toPlainString() + ",\"effectiveFrom\":\"" + effectiveFrom + "\",\"approvedBy\":" + q(approvedBy) + "}");
        return id;
    }

    @Transactional
    public UUID addTerms(TenantId tenantId, UUID clientId, BigDecimal minimumStorageCharge, LocalDate effectiveFrom, String note, UUID actor) {
        return addTerms(tenantId, clientId, minimumStorageCharge, effectiveFrom, note, actor, null);
    }

    /** As above; {@code approvedBy} is the second person when the change went through approval. */
    @Transactional
    public UUID addTerms(TenantId tenantId, UUID clientId, BigDecimal minimumStorageCharge, LocalDate effectiveFrom, String note, UUID actor, UUID approvedBy) {
        return addTerms(tenantId, clientId, minimumStorageCharge, null, null, effectiveFrom, note, actor, approvedBy);
    }

    /**
     * Adds a terms version: minimum charge per 30 days, free storage days and a fixed monthly fee per occupied pallet/bin.
     * A version is a full snapshot, so any value left null carries forward from the terms in force for this client on the start
     * date (the client's own, else the warehouse default's, else zero).
     */
    @Transactional
    public UUID addTerms(TenantId tenantId, UUID clientId, BigDecimal minimumStorageCharge, Integer freeDays, BigDecimal locationFeePerMonth,
                         LocalDate effectiveFrom, String note, UUID actor, UUID approvedBy) {
        requireActor(actor);
        precheckTerms(tenantId, clientId, minimumStorageCharge, freeDays, locationFeePerMonth, effectiveFrom);
        WhseTariffBook.Terms current = book(tenantId).termsFor(clientId, effectiveFrom);
        BigDecimal min = minimumStorageCharge != null ? minimumStorageCharge : current.minimumStorageCharge();
        int days = freeDays != null ? freeDays : current.freeDays();
        BigDecimal fee = locationFeePerMonth != null ? locationFeePerMonth : current.locationFeePerMonth();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_billing_terms (id, tenant_id, client_id, minimum_storage_charge, free_days, location_fee_per_month, effective_from, note, created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), clientId, min, days, fee, effectiveFrom, trim(note), actor);
        audit(tenantId, actor, "WHSE_BILLING_TERMS_ADDED", "WHSE_BILLING_TERMS", id,
                "{\"clientId\":" + q(clientId) + ",\"minimumStorageCharge\":" + min.toPlainString() + ",\"freeDays\":" + days
                        + ",\"locationFeePerMonth\":" + fee.toPlainString() + ",\"effectiveFrom\":\"" + effectiveFrom + "\",\"approvedBy\":" + q(approvedBy) + "}");
        return id;
    }

    // ── internals ───────────────────────────────────────────────────────────

    /** Checks a tariff change would be accepted, without writing it; used when a change is put up for approval. */
    void precheckTariff(TenantId tenantId, UUID clientId, UUID itemId, BigDecimal monthlyRate, LocalDate effectiveFrom) {
        if (monthlyRate == null || monthlyRate.signum() < 0) throw new IllegalArgumentException("The monthly rate cannot be negative.");
        requireNotBackdated(effectiveFrom);
        if (itemId != null && clientId == null) throw new IllegalArgumentException("An item rate needs its client.");
        requireClient(tenantId, clientId);
        if (itemId != null) requireItemOfClient(tenantId, clientId, itemId);
    }

    void precheckTerms(TenantId tenantId, UUID clientId, BigDecimal minimumStorageCharge, Integer freeDays, BigDecimal locationFeePerMonth, LocalDate effectiveFrom) {
        if (minimumStorageCharge == null && freeDays == null && locationFeePerMonth == null) {
            throw new IllegalArgumentException("Give at least one of: minimum charge, free days, pallet/bin fee.");
        }
        if (minimumStorageCharge != null && minimumStorageCharge.signum() < 0) throw new IllegalArgumentException("The minimum charge cannot be negative.");
        if (freeDays != null && (freeDays < 0 || freeDays > 3650)) throw new IllegalArgumentException("Free days must be between 0 and 3650.");
        if (locationFeePerMonth != null && locationFeePerMonth.signum() < 0) throw new IllegalArgumentException("The pallet/bin fee cannot be negative.");
        requireNotBackdated(effectiveFrom);
        requireClient(tenantId, clientId);
    }

    void requireNotBackdated(LocalDate effectiveFrom) {
        if (effectiveFrom == null) throw new IllegalArgumentException("Say when the change starts.");
        if (effectiveFrom.isBefore(LocalDate.now(java.time.ZoneId.of(billingZone)))) {
            throw new IllegalArgumentException("A tariff or term cannot start in the past. Past days keep the rate they were charged; correct an invoice with a credit.");
        }
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this change cannot be recorded.");
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientId == null) return;
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_clients WHERE id = ? AND tenant_id = ?", Integer.class, clientId, tenantId.getValue());
        if (n == null || n == 0) throw new ResourceNotFoundException("Client", clientId.toString());
    }

    private void requireItemOfClient(TenantId tenantId, UUID clientId, UUID itemId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM whse_items WHERE id = ? AND tenant_id = ? AND client_id = ?", Integer.class, itemId, tenantId.getValue(), clientId);
        if (n == null || n == 0) throw new ResourceNotFoundException("Item", itemId.toString());
    }

    private void audit(TenantId tenantId, UUID actor, String action, String itemType, UUID itemId, String afterJson) {
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?, ?::jsonb)",
                tenantId.getValue(), actor, name, action, itemType, itemId, afterJson);
        log.info("[Warehousing] {} tenant={} by={} id={}", action, tenantId.getValue(), actor, itemId);
    }

    private static String q(UUID id) { return id == null ? "null" : "\"" + id + "\""; }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
