package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseDispatchServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID manifest = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private final UUID order1 = UUID.randomUUID();
    private final UUID order2 = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseOutboundOrderService orders;
    private WhseNumberGenerator numbers;
    private WhseDispatchService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        orders = mock(WhseOutboundOrderService.class);
        numbers = mock(WhseNumberGenerator.class);
        service = new WhseDispatchService(jdbc, orders, numbers);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void manifest(String status, String vehicle, String driver) {
        when(jdbc.query(contains("FROM whse_dispatch_manifests WHERE tenant_id = ? AND id = ? FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhseDispatchService.Manifest("MAN00007", status, "DHL", vehicle, driver)));
    }

    private void orderCount(int n) {
        when(jdbc.queryForObject(contains("count(*) FROM whse_outbound_orders WHERE manifest_id"), eq(Integer.class), any(Object[].class))).thenReturn(n);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void onManifest(UUID... ids) {
        List rows = new java.util.ArrayList<>();
        for (UUID id : ids) rows.add(new Object[]{id, "ORD-" + id.toString().substring(0, 4)});
        when(jdbc.query(contains("FROM whse_outbound_orders WHERE tenant_id = ? AND manifest_id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(rows);
    }

    @Test
    @DisplayName("a manifest is created open, with the vehicle registration in capitals and the next manifest number")
    void create() {
        when(numbers.nextManifestNumber(t())).thenReturn("MAN00001");
        service.create(t(), " DHL ", "ca 123-456", "Sipho", null, actor);
        verify(jdbc).update(contains("INSERT INTO whse_dispatch_manifests"), any(), eq(tenant), eq("MAN00001"), eq("DHL"), eq("CA 123-456"), eq("Sipho"), isNull(), eq(actor));
        assertThrows(AccessDeniedException.class, () -> service.create(t(), null, null, null, null, null));
    }

    @Test
    @DisplayName("loading cannot start without a vehicle and a driver, or without any order")
    void loadingNeedsVehicleDriverAndOrders() {
        manifest("OPEN", null, "Sipho");
        assertThrows(IllegalArgumentException.class, () -> service.startLoading(t(), manifest, actor));
        manifest("OPEN", "CA 1", "Sipho");
        orderCount(0);
        assertThrows(IllegalStateException.class, () -> service.startLoading(t(), manifest, actor));
        verify(jdbc, never()).update(contains("status = 'LOADING'"), any(Object[].class));
    }

    @Test
    @DisplayName("loading starts on an open manifest with a vehicle, a driver and orders, and is recorded against the user")
    void loadingStarts() {
        manifest("OPEN", "CA 1", "Sipho");
        orderCount(2);
        service.startLoading(t(), manifest, actor);
        verify(jdbc).update(contains("status = 'LOADING'"), eq(actor), eq(manifest));
    }

    @Test
    @DisplayName("only an open manifest can start loading")
    void loadingOnlyFromOpen() {
        manifest("LOADING", "CA 1", "Sipho");
        assertThrows(IllegalStateException.class, () -> service.startLoading(t(), manifest, actor));
    }

    @Test
    @DisplayName("dispatching ships every order on the manifest with its carrier and manifest number, then closes it")
    void dispatchShipsAll() {
        manifest("LOADING", "CA 1", "Sipho");
        onManifest(order1, order2);
        service.dispatch(t(), manifest, actor);
        verify(orders).markShipped(t(), order1, "DHL", "MAN00007", actor);
        verify(orders).markShipped(t(), order2, "DHL", "MAN00007", actor);
        verify(jdbc).update(contains("status = 'DISPATCHED'"), eq(actor), eq(manifest));
    }

    @Test
    @DisplayName("if one order cannot ship, the error names it and the manifest is not marked dispatched")
    void dispatchIsAllOrNothing() {
        manifest("LOADING", "CA 1", "Sipho");
        onManifest(order1, order2);
        when(orders.markShipped(t(), order2, "DHL", "MAN00007", actor)).thenThrow(new IllegalStateException("2 pick tasks are not finished"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.dispatch(t(), manifest, actor));
        assertTrue(e.getMessage().contains("ORD-" + order2.toString().substring(0, 4)));
        assertTrue(e.getMessage().contains("pick tasks"));
        verify(jdbc, never()).update(contains("status = 'DISPATCHED'"), any(Object[].class));
    }

    @Test
    @DisplayName("a manifest that is not loading cannot be dispatched, and an empty one cannot either")
    void dispatchNeedsLoading() {
        manifest("OPEN", "CA 1", "Sipho");
        assertThrows(IllegalStateException.class, () -> service.dispatch(t(), manifest, actor));
        manifest("LOADING", "CA 1", "Sipho");
        onManifest();
        assertThrows(IllegalStateException.class, () -> service.dispatch(t(), manifest, actor));
        verifyNoInteractions(orders);
    }

    @Test
    @DisplayName("cancelling needs a reason, releases the orders, and is refused once dispatched")
    void cancel() {
        manifest("LOADING", "CA 1", "Sipho");
        assertThrows(IllegalArgumentException.class, () -> service.cancel(t(), manifest, " no", actor));
        service.cancel(t(), manifest, "truck broke down", actor);
        verify(jdbc).update(contains("SET manifest_id = NULL"), eq(tenant), eq(manifest));
        verify(jdbc).update(contains("status = 'CANCELLED'"), eq(actor), eq("truck broke down"), eq(manifest));
        manifest("DISPATCHED", "CA 1", "Sipho");
        assertThrows(IllegalStateException.class, () -> service.cancel(t(), manifest, "too late now", actor));
    }

    @Test
    @DisplayName("an order can be added only while the manifest is open or loading, and only if it is packed and free")
    void addOrderRules() {
        manifest("DISPATCHED", null, null);
        assertThrows(IllegalStateException.class, () -> service.addOrder(t(), manifest, order1));
        manifest("OPEN", null, null);
        when(jdbc.update(contains("SET manifest_id = ?"), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.addOrder(t(), manifest, order1));
        when(jdbc.update(contains("SET manifest_id = ?"), any(Object[].class))).thenReturn(1);
        assertDoesNotThrow(() -> service.addOrder(t(), manifest, order1));
    }

    // ── proof of delivery ───────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void shipped(String status, LocalDate shippedOn, Instant deliveredAt) {
        List rows = List.<Object[]>of(new Object[]{status, shippedOn == null ? null : java.sql.Date.valueOf(shippedOn),
                deliveredAt == null ? null : java.sql.Timestamp.from(deliveredAt)});
        when(jdbc.query(contains("SELECT status, shipped_date, delivered_at"), any(RowMapper.class), any(Object[].class))).thenReturn(rows);
    }

    @Test
    @DisplayName("delivery is recorded with who received it and when, and audited")
    void deliveryRecorded() {
        shipped("SHIPPED", LocalDate.now().minusDays(1), null);
        Instant when = Instant.now().minusSeconds(3600);
        service.recordDelivery(t(), order1, " T. Mokoena ", when, null, actor);
        verify(jdbc).update(contains("SET delivered_at = ?"), eq(java.sql.Timestamp.from(when)), eq(actor), eq("T. Mokoena"), isNull(), eq(order1));
        verify(jdbc).update(contains("WHSE_DELIVERY_RECORDED"), any(Object[].class));
    }

    @Test
    @DisplayName("delivery needs a receiver and a time that is not in the future")
    void deliveryValidation() {
        shipped("SHIPPED", LocalDate.now().minusDays(1), null);
        assertThrows(IllegalArgumentException.class, () -> service.recordDelivery(t(), order1, " ", Instant.now(), null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.recordDelivery(t(), order1, "T. Mokoena", null, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.recordDelivery(t(), order1, "T. Mokoena", Instant.now().plusSeconds(7200), null, actor));
    }

    @Test
    @DisplayName("an order cannot be delivered before it shipped, before it ships, or twice")
    void deliveryOrdering() {
        shipped("SHIPPED", LocalDate.now(), null);
        assertThrows(IllegalArgumentException.class, () -> service.recordDelivery(t(), order1, "T. Mokoena", Instant.now().minusSeconds(3 * 86400L), null, actor));
        shipped("PACKED", null, null);
        assertThrows(IllegalStateException.class, () -> service.recordDelivery(t(), order1, "T. Mokoena", Instant.now(), null, actor));
        shipped("SHIPPED", LocalDate.now().minusDays(2), Instant.now().minusSeconds(100));
        assertThrows(IllegalStateException.class, () -> service.recordDelivery(t(), order1, "T. Mokoena", Instant.now(), null, actor));
        verify(jdbc, never()).update(contains("SET delivered_at"), any(Object[].class));
    }
}
