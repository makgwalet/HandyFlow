package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.dto.RoomDtos.CreateRoomRequest;
import za.co.handyflow.platform.clinic.dto.RoomDtos.RoomResponse;
import za.co.handyflow.platform.clinic.dto.RoomDtos.UpdateRoomRequest;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consulting rooms. Switched off, never deleted. */
@Service
@RequiredArgsConstructor
public class ClinicRoomService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<RoomResponse> list(TenantId t, boolean includeInactive) {
        return jdbc.query("SELECT id, name, active FROM clinic_rooms WHERE tenant_id = ?"
                        + (includeInactive ? "" : " AND active") + " ORDER BY lower(name)",
                (rs, i) -> new RoomResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("active")),
                t.getValue());
    }

    @Transactional
    public RoomResponse create(TenantId t, CreateRoomRequest req) {
        String name = RoomRules.cleanName(req.name());
        requireNameFree(t, name, null);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_rooms (id, tenant_id, name) VALUES (?, ?, ?)", id, t.getValue(), name);
        return new RoomResponse(id, name, true);
    }

    @Transactional
    public RoomResponse update(TenantId t, UUID id, UpdateRoomRequest req) {
        RoomResponse current = find(t, id).orElseThrow(() -> new ResourceNotFoundException("Room", id.toString()));
        String name = req.name() != null ? RoomRules.cleanName(req.name()) : current.name();
        boolean active = req.active() != null ? req.active() : current.active();
        if (!name.equalsIgnoreCase(current.name())) requireNameFree(t, name, id);
        jdbc.update("UPDATE clinic_rooms SET name = ?, active = ? WHERE id = ? AND tenant_id = ?", name, active, id, t.getValue());
        return new RoomResponse(id, name, active);
    }

    @Transactional(readOnly = true)
    Optional<RoomResponse> find(TenantId t, UUID id) {
        return jdbc.query("SELECT id, name, active FROM clinic_rooms WHERE id = ? AND tenant_id = ?",
                (rs, i) -> new RoomResponse(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("active")),
                id, t.getValue()).stream().findFirst();
    }

    /** Names for a set of room ids (including switched-off rooms), for showing on appointments. */
    @Transactional(readOnly = true)
    Map<UUID, String> namesByIds(TenantId t, Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        for (UUID id : ids) find(t, id).ifPresent(r -> out.put(r.id(), r.name()));
        return out;
    }

    private void requireNameFree(TenantId t, String name, UUID exceptId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_rooms WHERE tenant_id = ? AND lower(name) = lower(?) "
                + "AND (?::uuid IS NULL OR id <> ?::uuid)", Integer.class, t.getValue(), name, exceptId, exceptId);
        if (n != null && n > 0) throw new ConflictException("There is already a room called \"" + name + "\"");
    }
}
