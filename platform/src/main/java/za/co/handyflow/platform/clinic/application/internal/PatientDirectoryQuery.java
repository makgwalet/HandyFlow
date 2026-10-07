package za.co.handyflow.platform.clinic.application.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the WHERE / ORDER BY for the patient directory views. Pure, so the views can be tested without a database.
 * The query it plugs into exposes: p (clinic_patients), v (visits: cnt, last_at), n (next: next_at), d (same-name: cnt).
 */
final class PatientDirectoryQuery {

    static final Set<String> VIEWS = Set.of("ALL", "RECENT", "TODAY", "MINE", "FOLLOW_UP", "NEVER_SEEN", "DUPLICATES");
    static final int MAX_PAGE_SIZE = 100;

    private PatientDirectoryQuery() {}

    record Built(String where, String orderBy, List<Object> params) {}

    static String normaliseView(String view) {
        String v = view == null ? "ALL" : view.trim().toUpperCase(Locale.ROOT);
        return VIEWS.contains(v) ? v : "ALL";
    }

    /**
     * @param followUpPatients patients with an open recall (for FOLLOW_UP)
     * @param dayStart/dayEnd  today in the clinic's calendar, [start, end)
     */
    static Built build(String view, String search, boolean includeArchived, UUID practitionerId,
                       Collection<UUID> followUpPatients, Instant dayStart, Instant dayEnd) {
        String v = normaliseView(view);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("p.tenant_id = ? AND p.deleted_at IS NULL");
        // tenant id is bound by the caller as the first WHERE parameter
        if (!includeArchived) where.append(" AND p.archived_at IS NULL");

        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (lower(p.full_name) LIKE ? OR lower(coalesce(p.id_number,'')) LIKE ? OR lower(coalesce(p.phone,'')) LIKE ?"
                    + " OR lower(coalesce(p.patient_number,'')) LIKE ?)");
            for (int i = 0; i < 4; i++) params.add(like);
        }

        String order = "lower(p.last_name), lower(p.first_name), p.id";
        switch (v) {
            case "RECENT" -> { where.append(" AND v.last_at IS NOT NULL"); order = "v.last_at DESC, p.id"; }
            case "NEVER_SEEN" -> { where.append(" AND v.cnt IS NULL"); order = "p.created_at DESC, p.id"; }
            case "DUPLICATES" -> { where.append(" AND d.cnt > 1"); order = "lower(p.last_name), lower(p.first_name), p.date_of_birth, p.id"; }
            case "TODAY" -> {
                where.append(" AND ((v.last_at >= ? AND v.last_at < ?) OR EXISTS (SELECT 1 FROM clinic_appointments a"
                        + " WHERE a.tenant_id = p.tenant_id AND a.patient_id = p.id AND a.deleted_at IS NULL"
                        + " AND a.status NOT IN ('CANCELLED','NO_SHOW') AND a.scheduled_at >= ? AND a.scheduled_at < ?))");
                params.add(Timestamp.from(dayStart)); params.add(Timestamp.from(dayEnd));
                params.add(Timestamp.from(dayStart)); params.add(Timestamp.from(dayEnd));
                order = "lower(p.last_name), lower(p.first_name), p.id";
            }
            case "MINE" -> {
                if (practitionerId == null) where.append(" AND 1 = 0");
                else {
                    where.append(" AND EXISTS (SELECT 1 FROM clinic_consultations c WHERE c.tenant_id = p.tenant_id AND c.patient_id = p.id"
                            + " AND c.deleted_at IS NULL AND c.practitioner_id = ?)");
                    params.add(practitionerId);
                }
                order = "v.last_at DESC NULLS LAST, p.id";
            }
            case "FOLLOW_UP" -> {
                if (followUpPatients == null || followUpPatients.isEmpty()) where.append(" AND 1 = 0");
                else {
                    where.append(" AND p.id = ANY (string_to_array(?, ',')::uuid[])");
                    params.add(followUpPatients.stream().map(UUID::toString).collect(Collectors.joining(",")));
                }
                order = "v.last_at, p.id";
            }
            default -> { }
        }
        return new Built(where.toString(), order, params);
    }

    static int clampSize(int size) { return Math.max(1, Math.min(size, MAX_PAGE_SIZE)); }
}
