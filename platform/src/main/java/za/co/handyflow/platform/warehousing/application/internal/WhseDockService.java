package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Dock appointments: a carrier booked into a dock door for a slot, to deliver an inbound shipment or collect a dispatch. Two live appointments
 * cannot overlap on the same door; the check runs under a lock on the door's location row so two people booking at once cannot both succeed.
 */
@Service
@RequiredArgsConstructor
public class WhseDockService {

    private final JdbcTemplate jdbc;

    public record Appointment(UUID id, String direction, UUID dockLocationId, String dockCode, Instant startsAt, Instant endsAt, String carrier,
                              String vehicleRegistration, String driverName, UUID inboundShipmentId, String shipmentReference, UUID manifestId, String manifestNumber,
                              String notes, String status, Instant arrivedAt, Instant atDockAt, Instant completedAt, String cancelReason, long lateMinutes) {}

    private static final String SELECT = """
            SELECT a.id, a.direction, a.dock_location_id, l.code, a.starts_at, a.ends_at, a.carrier, a.vehicle_registration, a.driver_name,
                   a.inbound_shipment_id, s.reference_number, a.manifest_id, m.manifest_number, a.notes, a.status, a.arrived_at, a.at_dock_at, a.completed_at, a.cancel_reason
              FROM whse_dock_appointments a JOIN whse_locations l ON l.id = a.dock_location_id
              LEFT JOIN whse_inbound_shipments s ON s.id = a.inbound_shipment_id
              LEFT JOIN whse_dispatch_manifests m ON m.id = a.manifest_id
            """;

    private static Appointment row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Instant start = rs.getTimestamp(5).toInstant();
        java.sql.Timestamp arr = rs.getTimestamp(16);
        Instant arrived = arr == null ? null : arr.toInstant();
        return new Appointment(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), start, rs.getTimestamp(6).toInstant(),
                rs.getString(7), rs.getString(8), rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11), rs.getObject(12, UUID.class), rs.getString(13),
                rs.getString(14), rs.getString(15), arrived, rs.getTimestamp(17) == null ? null : rs.getTimestamp(17).toInstant(),
                rs.getTimestamp(18) == null ? null : rs.getTimestamp(18).toInstant(), rs.getString(19), WhseDockRules.lateMinutes(start, arrived));
    }

    /** Appointments that start in [from, to), optionally for one door, in time order. */
    @Transactional(readOnly = true)
    public List<Appointment> list(TenantId tenantId, Instant from, Instant to, UUID dockLocationId) {
        if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("Give a period to look at.");
        if (java.time.Duration.between(from, to).toDays() > 62) throw new IllegalArgumentException("Look at no more than two months at a time.");
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE a.tenant_id = ? AND a.starts_at >= ? AND a.starts_at < ?");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>(List.of(tenantId.getValue(), java.sql.Timestamp.from(from), java.sql.Timestamp.from(to)));
        if (dockLocationId != null) { sql.append(" AND a.dock_location_id = ?"); args.add(dockLocationId); }
        sql.append(" ORDER BY a.starts_at, l.code, a.id");
        return jdbc.query(sql.toString(), WhseDockService::row, args.toArray());
    }

    @Transactional(readOnly = true)
    public Appointment get(TenantId tenantId, UUID id) {
        List<Appointment> r = jdbc.query(SELECT + " WHERE a.tenant_id = ? AND a.id = ?", WhseDockService::row, tenantId.getValue(), id);
        if (r.isEmpty()) throw new ResourceNotFoundException("WhseDockAppointment", id.toString());
        return r.get(0);
    }

    public record Booking(String direction, UUID dockLocationId, Instant startsAt, Instant endsAt, String carrier, String vehicleRegistration, String driverName,
                          UUID inboundShipmentId, UUID manifestId, String notes) {}

    @Transactional
    public UUID book(TenantId tenantId, Booking b, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        String dir = WhseDockRules.direction(b.direction());
        WhseDockRules.checkSlot(b.startsAt(), b.endsAt());
        if (dir.equals("INBOUND") && b.manifestId() != null) throw new IllegalArgumentException("A delivery is linked to an inbound shipment, not a dispatch manifest.");
        if (dir.equals("OUTBOUND") && b.inboundShipmentId() != null) throw new IllegalArgumentException("A collection is linked to a dispatch manifest, not an inbound shipment.");
        lockDoor(t, b.dockLocationId());
        requireFree(t, b.dockLocationId(), b.startsAt(), b.endsAt(), null);
        if (b.inboundShipmentId() != null) {
            List<String> st = jdbc.query("SELECT status FROM whse_inbound_shipments WHERE tenant_id = ? AND id = ?", (rs, n) -> rs.getString(1), t, b.inboundShipmentId());
            if (st.isEmpty()) throw new ResourceNotFoundException("WhseInboundShipment", b.inboundShipmentId().toString());
            if (!"EXPECTED".equals(st.get(0)) && !"PARTIALLY_RECEIVED".equals(st.get(0))) {
                throw new IllegalStateException("That shipment is " + st.get(0).toLowerCase().replace('_', ' ') + ", so there is nothing left to book a delivery for.");
            }
        }
        if (b.manifestId() != null) {
            List<String> st = jdbc.query("SELECT status FROM whse_dispatch_manifests WHERE tenant_id = ? AND id = ?", (rs, n) -> rs.getString(1), t, b.manifestId());
            if (st.isEmpty()) throw new ResourceNotFoundException("WhseDispatchManifest", b.manifestId().toString());
            if (!"OPEN".equals(st.get(0)) && !"LOADING".equals(st.get(0))) throw new IllegalStateException("That manifest is " + st.get(0).toLowerCase() + ", so it cannot be booked for collection.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO whse_dock_appointments (id, tenant_id, direction, dock_location_id, starts_at, ends_at, carrier, vehicle_registration, driver_name, inbound_shipment_id, manifest_id, notes, created_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, t, dir, b.dockLocationId(), java.sql.Timestamp.from(b.startsAt()), java.sql.Timestamp.from(b.endsAt()), blank(b.carrier()), blank(b.vehicleRegistration()),
                    blank(b.driverName()), b.inboundShipmentId(), b.manifestId(), blank(b.notes()), actor);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("That " + (b.inboundShipmentId() != null ? "shipment" : "manifest") + " already has a live appointment. Cancel or finish it first.");
        }
        return id;
    }

    /** Moves a SCHEDULED appointment to another slot and/or door. */
    @Transactional
    public void reschedule(TenantId tenantId, UUID id, UUID dockLocationId, Instant startsAt, Instant endsAt, UUID actor) {
        requireActor(actor);
        UUID t = tenantId.getValue();
        WhseDockRules.checkSlot(startsAt, endsAt);
        String status = lockAppointment(t, id);
        if (!"SCHEDULED".equals(status)) throw conflict("NOT_SCHEDULED", "Only a scheduled appointment can be moved (this one is " + status.toLowerCase().replace('_', ' ') + ").");
        lockDoor(t, dockLocationId);
        requireFree(t, dockLocationId, startsAt, endsAt, id);
        jdbc.update("UPDATE whse_dock_appointments SET dock_location_id = ?, starts_at = ?, ends_at = ?, updated_at = now() WHERE id = ?",
                dockLocationId, java.sql.Timestamp.from(startsAt), java.sql.Timestamp.from(endsAt), id);
    }

    @Transactional
    public void arrived(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        String s = lockAppointment(tenantId.getValue(), id);
        WhseDockRules.checkTransition(s, "ARRIVED");
        jdbc.update("UPDATE whse_dock_appointments SET status = 'ARRIVED', arrived_at = now(), updated_at = now() WHERE id = ?", id);
    }

    @Transactional
    public void atDock(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        String s = lockAppointment(tenantId.getValue(), id);
        WhseDockRules.checkTransition(s, "AT_DOCK");
        jdbc.update("UPDATE whse_dock_appointments SET status = 'AT_DOCK', at_dock_at = now(), updated_at = now() WHERE id = ?", id);
    }

    @Transactional
    public void complete(TenantId tenantId, UUID id, UUID actor) {
        requireActor(actor);
        String s = lockAppointment(tenantId.getValue(), id);
        WhseDockRules.checkTransition(s, "COMPLETED");
        jdbc.update("UPDATE whse_dock_appointments SET status = 'COMPLETED', completed_at = now(), updated_at = now() WHERE id = ?", id);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID id, String reason, UUID actor) {
        requireActor(actor);
        String r = requireReason(reason);
        String s = lockAppointment(tenantId.getValue(), id);
        WhseDockRules.checkTransition(s, "CANCELLED");
        jdbc.update("UPDATE whse_dock_appointments SET status = 'CANCELLED', cancel_reason = ?, updated_at = now() WHERE id = ?", r, id);
    }

    @Transactional
    public void noShow(TenantId tenantId, UUID id, String reason, UUID actor) {
        requireActor(actor);
        String r = requireReason(reason);
        UUID t = tenantId.getValue();
        String s = lockAppointment(t, id);
        WhseDockRules.checkTransition(s, "NO_SHOW");
        Instant start = jdbc.queryForObject("SELECT starts_at FROM whse_dock_appointments WHERE id = ?", java.sql.Timestamp.class, id).toInstant();
        WhseDockRules.checkNoShow(start, Instant.now());
        jdbc.update("UPDATE whse_dock_appointments SET status = 'NO_SHOW', cancel_reason = ?, updated_at = now() WHERE id = ?", r, id);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void lockDoor(UUID t, UUID dockLocationId) {
        List<String> type = jdbc.query("SELECT location_type FROM whse_locations WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL AND active = true FOR UPDATE",
                (rs, n) -> rs.getString(1), t, dockLocationId);
        if (type.isEmpty()) throw new ResourceNotFoundException("WhseLocation", String.valueOf(dockLocationId));
        if (!"DOCK".equals(type.get(0))) throw new IllegalArgumentException("Appointments are booked on a dock door. That location is a " + type.get(0).toLowerCase() + ".");
    }

    private void requireFree(UUID t, UUID dock, Instant start, Instant end, UUID ignore) {
        List<Instant[]> clashes = jdbc.query("SELECT starts_at, ends_at FROM whse_dock_appointments WHERE tenant_id = ? AND dock_location_id = ? AND status IN ('SCHEDULED','ARRIVED','AT_DOCK') "
                        + "AND starts_at < ? AND ends_at > ? AND (? IS NULL OR id <> ?)",
                (rs, n) -> new Instant[]{rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()},
                t, dock, java.sql.Timestamp.from(end), java.sql.Timestamp.from(start), ignore, ignore);
        for (Instant[] c : clashes) {
            if (WhseDockRules.overlaps(start, end, c[0], c[1])) {
                throw conflict("DOCK_BUSY", "That door is already booked from " + c[0] + " to " + c[1] + ".");
            }
        }
    }

    private String lockAppointment(UUID t, UUID id) {
        List<String> s = jdbc.query("SELECT status FROM whse_dock_appointments WHERE tenant_id = ? AND id = ? FOR UPDATE", (rs, n) -> rs.getString(1), t, id);
        if (s.isEmpty()) throw new ResourceNotFoundException("WhseDockAppointment", id.toString());
        return s.get(0);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason (at least 5 characters).");
        return reason.trim();
    }

    private static HandyFlowException conflict(String code, String msg) { return new HandyFlowException(msg, HttpStatus.CONFLICT, code); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static void requireActor(UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
    }
}
