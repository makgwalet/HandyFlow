package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A second person for storage-tariff and minimum-charge changes. Off by default: a change then applies at once, as before.
 * When a warehouse administrator switches the rule on, a change is only asked for, and another holder of
 * WAREHOUSING_TARIFF_APPROVE applies it. Where nobody else holds that permission the change is applied and recorded as an
 * audited sole-approver exception. A request approved after its start date has passed is refused, so no past day is re-priced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseTariffApprovalService {

    public static final String PERMISSION = "WAREHOUSING_TARIFF_APPROVE";
    static final int MIN_REASON = 5;
    static final String TARIFF = "TARIFF";
    static final String TERMS = "TERMS";

    private final JdbcTemplate jdbc;
    private final WhseTariffService tariffs;
    private final SeparationOfDutiesChecker duties;

    public record Item(UUID id, String kind, UUID clientId, String clientName, UUID itemId, String sku, BigDecimal monthlyRate,
                       BigDecimal minimumStorageCharge, Integer freeDays, BigDecimal locationFeePerMonth, LocalDate effectiveFrom, String note, UUID requestedBy,
                       String requestedByName, Instant requestedAt) {}

    @Transactional(readOnly = true)
    public boolean required(TenantId tenantId) {
        List<Boolean> rows = jdbc.query("SELECT tariff_approval_required FROM whse_profile WHERE tenant_id = ?",
                (rs, i) -> rs.getBoolean(1), tenantId.getValue());
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    /** Switches the rule on or off. Switching it off weakens a control, so a reason is required and recorded either way. */
    @Transactional
    public void setRequired(TenantId tenantId, boolean required, String reason, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason for the change (at least " + MIN_REASON + " characters).");
        boolean before = required(tenantId);
        List<UUID> profile = jdbc.query("SELECT id FROM whse_profile WHERE tenant_id = ?", (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue());
        if (profile.isEmpty()) throw new IllegalStateException("Set up the warehouse profile first.");
        jdbc.update("UPDATE whse_profile SET tariff_approval_required = ?, updated_at = now() WHERE tenant_id = ?", required, tenantId.getValue());
        audit(tenantId, actor, "WHSE_TARIFF_APPROVAL_RULE_CHANGED", "WHSE_PROFILE", profile.get(0),
                "{\"before\":" + before + ",\"after\":" + required + ",\"reason\":" + quote(reason.trim()) + "}");
        log.warn("[Warehousing] Tariff approval rule changed tenant={} by={} {} -> {}", tenantId.getValue(), actor, before, required);
    }

    /** Throws 409 APPROVAL_REQUIRED when changes must go through a second person. */
    @Transactional(readOnly = true)
    public void requireDirectAllowed(TenantId tenantId) {
        if (required(tenantId)) {
            throw new HandyFlowException("Tariff and minimum-charge changes need a second person. Submit it as a request.", HttpStatus.CONFLICT, "APPROVAL_REQUIRED");
        }
    }

    @Transactional
    public UUID requestTariff(TenantId tenantId, UUID clientId, UUID itemId, BigDecimal monthlyRate, LocalDate effectiveFrom, String note, UUID actor) {
        requireActor(actor);
        tariffs.precheckTariff(tenantId, clientId, itemId, monthlyRate, effectiveFrom);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_tariff_requests (id, tenant_id, kind, client_id, item_id, monthly_rate, effective_from, note, requested_by) VALUES (?,?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), TARIFF, clientId, itemId, monthlyRate, effectiveFrom, blank(note), actor);
        return id;
    }

    @Transactional
    public UUID requestTerms(TenantId tenantId, UUID clientId, BigDecimal minimumStorageCharge, Integer freeDays, BigDecimal locationFeePerMonth,
                             LocalDate effectiveFrom, String note, UUID actor) {
        requireActor(actor);
        tariffs.precheckTerms(tenantId, clientId, minimumStorageCharge, freeDays, locationFeePerMonth, effectiveFrom);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_tariff_requests (id, tenant_id, kind, client_id, minimum_storage_charge, free_days, location_fee_per_month, effective_from, note, requested_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), TERMS, clientId, minimumStorageCharge, freeDays, locationFeePerMonth, effectiveFrom, blank(note), actor);
        return id;
    }

    @Transactional
    public void approve(TenantId tenantId, UUID requestId, String note, UUID actor) {
        requireActor(actor);
        Row r = lock(tenantId, requestId);
        requirePending(r);
        boolean sole = actor.equals(r.requestedBy) && duties.otherHolders(tenantId, PERMISSION, actor) == 0;
        duties.require(tenantId, PERMISSION, r.requestedBy, actor,
                "The person who asked for this change cannot approve it. Ask another warehouse administrator.",
                "WHSE_TARIFF_REQUEST", requestId, "approved their own tariff change");
        // A past start date is refused by the tariff service, so a late approval can never re-price a day already billed.
        UUID created = TARIFF.equals(r.kind)
                ? tariffs.addTariff(tenantId, r.clientId, r.itemId, r.monthlyRate, r.effectiveFrom, r.note, r.requestedBy, actor)
                : tariffs.addTerms(tenantId, r.clientId, r.minimumStorageCharge, r.freeDays, r.locationFeePerMonth, r.effectiveFrom, r.note, r.requestedBy, actor);
        jdbc.update("UPDATE whse_tariff_requests SET status = 'APPLIED', decided_by = ?, decided_at = now(), decision_note = ?, sole_exception = ?, resulting_id = ?, version = version + 1 WHERE id = ?",
                actor, blank(note), sole, created, requestId);
    }

    @Transactional
    public void reject(TenantId tenantId, UUID requestId, String note, UUID actor) {
        requireActor(actor);
        if (note == null || note.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason for declining (at least " + MIN_REASON + " characters).");
        Row r = lock(tenantId, requestId);
        requirePending(r);
        jdbc.update("UPDATE whse_tariff_requests SET status = 'REJECTED', decided_by = ?, decided_at = now(), decision_note = ?, version = version + 1 WHERE id = ?",
                actor, note.trim(), requestId);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID requestId, UUID actor) {
        Row r = lock(tenantId, requestId);
        requirePending(r);
        if (actor == null || !actor.equals(r.requestedBy)) throw new AccessDeniedException("Only the person who asked for the change can cancel it.");
        jdbc.update("UPDATE whse_tariff_requests SET status = 'CANCELLED', decided_by = ?, decided_at = now(), version = version + 1 WHERE id = ?", actor, requestId);
    }

    @Transactional(readOnly = true)
    public List<Item> pending(TenantId tenantId) {
        return jdbc.query("""
                SELECT r.id, r.kind, r.client_id, c.trading_name AS client_name, r.item_id, it.sku AS sku, r.monthly_rate,
                       r.minimum_storage_charge, r.free_days, r.location_fee_per_month, r.effective_from, r.note, r.requested_by, r.requested_at,
                       trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS requested_by_name
                  FROM whse_tariff_requests r
                  LEFT JOIN whse_clients c ON c.id = r.client_id
                  LEFT JOIN whse_items it ON it.id = r.item_id
                  LEFT JOIN users u ON u.id = r.requested_by
                 WHERE r.tenant_id = ? AND r.status = 'PENDING' ORDER BY r.requested_at""",
                (rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getObject("client_id", UUID.class),
                        rs.getString("client_name"), rs.getObject("item_id", UUID.class), rs.getString("sku"), rs.getBigDecimal("monthly_rate"),
                        rs.getBigDecimal("minimum_storage_charge"), (Integer) rs.getObject("free_days"), rs.getBigDecimal("location_fee_per_month"),
                        rs.getDate("effective_from").toLocalDate(), rs.getString("note"),
                        rs.getObject("requested_by", UUID.class), rs.getString("requested_by_name"), rs.getTimestamp("requested_at").toInstant()),
                tenantId.getValue());
    }

    // ── internals ───────────────────────────────────────────────────────────

    record Row(String kind, UUID clientId, UUID itemId, BigDecimal monthlyRate, BigDecimal minimumStorageCharge,
               Integer freeDays, BigDecimal locationFeePerMonth, LocalDate effectiveFrom, String note, String status, UUID requestedBy) {}

    private Row lock(TenantId tenantId, UUID id) {
        List<Row> rows = jdbc.query("""
                SELECT kind, client_id, item_id, monthly_rate, minimum_storage_charge, free_days, location_fee_per_month, effective_from, note, status, requested_by
                  FROM whse_tariff_requests WHERE tenant_id = ? AND id = ? FOR UPDATE""",
                (rs, i) -> new Row(rs.getString("kind"), rs.getObject("client_id", UUID.class), rs.getObject("item_id", UUID.class),
                        rs.getBigDecimal("monthly_rate"), rs.getBigDecimal("minimum_storage_charge"),
                        (Integer) rs.getObject("free_days"), rs.getBigDecimal("location_fee_per_month"), rs.getDate("effective_from").toLocalDate(),
                        rs.getString("note"), rs.getString("status"), rs.getObject("requested_by", UUID.class)),
                tenantId.getValue(), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Tariff request", id.toString());
        return rows.get(0);
    }

    private static void requirePending(Row r) {
        if (!"PENDING".equals(r.status)) throw new HandyFlowException("This request has already been decided.", HttpStatus.CONFLICT, "TARIFF_REQUEST_DECIDED");
    }

    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }

    private void audit(TenantId tenantId, UUID actor, String action, String itemType, UUID itemId, String afterJson) {
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json) VALUES (?, 'STAFF', ?, ?, ?, ?, ?, ?::jsonb)",
                tenantId.getValue(), actor, name, action, itemType, itemId, afterJson);
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }
}
