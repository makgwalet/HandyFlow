package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The contract rate a client pays for a site. Rates are never deleted; ending one and adding the next keeps the history. */
@Service
@RequiredArgsConstructor
public class SiteRateService {

    public record Row(UUID id, UUID siteId, String type, long amountCents, String currency, boolean vatInclusive,
                      LocalDate effectiveFrom, LocalDate effectiveTo, String note, OffsetDateTime createdAt) {}

    private final JdbcTemplate jdbc;

    private static final String COLS = "id, site_id, rate_type, amount_cents, currency, vat_inclusive, effective_from, effective_to, note, created_at";

    private static Row row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Date to = rs.getDate("effective_to");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class), rs.getString("rate_type"), rs.getLong("amount_cents"),
                rs.getString("currency").trim(), rs.getBoolean("vat_inclusive"), rs.getDate("effective_from").toLocalDate(),
                to == null ? null : to.toLocalDate(), rs.getString("note"), rs.getObject("created_at", OffsetDateTime.class));
    }

    @Transactional(readOnly = true)
    public List<Row> list(TenantId tenantId, UUID siteId) {
        requireSite(tenantId, siteId, false);
        return jdbc.query("SELECT " + COLS + " FROM security_site_rates WHERE tenant_id = ? AND site_id = ? ORDER BY effective_from DESC, created_at DESC",
                SiteRateService::row, tenantId.getValue(), siteId);
    }

    /** The rate in force for a site on a day (used by billing). */
    @Transactional(readOnly = true)
    public Optional<Row> rateOn(TenantId tenantId, UUID siteId, LocalDate day) {
        List<Row> all = jdbc.query("SELECT " + COLS + " FROM security_site_rates WHERE tenant_id = ? AND site_id = ?", SiteRateService::row, tenantId.getValue(), siteId);
        List<SiteRateRules.Rate> rates = all.stream().map(r -> new SiteRateRules.Rate(r.id(), r.type(), r.amountCents(), r.effectiveFrom(), r.effectiveTo())).toList();
        return SiteRateRules.rateOn(rates, day).flatMap(hit -> all.stream().filter(r -> r.id().equals(hit.id())).findFirst());
    }

    @Transactional
    public Row add(TenantId tenantId, UUID actor, UUID siteId, String type, Long cents, boolean vatInclusive, LocalDate from, LocalDate to, String note) {
        SiteRateRules.validate(type, cents, from, to);
        UUID t = tenantId.getValue();
        requireSite(tenantId, siteId, true); // locks the site, so two people adding rates at once cannot both pass the overlap check
        SiteRateRules.Plan plan = SiteRateRules.plan(rules(tenantId, siteId), from, to);
        if (plan.closeId() != null) {
            jdbc.update("UPDATE security_site_rates SET effective_to = ?, ended_by = ?, ended_at = now() WHERE id = ? AND tenant_id = ?",
                    Date.valueOf(plan.closeTo()), actor, plan.closeId(), t);
            audit(t, actor, plan.closeId(), "RATE_ENDED", "{\"siteId\":\"" + siteId + "\",\"effectiveTo\":\"" + plan.closeTo() + "\",\"replacedByNewRate\":true}");
        }
        UUID id = UUID.randomUUID();
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        jdbc.update("INSERT INTO security_site_rates (id, tenant_id, site_id, rate_type, amount_cents, vat_inclusive, effective_from, effective_to, note, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, t, siteId, type, cents, vatInclusive, Date.valueOf(from), to == null ? null : Date.valueOf(to), cleanNote, actor);
        audit(t, actor, id, "RATE_ADDED", "{\"siteId\":\"" + siteId + "\",\"type\":\"" + type + "\",\"amountCents\":" + cents + ",\"vatInclusive\":" + vatInclusive
                + ",\"effectiveFrom\":\"" + from + "\",\"effectiveTo\":" + (to == null ? "null" : "\"" + to + "\"") + "}");
        return jdbc.queryForObject("SELECT " + COLS + " FROM security_site_rates WHERE id = ? AND tenant_id = ?", SiteRateService::row, id, t);
    }

    @Transactional
    public Row end(TenantId tenantId, UUID actor, UUID siteId, UUID rateId, LocalDate lastDay) {
        UUID t = tenantId.getValue();
        requireSite(tenantId, siteId, true);
        List<SiteRateRules.Rate> all = rules(tenantId, siteId);
        SiteRateRules.Rate rate = all.stream().filter(r -> r.id().equals(rateId)).findFirst().orElse(null);
        LocalDate end = SiteRateRules.requireEndable(rate, lastDay);
        jdbc.update("UPDATE security_site_rates SET effective_to = ?, ended_by = ?, ended_at = now() WHERE id = ? AND tenant_id = ? AND site_id = ?",
                Date.valueOf(end), actor, rateId, t, siteId);
        audit(t, actor, rateId, "RATE_ENDED", "{\"siteId\":\"" + siteId + "\",\"effectiveTo\":\"" + end + "\"}");
        return jdbc.queryForObject("SELECT " + COLS + " FROM security_site_rates WHERE id = ? AND tenant_id = ?", SiteRateService::row, rateId, t);
    }

    private List<SiteRateRules.Rate> rules(TenantId tenantId, UUID siteId) {
        return jdbc.query("SELECT id, rate_type, amount_cents, effective_from, effective_to FROM security_site_rates WHERE tenant_id = ? AND site_id = ?",
                (rs, i) -> new SiteRateRules.Rate(rs.getObject("id", UUID.class), rs.getString("rate_type"), rs.getLong("amount_cents"),
                        rs.getDate("effective_from").toLocalDate(), rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate()),
                tenantId.getValue(), siteId);
    }

    private void requireSite(TenantId tenantId, UUID siteId, boolean lock) {
        List<Integer> found = jdbc.queryForList("SELECT 1 FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""),
                Integer.class, siteId, tenantId.getValue());
        if (found.isEmpty()) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
    }

    private void audit(UUID tenant, UUID actor, UUID entity, String action, String json) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'SITE_RATE', ?, ?, ?::jsonb, now())""", tenant, actor, entity, action, json);
    }
}
