package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.BusinessCalendar;
import za.co.handyflow.platform.shared.BusinessTime;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Reads and changes the time zone the company's business days are decided in (tenants.time_zone). */
@Service
@RequiredArgsConstructor
public class CompanyZoneService {

    public record View(String timeZone, String abbreviation, String now, LocalDate today) {}
    public record Changed(View view, CompanyZoneRules.Effect effect) {}

    private final JdbcTemplate jdbc;
    private final BusinessCalendar calendar;

    @Transactional(readOnly = true)
    public View get(TenantId tenantId) {
        return view(zoneOf(tenantId.getValue()), Instant.now());
    }

    /** What changing to this zone would affect, without changing anything. */
    @Transactional(readOnly = true)
    public CompanyZoneRules.Effect preview(TenantId tenantId, String wanted) {
        String id = CompanyZoneRules.requireValid(wanted);
        return CompanyZoneRules.effect(zoneOf(tenantId.getValue()), ZoneId.of(id), Instant.now());
    }

    @Transactional
    public Changed change(TenantId tenantId, UUID actor, String wanted, boolean confirm) {
        UUID tenant = tenantId.getValue();
        String id = CompanyZoneRules.requireValid(wanted);
        List<String> current = jdbc.queryForList("SELECT time_zone FROM tenants WHERE id = ? FOR UPDATE", String.class, tenant);
        if (current.isEmpty()) throw new HandyFlowException("Company not found", HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND");
        ZoneId from = BusinessTime.zoneOrDefault(current.get(0));
        CompanyZoneRules.requireDifferent(current.get(0), id);
        CompanyZoneRules.requireConfirmed(confirm);
        Instant now = Instant.now();
        CompanyZoneRules.Effect effect = CompanyZoneRules.effect(from, ZoneId.of(id), now);
        jdbc.update("UPDATE tenants SET time_zone = ? WHERE id = ?", id, tenant);
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'COMPANY', ?, 'TIME_ZONE_CHANGED', ?::jsonb, now())""",
                tenant, actor, tenant, "{\"from\":\"" + from.getId() + "\",\"to\":\"" + id + "\"}");
        calendar.evict(tenant);
        return new Changed(view(ZoneId.of(id), now), effect);
    }

    private ZoneId zoneOf(UUID tenant) {
        List<String> ids = jdbc.queryForList("SELECT time_zone FROM tenants WHERE id = ?", String.class, tenant);
        return BusinessTime.zoneOrDefault(ids.isEmpty() ? null : ids.get(0));
    }

    private static View view(ZoneId zone, Instant now) {
        return new View(zone.getId(), BusinessTime.abbreviation(zone, now), BusinessTime.format(zone, now), BusinessTime.today(zone, now));
    }
}
