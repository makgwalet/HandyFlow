package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseDockServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID door = UUID.randomUUID();
    private final UUID appt = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final Instant start = Instant.now().plus(Duration.ofHours(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private final Instant end = start.plus(Duration.ofHours(1));

    private JdbcTemplate jdbc;
    private WhseDockService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseDockService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doorType(String type) {
        when(jdbc.query(contains("SELECT location_type FROM whse_locations"), any(RowMapper.class), any(Object[].class))).thenReturn(type == null ? List.of() : List.of(type));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void existing(Instant s, Instant e) {
        when(jdbc.query(contains("FROM whse_dock_appointments WHERE tenant_id = ? AND dock_location_id"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(s == null ? List.of() : List.<Instant[]>of(new Instant[]{s, e}));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void status(String st) {
        when(jdbc.query(contains("SELECT status FROM whse_dock_appointments"), any(RowMapper.class), any(Object[].class))).thenReturn(st == null ? List.of() : List.of(st));
    }

    private WhseDockService.Booking booking(String dir, UUID shipment, UUID manifest) {
        return new WhseDockService.Booking(dir, door, start, end, " Acme Freight ", "CA 123", null, shipment, manifest, null);
    }

    @Test @DisplayName("a free door is booked")
    void books() {
        doorType("DOCK");
        existing(null, null);
        UUID id = service.book(t(), booking("inbound", null, null), actor);
        assertNotNull(id);
        verify(jdbc).update(contains("INSERT INTO whse_dock_appointments"), eq(id), eq(tenant), eq("INBOUND"), eq(door), any(Timestamp.class), any(Timestamp.class), eq("Acme Freight"),
                eq("CA 123"), isNull(), isNull(), isNull(), isNull(), eq(actor));
    }

    @Test @DisplayName("a door that is already booked in that slot is refused, back-to-back is fine")
    void doubleBooking() {
        doorType("DOCK");
        existing(start.minus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(30)));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.book(t(), booking("INBOUND", null, null), actor));
        assertTrue(e.getMessage().contains("already booked"));
        verify(jdbc, never()).update(contains("INSERT INTO whse_dock_appointments"), any(Object[].class));
        existing(start.minus(Duration.ofHours(1)), start); // ends exactly as ours starts
        assertNotNull(service.book(t(), booking("INBOUND", null, null), actor));
    }

    @Test @DisplayName("only a dock door can be booked")
    void onlyDocks() {
        doorType("BIN");
        assertThrows(IllegalArgumentException.class, () -> service.book(t(), booking("INBOUND", null, null), actor));
        doorType(null);
        assertThrows(ResourceNotFoundException.class, () -> service.book(t(), booking("INBOUND", null, null), actor));
    }

    @Test @DisplayName("a delivery links a shipment and a collection a manifest, not the other way round")
    void links() {
        assertThrows(IllegalArgumentException.class, () -> service.book(t(), booking("INBOUND", null, UUID.randomUUID()), actor));
        assertThrows(IllegalArgumentException.class, () -> service.book(t(), booking("OUTBOUND", UUID.randomUUID(), null), actor));
    }

    @Test @DisplayName("a shipment that is already received or cancelled cannot be booked")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void closedShipment() {
        doorType("DOCK");
        existing(null, null);
        when(jdbc.query(contains("FROM whse_inbound_shipments"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("RECEIVED"));
        assertThrows(IllegalStateException.class, () -> service.book(t(), booking("INBOUND", UUID.randomUUID(), null), actor));
        when(jdbc.query(contains("FROM whse_inbound_shipments"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("EXPECTED"));
        assertNotNull(service.book(t(), booking("INBOUND", UUID.randomUUID(), null), actor));
    }

    @Test @DisplayName("a manifest must be open or loading, and a second live appointment for it is refused")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void manifest() {
        doorType("DOCK");
        existing(null, null);
        when(jdbc.query(contains("FROM whse_dispatch_manifests"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("DISPATCHED"));
        assertThrows(IllegalStateException.class, () -> service.book(t(), booking("OUTBOUND", null, UUID.randomUUID()), actor));
        when(jdbc.query(contains("FROM whse_dispatch_manifests"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("OPEN"));
        when(jdbc.update(contains("INSERT INTO whse_dock_appointments"), any(Object[].class))).thenThrow(new DuplicateKeyException("dup"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.book(t(), booking("OUTBOUND", null, UUID.randomUUID()), actor));
        assertTrue(e.getMessage().contains("already has a live appointment"));
    }

    @Test @DisplayName("an unidentified user cannot book or change anything")
    void needsActor() {
        assertThrows(AccessDeniedException.class, () -> service.book(t(), booking("INBOUND", null, null), null));
        assertThrows(AccessDeniedException.class, () -> service.arrived(t(), appt, null));
        assertThrows(AccessDeniedException.class, () -> service.cancel(t(), appt, "No longer needed", null));
    }

    @Test @DisplayName("the day runs scheduled, arrived, at dock, completed")
    void happyPath() {
        status("SCHEDULED");
        service.arrived(t(), appt, actor);
        verify(jdbc).update(contains("status = 'ARRIVED', arrived_at = now()"), eq(appt));
        status("ARRIVED");
        service.atDock(t(), appt, actor);
        verify(jdbc).update(contains("status = 'AT_DOCK'"), eq(appt));
        status("AT_DOCK");
        service.complete(t(), appt, actor);
        verify(jdbc).update(contains("status = 'COMPLETED'"), eq(appt));
    }

    @Test @DisplayName("steps cannot be skipped, and an unknown appointment is not found")
    void order() {
        status("SCHEDULED");
        assertThrows(IllegalStateException.class, () -> service.atDock(t(), appt, actor));
        assertThrows(IllegalStateException.class, () -> service.complete(t(), appt, actor));
        status(null);
        assertThrows(ResourceNotFoundException.class, () -> service.arrived(t(), appt, actor));
    }

    @Test @DisplayName("cancelling needs a reason and is only for scheduled or arrived appointments")
    void cancel() {
        status("SCHEDULED");
        assertThrows(IllegalArgumentException.class, () -> service.cancel(t(), appt, "no", actor));
        service.cancel(t(), appt, " Carrier called off ", actor);
        verify(jdbc).update(contains("status = 'CANCELLED'"), eq("Carrier called off"), eq(appt));
        status("AT_DOCK");
        assertThrows(IllegalStateException.class, () -> service.cancel(t(), appt, "Carrier called off", actor));
    }

    @Test @DisplayName("a no-show can only be recorded once the slot has started")
    void noShow() {
        status("SCHEDULED");
        when(jdbc.queryForObject(contains("SELECT starts_at"), eq(Timestamp.class), any(Object[].class))).thenReturn(Timestamp.from(Instant.now().plusSeconds(3600)));
        assertThrows(IllegalStateException.class, () -> service.noShow(t(), appt, "Did not turn up", actor));
        when(jdbc.queryForObject(contains("SELECT starts_at"), eq(Timestamp.class), any(Object[].class))).thenReturn(Timestamp.from(Instant.now().minusSeconds(3600)));
        service.noShow(t(), appt, "Did not turn up", actor);
        verify(jdbc).update(contains("status = 'NO_SHOW'"), eq("Did not turn up"), eq(appt));
    }

    @Test @DisplayName("only a scheduled appointment moves, and it must not clash with another")
    void reschedule() {
        status("ARRIVED");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.reschedule(t(), appt, door, start, end, actor));
        assertTrue(e.getMessage().contains("scheduled"));
        status("SCHEDULED");
        doorType("DOCK");
        existing(start, end);
        assertThrows(HandyFlowException.class, () -> service.reschedule(t(), appt, door, start, end, actor));
        existing(null, null);
        service.reschedule(t(), appt, door, start, end, actor);
        verify(jdbc).update(contains("SET dock_location_id = ?, starts_at = ?"), eq(door), any(Timestamp.class), any(Timestamp.class), eq(appt));
    }

    @Test @DisplayName("listing needs a sensible period")
    void listPeriod() {
        assertThrows(IllegalArgumentException.class, () -> service.list(t(), end, start, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(t(), start, start.plus(Duration.ofDays(90)), null));
    }
}
