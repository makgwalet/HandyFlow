package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.Entry;
import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.Mapping;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * The practice's own list of question groups per visit type. Setting a list replaces the platform default for that
 * visit type for this practice only; clearing it goes back to the default. Only the list is stored here: whether a
 * group is actually shown still depends on its review status and the patient (see the question library service).
 */
@Service
@RequiredArgsConstructor
public class ClinicVisitMappingService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public Mapping get(TenantId t, String rawVisitType) {
        String visitType = VisitMappingRules.visitType(rawVisitType);
        List<Entry> own = rows("tenant_id = ?", visitType, t.getValue());
        if (!own.isEmpty()) return new Mapping(visitType, "TENANT", own);
        return new Mapping(visitType, "PLATFORM", rows("tenant_id IS NULL", visitType));
    }

    /** Replaces this practice's list. An empty list means "open no groups", which is different from clearing it. */
    @Transactional
    public Mapping set(TenantId t, String rawVisitType, List<Entry> requested) {
        String visitType = VisitMappingRules.visitType(rawVisitType);
        List<Entry> entries = VisitMappingRules.check(requested);
        for (Entry e : entries) {
            Integer known = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM clinic_question_group WHERE code = ? AND (tenant_id IS NULL OR tenant_id = ?)",
                    Integer.class, e.groupCode(), t.getValue());
            if (known == null || known == 0) throw new IllegalArgumentException("There is no question group with the code " + e.groupCode() + ".");
        }
        jdbc.update("DELETE FROM clinic_visit_type_group WHERE tenant_id = ? AND visit_type = ?", t.getValue(), visitType);
        int order = 0;
        for (Entry e : entries) {
            jdbc.update("INSERT INTO clinic_visit_type_group (tenant_id, visit_type, group_code, sort_order, required) VALUES (?, ?, ?, ?, ?)",
                    t.getValue(), visitType, e.groupCode(), order++, e.required());
        }
        audit(t, visitType, "SET", entries);
        return get(t, visitType);
    }

    /** Goes back to the platform default for this visit type. */
    @Transactional
    public Mapping clear(TenantId t, String rawVisitType) {
        String visitType = VisitMappingRules.visitType(rawVisitType);
        jdbc.update("DELETE FROM clinic_visit_type_group WHERE tenant_id = ? AND visit_type = ?", t.getValue(), visitType);
        audit(t, visitType, "CLEAR", List.of());
        return get(t, visitType);
    }

    /** Written in the same transaction as the change, so a change can never be saved without its record. */
    private void audit(TenantId t, String visitType, String action, List<Entry> groups) {
        try {
            jdbc.update("INSERT INTO clinic_visit_mapping_audit (tenant_id, visit_type, action, groups, actor_id) VALUES (?, ?, ?, ?::jsonb, ?)",
                    t.getValue(), visitType, action, json.writeValueAsString(groups), currentUserIdOrNull());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not record the change.", e);
        }
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<Entry> rows(String where, String visitType, Object... args) {
        Object[] params = new Object[args.length + 1];
        System.arraycopy(args, 0, params, 0, args.length);
        params[args.length] = visitType;
        return jdbc.query("SELECT group_code, required FROM clinic_visit_type_group WHERE " + where
                + " AND visit_type = ? ORDER BY sort_order",
                (rs, i) -> new Entry(rs.getString(1), rs.getBoolean(2)), params);
    }
}
