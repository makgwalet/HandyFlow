package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.PatientDirectoryPage;
import za.co.handyflow.platform.clinic.dto.PatientResponse;
import za.co.handyflow.platform.clinic.dto.RecallResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** The Patients screen's list: saved views (recent, today, mine, follow-up, never seen, duplicates) with visit facts per row. */
@Service
@RequiredArgsConstructor
public class ClinicPatientDirectoryService {

    private static final String SQL_HEAD =
            "WITH v AS (SELECT patient_id, count(*) AS cnt, max(consulted_at) AS last_at FROM clinic_consultations"
                    + "   WHERE tenant_id = ? AND deleted_at IS NULL AND status IN ('SIGNED','LOCKED','DOCTOR_COMPLETED') GROUP BY patient_id),"
                    + " n AS (SELECT patient_id, min(scheduled_at) AS next_at FROM clinic_appointments"
                    + "   WHERE tenant_id = ? AND deleted_at IS NULL AND status IN ('SCHEDULED','CONFIRMED') AND scheduled_at >= ? GROUP BY patient_id),"
                    + " d AS (SELECT lower(btrim(first_name)) AS f, lower(btrim(last_name)) AS l, count(*) AS cnt FROM clinic_patients"
                    + "   WHERE tenant_id = ? AND deleted_at IS NULL AND archived_at IS NULL GROUP BY 1, 2)"
                    + " SELECT p.id, count(*) OVER () AS total, coalesce(v.cnt, 0) AS visits, v.last_at, n.next_at, coalesce(d.cnt, 1) AS same_name"
                    + " FROM clinic_patients p"
                    + " LEFT JOIN v ON v.patient_id = p.id LEFT JOIN n ON n.patient_id = p.id"
                    + " LEFT JOIN d ON d.f = lower(btrim(p.first_name)) AND d.l = lower(btrim(p.last_name)) AND p.archived_at IS NULL"
                    + " WHERE ";

    private record Row(UUID id, long total, int visits, Instant lastAt, Instant nextAt, int sameName) {}

    private final JdbcTemplate jdbc;
    private final ClinicService clinicService;
    private final ClinicRecallService recallService;

    @Transactional(readOnly = true)
    public PatientDirectoryPage directory(TenantId tenantId, String view, String search, boolean includeArchived,
                                          UUID practitionerId, int page, int size) {
        return directory(tenantId, view, search, includeArchived, practitionerId, page, size, Instant.now());
    }

    @Transactional(readOnly = true)
    public PatientDirectoryPage directory(TenantId tenantId, String view, String search, boolean includeArchived,
                                          UUID practitionerId, int page, int size, Instant now) {
        int s = PatientDirectoryQuery.clampSize(size);
        int p = Math.max(0, page);
        LocalDate today = now.atZone(RecallRules.CLINIC_ZONE).toLocalDate();
        Instant dayStart = today.atStartOfDay(RecallRules.CLINIC_ZONE).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(RecallRules.CLINIC_ZONE).toInstant();

        Set<UUID> followUp = recallService.getDueRecalls(tenantId, now).stream()
                .filter(r -> "OPEN".equals(r.status())).map(RecallResponse::patientId).collect(Collectors.toSet());

        var built = PatientDirectoryQuery.build(view, search, includeArchived, practitionerId, followUp, dayStart, dayEnd);
        List<Object> args = new ArrayList<>();
        UUID t = tenantId.getValue();
        args.add(t); args.add(t); args.add(Timestamp.from(now)); args.add(t);   // CTEs
        args.add(t);                                                              // WHERE p.tenant_id
        args.addAll(built.params());
        args.add(s); args.add((long) p * s);

        List<Row> rows = jdbc.query(SQL_HEAD + built.where() + " ORDER BY " + built.orderBy() + " LIMIT ? OFFSET ?",
                (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getLong("total"), rs.getInt("visits"),
                        rs.getTimestamp("last_at") == null ? null : rs.getTimestamp("last_at").toInstant(),
                        rs.getTimestamp("next_at") == null ? null : rs.getTimestamp("next_at").toInstant(),
                        rs.getInt("same_name")), args.toArray());

        Map<UUID, PatientResponse> patients = clinicService.getPatientsByIds(tenantId, rows.stream().map(Row::id).toList())
                .stream().collect(Collectors.toMap(PatientResponse::id, x -> x));
        List<PatientDirectoryPage.Entry> content = rows.stream().filter(r -> patients.containsKey(r.id()))
                .map(r -> new PatientDirectoryPage.Entry(patients.get(r.id()), r.visits(), r.lastAt(), r.nextAt(), r.sameName(), followUp.contains(r.id())))
                .toList();
        long total = rows.isEmpty() ? 0 : rows.get(0).total();
        return new PatientDirectoryPage(content, p, s, total);
    }
}
