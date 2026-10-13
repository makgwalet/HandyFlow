package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.PatientMergeRules.Conflict;
import za.co.handyflow.platform.clinic.application.internal.PatientMergeRules.Handling;
import za.co.handyflow.platform.clinic.application.internal.PatientMergeRules.Side;
import za.co.handyflow.platform.clinic.dto.PatientMergeDtos.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Merges two patient records that are the same person (patch 0188). The record that stays keeps its identity (name, ID number,
 * date of birth); the other is archived with a pointer to it, everything attached to it moves across in one transaction, and a
 * row in clinic_patient_merges lists exactly what moved. Tables are found from the database, so a table added later that holds
 * a patient_id moves too; a table that cannot move (a trigger or a unique rule says no) stops the whole merge.
 */
@Service
public class ClinicPatientMergeService {

    private record Table(String name, boolean hasId, boolean hasTenant) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ClinicPatientMergeService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    // ── Preview ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Preview preview(UUID tenantId, UUID survivorId, UUID mergedId) {
        Side keep = side(tenantId, survivorId);
        Side away = side(tenantId, mergedId);
        List<String> blockers = PatientMergeRules.blockers(keep, away);
        if (!keep.found() || !away.found()) return new Preview(card(keep), card(away), List.of(), List.of(), List.of(), blockers, List.of(), false);
        List<Conflict> conflicts = PatientMergeRules.conflicts(keep, away);
        List<Count> moves = new ArrayList<>();
        List<String> stays = new ArrayList<>();
        for (Table t : tables()) {
            if (!t.hasTenant()) { stays.add(PatientMergeRules.label(t.name()) + " (cannot be checked for this practice)"); continue; }
            int n = count(t.name(), tenantId, mergedId);
            if (n == 0) continue;
            switch (PatientMergeRules.handling(t.name())) {
                case MOVE -> moves.add(new Count(t.name(), PatientMergeRules.label(t.name()), n));
                case SINGLE -> {
                    if (count(t.name(), tenantId, survivorId) == 0) moves.add(new Count(t.name(), PatientMergeRules.label(t.name()), n));
                    else stays.add(PatientMergeRules.label(t.name()) + " (the kept record already has one)");
                }
                case SKIP -> stays.add(PatientMergeRules.label(t.name()) + " (history stays with the record it was written for)");
            }
        }
        return new Preview(card(keep), card(away), moves, fillColumns(tenantId, survivorId, mergedId),
                conflicts.stream().map(c -> new ConflictView(c.field(), c.text(), c.blocking())).toList(),
                blockers, stays, PatientMergeRules.needsAcknowledgement(conflicts));
    }

    // ── Merge ────────────────────────────────────────────────────────────────

    @Transactional
    public MergeResult merge(UUID tenantId, UUID by, MergeRequest req) {
        if (req == null || req.survivorId() == null || req.mergedId() == null) throw new IllegalArgumentException("Choose the two records to merge.");
        String reason = PatientMergeRules.reason(req.reason());
        UUID survivorId = req.survivorId(), mergedId = req.mergedId();
        if (survivorId.equals(mergedId)) throw new IllegalArgumentException("Choose two different patients.");

        // Both rows locked, always in the same order, so two people merging the same pair cannot deadlock or both succeed.
        UUID first = survivorId.compareTo(mergedId) < 0 ? survivorId : mergedId, second = first.equals(survivorId) ? mergedId : survivorId;
        jdbc.query("SELECT id FROM clinic_patients WHERE tenant_id = ? AND id IN (?, ?) ORDER BY id FOR UPDATE", rs -> {}, tenantId, first, second);

        Side keep = side(tenantId, survivorId);
        Side away = side(tenantId, mergedId);
        List<String> blockers = PatientMergeRules.blockers(keep, away);
        if (!blockers.isEmpty()) throw new IllegalStateException(String.join(" ", blockers));
        List<Conflict> conflicts = PatientMergeRules.conflicts(keep, away);
        if (PatientMergeRules.needsAcknowledgement(conflicts) && !req.acknowledgeConflicts()) {
            throw new IllegalStateException("These records disagree on " + String.join(" and ", conflicts.stream().filter(Conflict::blocking).map(Conflict::field).toList())
                    + ". Check they are the same person and confirm to go on.");
        }

        String keepBefore = snapshot(tenantId, survivorId), awayBefore = snapshot(tenantId, mergedId);
        List<String> filled = fillColumns(tenantId, survivorId, mergedId);
        if (!filled.isEmpty()) {
            String set = String.join(", ", filled.stream().map(c -> c + " = m." + c).toList());
            jdbc.update("UPDATE clinic_patients s SET " + set + ", updated_at = now() FROM clinic_patients m WHERE s.tenant_id = ? AND s.id = ? AND m.tenant_id = s.tenant_id AND m.id = ?",
                    tenantId, survivorId, mergedId);
        }

        Map<String, List<UUID>> moved = new LinkedHashMap<>();
        Map<String, String> notMoved = new LinkedHashMap<>();
        List<Count> counts = new ArrayList<>();
        int total = 0;
        for (Table t : tables()) {
            Handling h = PatientMergeRules.handling(t.name());
            if (!t.hasTenant()) { notMoved.put(t.name(), "no tenant column, cannot be checked"); continue; }
            if (h == Handling.SKIP) { if (count(t.name(), tenantId, mergedId) > 0) notMoved.put(t.name(), "history stays with the record it was written for"); continue; }
            int n = count(t.name(), tenantId, mergedId);
            if (n == 0) continue;
            if (h == Handling.SINGLE && count(t.name(), tenantId, survivorId) > 0) { notMoved.put(t.name(), "the kept record already has one"); continue; }
            List<UUID> ids = t.hasId() ? jdbc.queryForList("SELECT id FROM " + t.name() + " WHERE tenant_id = ? AND patient_id = ?", UUID.class, tenantId, mergedId) : List.of();
            int updated;
            try {
                updated = jdbc.update("UPDATE " + t.name() + " SET patient_id = ? WHERE tenant_id = ? AND patient_id = ?", survivorId, tenantId, mergedId);
            } catch (DataAccessException e) {
                throw new IllegalStateException("Nothing was merged: " + PatientMergeRules.label(t.name()) + " could not be moved (" + rootMessage(e) + ").");
            }
            moved.put(t.name(), ids);
            counts.add(new Count(t.name(), PatientMergeRules.label(t.name()), updated));
            total += updated;
        }

        // Family accounts: people who were dependants of the merged-away record now belong to the one that stays.
        jdbc.update("UPDATE clinic_patients SET principal_id = NULL, updated_at = now() WHERE tenant_id = ? AND id = ? AND principal_id = ?", tenantId, survivorId, mergedId);
        jdbc.update("UPDATE clinic_patients SET principal_id = ?, updated_at = now() WHERE tenant_id = ? AND principal_id = ? AND id <> ?", survivorId, tenantId, mergedId, survivorId);
        jdbc.update("UPDATE clinic_patients s SET last_visit_at = GREATEST(s.last_visit_at, m.last_visit_at) FROM clinic_patients m WHERE s.tenant_id = ? AND s.id = ? AND m.tenant_id = s.tenant_id AND m.id = ? AND m.last_visit_at IS NOT NULL",
                tenantId, survivorId, mergedId);

        String keepName = (keep.firstName() + " " + keep.lastName()).trim();
        jdbc.update("UPDATE clinic_patients SET archived_at = ?, archive_reason = ?, merged_into = ?, updated_at = now() WHERE tenant_id = ? AND id = ?",
                Timestamp.from(Instant.now()), PatientMergeRules.archiveReason(keep.patientNumber(), keepName), survivorId, tenantId, mergedId);

        UUID mergeId = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_patient_merges (id, tenant_id, survivor_id, merged_id, merged_by, merged_at, reason, acknowledged_conflicts, conflicts, filled_columns, moved, not_moved, survivor_before, merged_before) "
                        + "VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb)",
                mergeId, tenantId, survivorId, mergedId, by, Timestamp.from(Instant.now()), reason, req.acknowledgeConflicts(),
                toJson(conflicts.stream().map(Conflict::text).toList()), toJson(filled), toJson(moved), toJson(notMoved), keepBefore, awayBefore);
        return new MergeResult(mergeId, survivorId, mergedId, total, counts, filled);
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    private Side side(UUID tenantId, UUID id) {
        List<Side> rows = jdbc.query("SELECT id, archived_at, deleted_at, merged_into, patient_number, first_name, last_name, id_number, date_of_birth, sex_at_birth FROM clinic_patients WHERE tenant_id = ? AND id = ?",
                (rs, i) -> {
                    UUID pid = (UUID) rs.getObject(1);
                    java.sql.Date dob = rs.getDate(9);
                    return new Side(pid, rs.getObject(3) == null, rs.getObject(2) != null || rs.getObject(4) != null, (UUID) rs.getObject(4), rs.getString(5),
                            rs.getString(6), rs.getString(7), rs.getString(8), dob == null ? null : dob.toLocalDate(), rs.getString(10), false, 0);
                }, tenantId, id);
        if (rows.isEmpty()) return new Side(id, false, false, null, null, null, null, null, null, null, false, 0);
        Side s = rows.get(0);
        Integer restricted = jdbc.queryForObject("SELECT count(*) FROM clinic_restricted_records WHERE tenant_id = ? AND patient_id = ? AND released_at IS NULL", Integer.class, tenantId, id);
        Integer open = jdbc.queryForObject("SELECT count(*) FROM clinic_consultations WHERE tenant_id = ? AND patient_id = ? AND status NOT IN ('SIGNED','LOCKED','ABANDONED')", Integer.class, tenantId, id);
        return new Side(s.id(), s.found(), s.archived(), s.mergedInto(), s.patientNumber(), s.firstName(), s.lastName(), s.idNumber(), s.dateOfBirth(),
                s.sexAtBirth(), restricted != null && restricted > 0, open == null ? 0 : open);
    }

    private static Card card(Side s) {
        String name = s.found() ? ((s.firstName() == null ? "" : s.firstName()) + " " + (s.lastName() == null ? "" : s.lastName())).trim() : null;
        return new Card(s.id(), s.patientNumber(), name, s.dateOfBirth(), s.idNumber());
    }

    private List<String> fillColumns(UUID tenantId, UUID survivorId, UUID mergedId) {
        Map<String, String> a = textColumns(tenantId, survivorId), b = textColumns(tenantId, mergedId);
        return PatientMergeRules.fill(a, b);
    }

    private Map<String, String> textColumns(UUID tenantId, UUID id) {
        String cols = String.join(", ", PatientMergeRules.FILLABLE.stream().map(c -> c + "::text").toList());
        return jdbc.query("SELECT " + cols + " FROM clinic_patients WHERE tenant_id = ? AND id = ?", rs -> {
            Map<String, String> m = new HashMap<>();
            if (rs.next()) for (int i = 0; i < PatientMergeRules.FILLABLE.size(); i++) m.put(PatientMergeRules.FILLABLE.get(i), rs.getString(i + 1));
            return m;
        }, tenantId, id);
    }

    /** Every clinic table with a patient_id column, found from the database (the patient table's own columns excluded). */
    private List<Table> tables() {
        List<Table> out = new ArrayList<>();
        jdbc.query("SELECT c.table_name, "
                + "EXISTS (SELECT 1 FROM information_schema.columns x WHERE x.table_schema = c.table_schema AND x.table_name = c.table_name AND x.column_name = 'id') AS has_id, "
                + "EXISTS (SELECT 1 FROM information_schema.columns x WHERE x.table_schema = c.table_schema AND x.table_name = c.table_name AND x.column_name = 'tenant_id') AS has_tenant "
                + "FROM information_schema.columns c JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE' "
                + "WHERE c.table_schema = current_schema() AND c.column_name = 'patient_id' AND c.table_name LIKE 'clinic\\_%' ORDER BY c.table_name",
                rs -> {
                    String name = rs.getString(1);
                    if (PatientMergeRules.safeName(name) && !"clinic_patients".equals(name)) out.add(new Table(name, rs.getBoolean(2), rs.getBoolean(3)));
                });
        return out;
    }

    private int count(String table, UUID tenantId, UUID patientId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE tenant_id = ? AND patient_id = ?", Integer.class, tenantId, patientId);
        return n == null ? 0 : n;
    }

    private String snapshot(UUID tenantId, UUID id) {
        return jdbc.queryForObject("SELECT to_jsonb(p)::text FROM clinic_patients p WHERE tenant_id = ? AND id = ?", String.class, tenantId, id);
    }

    private String toJson(Object o) {
        try { return json.writeValueAsString(o); } catch (JsonProcessingException e) { throw new IllegalStateException("Could not record the merge.", e); }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e; while (t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m.lines().findFirst().orElse(m);
    }
}
