package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.Settings;
import za.co.handyflow.platform.internalaudit.application.internal.AuditSettings.Values;
import za.co.handyflow.platform.internalaudit.domain.model.AuditSettingsRow;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditSettingsRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditSettingsService {
    private final AuditSettingsRepository repo;
    private final AuditTrailFacade trail;

    /** The company's rules, or the standard ones if it has not changed them. */
    @Transactional(readOnly = true)
    public Values valuesFor(UUID tenantId) {
        return repo.findById(tenantId).map(AuditSettingsService::values).orElse(AuditSettings.DEFAULTS);
    }

    @Transactional(readOnly = true)
    public Settings get(UUID tenantId) {
        return repo.findById(tenantId).map(r -> AuditSettings.of(values(r), true)).orElseGet(() -> AuditSettings.of(AuditSettings.DEFAULTS, false));
    }

    /** Saves new rules. New risk assessments use the new bands; assessments already made keep the level they were given. */
    @Transactional
    public Settings update(TenantId tenant, Values v, UUID by) {
        var problems = AuditSettings.problems(v);
        if (!problems.isEmpty()) throw new IllegalArgumentException(String.join("; ", problems));
        Values before = valuesFor(tenant.getValue());
        AuditSettingsRow row = repo.findById(tenant.getValue()).orElseGet(() -> AuditSettingsRow.forTenant(tenant.getValue()));
        row.set(v.lowMax(), v.mediumMax(), v.highMax(), v.escalateAfterDays(), v.repeatEveryDays(), v.firstReviewReminderDay(), by);
        repo.save(row);
        trail.record(tenant, "AUDIT_SETTINGS_CHANGED", "AUDIT_SETTINGS", tenant.getValue(), asMap(before), asMap(v));
        return AuditSettings.of(v, true);
    }

    /** Goes back to the standard rules. */
    @Transactional
    public Settings reset(TenantId tenant) {
        Values before = valuesFor(tenant.getValue());
        repo.deleteById(tenant.getValue());
        trail.record(tenant, "AUDIT_SETTINGS_CHANGED", "AUDIT_SETTINGS", tenant.getValue(), asMap(before), asMap(AuditSettings.DEFAULTS));
        return AuditSettings.current();
    }

    private static Map<String, Object> asMap(Values v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lowMax", v.lowMax()); m.put("mediumMax", v.mediumMax()); m.put("highMax", v.highMax());
        m.put("escalateAfterDays", v.escalateAfterDays()); m.put("repeatEveryDays", v.repeatEveryDays()); m.put("firstReviewReminderDay", v.firstReviewReminderDay());
        return m;
    }

    private static Values values(AuditSettingsRow r) {
        return new Values(r.getLowMax(), r.getMediumMax(), r.getHighMax(), r.getEscalateAfterDays(), r.getRepeatEveryDays(), r.getFirstReviewReminderDay());
    }
}
