package za.co.handyflow.platform.clinic.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/** Display names for user ids (the person who signed, prepared or added a note). */
@Component
public class ClinicStaffNames {

    private final JdbcTemplate jdbc;

    public ClinicStaffNames(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** A name for each id that has one; ids with no user row are left out. */
    public Map<UUID, String> names(UUID tenantId, Collection<UUID> ids) {
        Set<UUID> wanted = new LinkedHashSet<>();
        for (UUID id : ids) if (id != null) wanted.add(id);
        if (wanted.isEmpty()) return Map.of();
        String in = String.join(",", Collections.nCopies(wanted.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(wanted);
        Map<UUID, String> out = new HashMap<>();
        jdbc.query("SELECT u.id, NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), '') AS name FROM users u WHERE u.tenant_id = ? AND u.id IN (" + in + ")",
                rs -> { if (rs.getString("name") != null) out.put((UUID) rs.getObject("id"), rs.getString("name")); }, args.toArray());
        return out;
    }
}
