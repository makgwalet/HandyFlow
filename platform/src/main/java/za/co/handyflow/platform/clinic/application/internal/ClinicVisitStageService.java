package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stage;
import za.co.handyflow.platform.clinic.dto.VisitStageDtos.Stages;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;

/**
 * Which consultation stages a visit type requires before signing (CLINIC-DEC-012). A practice's own stages replace the
 * platform default for that visit type; a visit type with neither requires Symptoms and Diagnosis.
 */
@Service
@RequiredArgsConstructor
public class ClinicVisitStageService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Stages get(TenantId t, String rawVisitType) {
        String visitType = VisitStageRules.visitType(rawVisitType);
        List<Stage> own = rows("tenant_id = ?", visitType, t.getValue());
        if (!own.isEmpty()) return new Stages(visitType, "TENANT", VisitStageRules.check(own));
        List<Stage> platform = rows("tenant_id IS NULL", visitType);
        if (!platform.isEmpty()) return new Stages(visitType, "PLATFORM", VisitStageRules.check(platform));
        return new Stages(visitType, "DEFAULT", VisitStageRules.ORDER.stream()
                .map(n -> new Stage(n, VisitStageRules.DEFAULT_REQUIRED.contains(n))).toList());
    }

    /** The stage names that must be filled in (or overridden with a reason) to sign a visit of this type. */
    @Transactional(readOnly = true)
    public Set<String> requiredStages(TenantId t, String visitType) {
        return VisitStageRules.required(get(t, visitType).stages());
    }

    @Transactional
    public Stages set(TenantId t, String rawVisitType, List<Stage> requested) {
        String visitType = VisitStageRules.visitType(rawVisitType);
        List<Stage> stages = VisitStageRules.check(requested);
        jdbc.update("DELETE FROM clinic_visit_type_stage WHERE tenant_id = ? AND visit_type = ?", t.getValue(), visitType);
        for (Stage s : stages) {
            jdbc.update("INSERT INTO clinic_visit_type_stage (tenant_id, visit_type, stage, required) VALUES (?, ?, ?, ?)",
                    t.getValue(), visitType, s.stage(), s.required());
        }
        return get(t, visitType);
    }

    /** Goes back to the platform default for this visit type. */
    @Transactional
    public Stages clear(TenantId t, String rawVisitType) {
        String visitType = VisitStageRules.visitType(rawVisitType);
        jdbc.update("DELETE FROM clinic_visit_type_stage WHERE tenant_id = ? AND visit_type = ?", t.getValue(), visitType);
        return get(t, visitType);
    }

    private List<Stage> rows(String where, String visitType, Object... args) {
        Object[] params = new Object[args.length + 1];
        params[0] = visitType;
        System.arraycopy(args, 0, params, 1, args.length);
        return jdbc.query("SELECT stage, required FROM clinic_visit_type_stage WHERE visit_type = ? AND " + where,
                (rs, i) -> new Stage(rs.getString("stage"), rs.getBoolean("required")), params);
    }
}
