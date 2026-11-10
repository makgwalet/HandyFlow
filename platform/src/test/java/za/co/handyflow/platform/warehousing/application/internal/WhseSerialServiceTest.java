package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseSerialServiceTest {

    private final UUID tenant = UUID.randomUUID(), client = UUID.randomUUID(), item = UUID.randomUUID(), line = UUID.randomUUID(),
            shipment = UUID.randomUUID(), order = UUID.randomUUID(), receipt = UUID.randomUUID(), actor = UUID.randomUUID(), ret = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private WhseSerialService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseSerialService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @FunctionalInterface private interface Stub { void on(ResultSet rs) throws Exception; }

    /** Makes the query whose SQL contains the fragment return one row, built by the service's own row mapper from a stubbed result set. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void row(String fragment, Stub stub) {
        when(jdbc.query(contains(fragment), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            stub.on(rs);
            return List.of(((RowMapper) inv.getArgument(1)).mapRow(rs, 0));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rows(String fragment, Object... values) {
        when(jdbc.query(contains(fragment), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(values));
    }

    private void tracked(boolean on) throws Exception { row("SELECT serial_tracked", rs -> when(rs.getBoolean(1)).thenReturn(on)); }

    private void receiptLine() throws Exception {
        row("FROM whse_inbound_shipment_lines", rs -> { when(rs.getObject(1, UUID.class)).thenReturn(item); when(rs.getObject(2, UUID.class)).thenReturn(client); });
    }

    private void orderLine(String status, String toShip) throws Exception {
        row("FROM whse_outbound_order_lines l JOIN whse_outbound_orders", rs -> {
            when(rs.getObject(1, UUID.class)).thenReturn(item); when(rs.getBigDecimal(2)).thenReturn(new BigDecimal(toShip));
            when(rs.getObject(3, UUID.class)).thenReturn(client); when(rs.getString(4)).thenReturn(status); when(rs.getString(5)).thenReturn("SO-1");
        });
    }

    private void count(String fragment, long n) { when(jdbc.queryForObject(contains(fragment), eq(Long.class), any(Object[].class))).thenReturn(n); }

    // ── formats ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("serials are trimmed, blanks dropped, and the format and duplicates are checked ignoring case")
    void cleaning() {
        assertEquals(List.of("SN-1", "ab.2/3"), WhseSerialService.clean(java.util.Arrays.asList(" SN-1 ", "", null, "ab.2/3")));
        assertTrue(WhseSerialService.clean(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> WhseSerialService.clean(List.of("has space")));
        assertThrows(IllegalArgumentException.class, () -> WhseSerialService.clean(List.of("-leading")));
        assertThrows(IllegalArgumentException.class, () -> WhseSerialService.clean(List.of("x".repeat(81))));
        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class, () -> WhseSerialService.clean(List.of("sn1", "SN1")));
        assertTrue(dup.getMessage().contains("twice"));
        assertThrows(IllegalArgumentException.class, () -> WhseSerialService.clean(java.util.Collections.nCopies(1001, "A")));
    }

    // ── receiving ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a receipt of a serial-tracked item records one serial and one RECEIVED event per unit")
    void receiptRecords() throws Exception {
        receiptLine(); tracked(true);
        assertEquals(2, service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("2"), List.of("A1", "A2"), actor));
        verify(jdbc).batchUpdate(contains("INSERT INTO whse_serials"), anyList());
        verify(jdbc).batchUpdate(contains("INSERT INTO whse_serial_events"), anyList());
    }

    @Test
    @DisplayName("the serial count must equal the units, and units must be whole")
    void receiptCount() throws Exception {
        receiptLine(); tracked(true);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("3"), List.of("A1", "A2"), actor));
        assertTrue(e.getMessage().contains("3 serial number"));
        assertThrows(IllegalArgumentException.class, () -> service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("1.5"), List.of("A1"), actor));
        assertThrows(IllegalArgumentException.class, () -> service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("2"), List.of(), actor));
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    @DisplayName("a serial already on file for the item is refused with SERIAL_EXISTS")
    void receiptDuplicate() throws Exception {
        receiptLine(); tracked(true);
        rows("SELECT serial_number FROM whse_serials", "A2");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("2"), List.of("A1", "A2"), actor));
        assertEquals("SERIAL_EXISTS", e.getErrorCode());
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    @DisplayName("replaying a receipt whose serials are already recorded does nothing")
    void receiptReplay() throws Exception {
        receiptLine(); tracked(true);
        count("receipt_event_id", 2);
        assertEquals(0, service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("2"), List.of("A1", "A2"), actor));
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
    }

    @Test
    @DisplayName("an item that is not serial tracked takes no serials")
    void receiptNotTracked() throws Exception {
        receiptLine(); tracked(false);
        assertEquals(0, service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("2"), List.of(), actor));
        assertThrows(IllegalArgumentException.class, () -> service.registerReceipt(t(), shipment, line, receipt, new BigDecimal("1"), List.of("A1"), actor));
    }

    @Test
    @DisplayName("an unknown receipt line is not found")
    void receiptLineMissing() {
        assertThrows(ResourceNotFoundException.class, () -> service.registerReceipt(t(), shipment, line, receipt, BigDecimal.ONE, List.of("A1"), actor));
    }

    // ── picking ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("picking assigns each serial to the line and writes a PICKED event")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pickAssigns() throws Exception {
        orderLine("PICKING", "2"); tracked(true);
        doReturn(List.of(UUID.randomUUID())).when(jdbc).query(contains("SET status = 'PICKED'"), any(RowMapper.class), any(Object[].class));
        assertEquals(2, service.pick(t(), order, line, List.of("A1", "A2"), actor));
        verify(jdbc, times(2)).update(contains("INSERT INTO whse_serial_events"), any(Object[].class));
    }

    @Test
    @DisplayName("serials can only be picked while the order is being picked, and never more than the units on the line")
    void pickLimits() throws Exception {
        orderLine("PENDING", "2"); tracked(true);
        assertEquals("ORDER_NOT_PICKING", assertThrows(HandyFlowException.class, () -> service.pick(t(), order, line, List.of("A1"), actor)).getErrorCode());
        orderLine("PICKING", "2"); count("outbound_line_id = ? AND status = 'PICKED'", 1);
        assertThrows(IllegalArgumentException.class, () -> service.pick(t(), order, line, List.of("A1", "A2"), actor));
        assertThrows(IllegalArgumentException.class, () -> service.pick(t(), order, line, List.of(), actor));
    }

    @Test
    @DisplayName("a serial that is not in stock is refused, saying why")
    void pickNotAvailable() throws Exception {
        orderLine("PICKING", "2"); tracked(true);
        rows("SELECT status FROM whse_serials", "SHIPPED");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.pick(t(), order, line, List.of("A1"), actor));
        assertEquals("SERIAL_NOT_AVAILABLE", e.getErrorCode());
        assertTrue(e.getMessage().contains("shipped"));
    }

    @Test
    @DisplayName("an item that is not serial tracked cannot have serials picked")
    void pickNotTracked() throws Exception {
        orderLine("PICKING", "2"); tracked(false);
        assertThrows(IllegalArgumentException.class, () -> service.pick(t(), order, line, List.of("A1"), actor));
    }

    @Test
    @DisplayName("taking serials back off a line needs the order to be picking and the serial to be on that line")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void unpick() {
        rows("SELECT o.status", "PICKING");
        assertThrows(IllegalArgumentException.class, () -> service.unpick(t(), order, line, List.of("A1"), actor)); // nothing matched
        doReturn(List.of(UUID.randomUUID())).when(jdbc).query(contains("SET status = 'IN_STOCK'"), any(RowMapper.class), any(Object[].class));
        assertEquals(1, service.unpick(t(), order, line, List.of("A1"), actor));
        rows("SELECT o.status", "SHIPPED");
        assertEquals("ORDER_NOT_PICKING", assertThrows(HandyFlowException.class, () -> service.unpick(t(), order, line, List.of("A1"), actor)).getErrorCode());
    }

    // ── shipping ────────────────────────────────────────────────────────────

    private void serialLine(String toShip, String... recorded) throws Exception {
        row("AND i.serial_tracked", rs -> {
            when(rs.getObject(1, UUID.class)).thenReturn(line); when(rs.getObject(2, UUID.class)).thenReturn(item);
            when(rs.getString(3)).thenReturn("LAP1"); when(rs.getString(4)).thenReturn("Laptop"); when(rs.getBigDecimal(5)).thenReturn(new BigDecimal(toShip));
        });
        rows("SELECT serial_number FROM whse_serials WHERE outbound_line_id", (Object[]) recorded);
    }

    @Test
    @DisplayName("an order cannot ship until every serial-tracked line has a serial for each unit")
    void shipGate() throws Exception {
        serialLine("2", "A1");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.requireCaptured(t(), order));
        assertEquals("SERIALS_MISSING", e.getErrorCode());
        assertTrue(e.getMessage().contains("LAP1 needs 2"));
        serialLine("2", "A1", "A2");
        assertDoesNotThrow(() -> service.requireCaptured(t(), order));
    }

    @Test
    @DisplayName("a line short-picked to nothing needs no serials")
    void shipGateShortToZero() throws Exception {
        serialLine("0");
        assertDoesNotThrow(() -> service.requireCaptured(t(), order));
    }

    @Test
    @DisplayName("shipping marks the picked serials shipped and cancelling gives them back, each with an event")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shipAndRelease() {
        doReturn(List.of(UUID.randomUUID(), UUID.randomUUID())).when(jdbc).query(contains("SET status = 'SHIPPED'"), any(RowMapper.class), any(Object[].class));
        assertEquals(2, service.markShipped(t(), order, actor));
        doReturn(List.of(UUID.randomUUID())).when(jdbc).query(contains("outbound_order_id = ? AND status = 'PICKED' RETURNING"), any(RowMapper.class), any(Object[].class));
        assertEquals(1, service.releasePicked(t(), order, actor));
        verify(jdbc, times(3)).update(contains("INSERT INTO whse_serial_events"), any(Object[].class));
    }

    // ── returns ─────────────────────────────────────────────────────────────

    private void returnLine(UUID orderId) throws Exception {
        row("FROM whse_returns r JOIN whse_return_lines", rs -> {
            when(rs.getObject(1, UUID.class)).thenReturn(client); when(rs.getObject(2, UUID.class)).thenReturn(orderId); when(rs.getObject(3, UUID.class)).thenReturn(item);
        });
    }

    @Test
    @DisplayName("a returned serial must have shipped, on the return's order when it names one")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returns() throws Exception {
        returnLine(order); tracked(true);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.acceptReturn(t(), ret, line, BigDecimal.ONE, List.of("A1"), actor));
        assertEquals("SERIAL_NOT_SHIPPED", e.getErrorCode());
        assertTrue(e.getMessage().contains("on this order"));
        doReturn(List.of(UUID.randomUUID())).when(jdbc).query(contains("return_count = return_count + 1"), any(RowMapper.class), any(Object[].class));
        assertEquals(1, service.acceptReturn(t(), ret, line, BigDecimal.ONE, List.of("A1"), actor));
        verify(jdbc).update(contains("INSERT INTO whse_serial_events"), any(Object[].class));
    }

    @Test
    @DisplayName("a return with no order accepts any shipped serial of that client and item, and the count must match")
    void returnsNoOrder() throws Exception {
        returnLine(null); tracked(true);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.acceptReturn(t(), ret, line, BigDecimal.ONE, List.of("A1"), actor));
        assertFalse(e.getMessage().contains("on this order"));
        assertThrows(IllegalArgumentException.class, () -> service.acceptReturn(t(), ret, line, new BigDecimal("2"), List.of("A1"), actor));
    }

    @Test
    @DisplayName("returning an item that is not serial tracked takes no serials")
    void returnsNotTracked() throws Exception {
        returnLine(order); tracked(false);
        assertEquals(0, service.acceptReturn(t(), ret, line, BigDecimal.ONE, List.of(), actor));
        assertThrows(IllegalArgumentException.class, () -> service.acceptReturn(t(), ret, line, BigDecimal.ONE, List.of("A1"), actor));
    }

    // ── the item switch ─────────────────────────────────────────────────────

    @Test
    @DisplayName("switching tracking on needs zero stock, and is audited")
    void switchOn() throws Exception {
        tracked(false);
        count("FROM whse_inventory", 4);
        assertEquals("ITEM_HAS_STOCK", assertThrows(HandyFlowException.class, () -> service.setSerialTracked(t(), item, true, actor)).getErrorCode());
        count("FROM whse_inventory", 0);
        assertTrue(service.setSerialTracked(t(), item, true, actor));
        verify(jdbc).update(contains("SET serial_tracked"), eq(true), eq(item), eq(tenant));
        verify(jdbc).update(contains("audit_events"), any(Object[].class));
    }

    @Test
    @DisplayName("switching tracking off is refused once serials exist; no change does nothing; an unknown user is refused")
    void switchOff() throws Exception {
        tracked(true);
        count("FROM whse_inventory", 0); count("FROM whse_serials WHERE tenant_id = ? AND item_id", 3);
        assertEquals("SERIALS_EXIST", assertThrows(HandyFlowException.class, () -> service.setSerialTracked(t(), item, false, actor)).getErrorCode());
        assertTrue(service.setSerialTracked(t(), item, true, actor));
        verify(jdbc, never()).update(contains("SET serial_tracked"), any(Object[].class));
        assertThrows(AccessDeniedException.class, () -> service.setSerialTracked(t(), item, true, null));
    }
}
