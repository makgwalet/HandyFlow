package za.co.handyflow.platform.clinic.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Audit trail of required steps a clinician overrode when signing (REQUIRED_STEP_OVERRIDDEN). Written inside the signing transaction. */
@Service
public class ClinicSignOverrideService {

    private final JdbcTemplate jdbc;

    public ClinicSignOverrideService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(TenantId tenantId, UUID consultationId, UUID userId, List<String> steps, String reason) {
        for (String step : steps) {
            jdbc.update("""
                INSERT INTO clinic_consultation_overrides (id, tenant_id, consultation_id, step, reason, overridden_by)
                VALUES (?,?,?,?,?,?)""", UUID.randomUUID(), tenantId.getValue(), consultationId, step, reason, userId);
        }
    }

    public List<Map<String, Object>> forConsultation(TenantId tenantId, UUID consultationId) {
        return jdbc.queryForList("""
            SELECT step, reason, overridden_by, created_at FROM clinic_consultation_overrides
            WHERE tenant_id = ? AND consultation_id = ? ORDER BY created_at""", tenantId.getValue(), consultationId);
    }
}
