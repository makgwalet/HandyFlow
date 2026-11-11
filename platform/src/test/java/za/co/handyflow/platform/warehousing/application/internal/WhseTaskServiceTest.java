package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseTaskServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID from = UUID.randomUUID();
    private final UUID dest = UUID.randomUUID();
    private final UUID lineId = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseInventoryService inventory;
    private WhseLotAllocationService lotAllocation;
    private WhseCycleCountService cycleCounts;
    private WhseTaskService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventory = mock(WhseInventoryService.class);
        lotAllocation = mock(WhseLotAllocationService.class);
        cycleCounts = mock(WhseCycleCountService.class);
        service = new WhseTaskService(jdbc, inventory, lotAllocation, cycleCounts);
    }

    private TenantId t() { return TenantId.of(tenant); }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    /** The locked row read in complete(): type, status, assignee, client, item, lot, from, to, qty, reference, number, line. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void lockedTask(String type, String status, UUID assignee, UUID to, String qty) {
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<Object[]>of(new Object[]{type, status, assignee, client, item, null, from, to, bd(qty), UUID.randomUUID(), "OUT-1", lineId}));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void candidate(String code, String type, String capacity, String onHand, boolean otherClient) {
        when(jdbc.query(contains("FROM whse_locations loc"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhsePutawayRules.Candidate(dest, code, type, true, capacity == null ? null : bd(capacity), bd(onHand), 1, false, otherClient)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void taskAfterwards() {
        when(jdbc.query(contains("WHERE t.tenant_id = ? AND t.id = ?"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhseTaskService.TaskRow(taskId, "PICK", "DONE", 5, client, "C", item, "SKU", "d", null, null, null, from, "A-01", null, null,
                        bd("4"), bd("4"), 1, null, "OUT-1", user, null, null, null)));
    }

    @Test @DisplayName("claiming takes an open task and reports a task someone else holds")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void claim() {
        taskAfterwards();
        when(jdbc.update(contains("SET status = 'IN_PROGRESS'"), any(Object[].class))).thenReturn(1);
        assertNotNull(service.claim(t(), taskId, user));
        when(jdbc.update(contains("SET status = 'IN_PROGRESS'"), any(Object[].class))).thenReturn(0);
        when(jdbc.query(contains("WHERE t.tenant_id = ? AND t.id = ?"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhseTaskService.TaskRow(taskId, "PICK", "IN_PROGRESS", 5, client, "C", item, "SKU", "d", null, null, null, from, "A-01", null, null,
                        bd("4"), bd("0"), 1, null, "OUT-1", other, null, null, null)));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.claim(t(), taskId, user));
        assertEquals("TASK_TAKEN", e.getErrorCode());
    }

    @Test @DisplayName("every action needs a known user")
    void needsUser() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.claim(t(), taskId, null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.complete(t(), taskId, bd("1"), null, null, null));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.cancel(t(), taskId, "no longer needed", null));
    }

    @Test @DisplayName("handing a task back is refused when it is not yours")
    void releaseRefused() {
        when(jdbc.update(contains("SET status = 'OPEN'"), any(Object[].class))).thenReturn(0);
        assertThrows(HandyFlowException.class, () -> service.release(t(), taskId, user, false));
    }

    @Test @DisplayName("a full pick is done and the order line's picked quantity is brought up to date; no stock moves")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void fullPick() {
        lockedTask("PICK", "OPEN", null, null, "4");
        taskAfterwards();
        service.complete(t(), taskId, bd("4"), null, null, user);
        verify(jdbc).update(contains("SET status = ?, qty_done = ?"), eq("DONE"), eq(bd("4")), isNull(), eq(user), eq(user), eq(taskId));
        verify(jdbc).update(contains("UPDATE whse_outbound_order_lines SET qty_picked"), eq(lineId), eq(lineId));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("picking less than asked needs a reason and leaves the task short")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shortPick() {
        lockedTask("PICK", "IN_PROGRESS", user, null, "4");
        taskAfterwards();
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("2"), " ", null, user));
        service.complete(t(), taskId, bd("2"), " only two on the shelf ", null, user);
        verify(jdbc).update(contains("SET status = ?, qty_done = ?"), eq("SHORT"), eq(bd("2")), eq("only two on the shelf"), eq(user), eq(user), eq(taskId));
    }

    @Test @DisplayName("more than asked, zero, someone else's task and a finished task are refused")
    void completeRefusals() {
        lockedTask("PICK", "OPEN", null, null, "4");
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("5"), null, null, user));
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("0"), null, null, user));
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, null, null, null, user));
        lockedTask("PICK", "IN_PROGRESS", other, null, "4");
        assertEquals("TASK_TAKEN", assertThrows(HandyFlowException.class, () -> service.complete(t(), taskId, bd("4"), null, null, user)).getErrorCode());
        lockedTask("PICK", "DONE", user, null, "4");
        assertEquals("TASK_FINISHED", assertThrows(HandyFlowException.class, () -> service.complete(t(), taskId, bd("4"), null, null, user)).getErrorCode());
        verify(jdbc, never()).update(contains("SET status = ?, qty_done = ?"), any(Object[].class));
    }

    @Test @DisplayName("a put-away moves the stock to the suggested location")
    void putaway() {
        lockedTask("PUTAWAY", "OPEN", null, dest, "6");
        candidate("A-02", "BIN", null, "0", false);
        taskAfterwards();
        service.complete(t(), taskId, bd("6"), null, null, user);
        verify(inventory).transfer(eq(t()), eq(client), eq(item), eq(from), eq(dest), isNull(), eq("AVAILABLE"), eq(bd("6")), eq("PUTAWAY"), eq("WHSE_TASK"), eq(taskId),
                anyString(), isNull(), eq(user), eq("task:" + taskId));
        verify(jdbc).update(contains("SET status = 'DONE', qty_done = ?, to_location_id = ?"), eq(bd("6")), eq(dest), eq(user), eq(user), eq(taskId));
    }

    @Test @DisplayName("a replenishment moves stock to its pick face as a transfer, not a put-away")
    void replenishment() {
        lockedTask("REPLEN", "OPEN", null, dest, "6");
        candidate("A-02", "BIN", null, "0", false);
        taskAfterwards();
        service.complete(t(), taskId, bd("6"), null, null, user);
        verify(inventory).transfer(eq(t()), eq(client), eq(item), eq(from), eq(dest), isNull(), eq("AVAILABLE"), eq(bd("6")), eq("TRANSFER"), eq("WHSE_TASK"), eq(taskId),
                eq("Replenishment"), isNull(), eq(user), eq("task:" + taskId));
    }

    @Test @DisplayName("a replenishment cannot be sent to any other location than its face")
    void replenishmentFixedDestination() {
        lockedTask("REPLEN", "OPEN", null, dest, "6");
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("6"), null, UUID.randomUUID(), user));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a replenishment moves something: zero is refused")
    void replenishmentNeedsQty() {
        lockedTask("REPLEN", "OPEN", null, dest, "6");
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, BigDecimal.ZERO, null, null, user));
    }

    @Test @DisplayName("the summary counts replenishments apart from put-aways")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void summaryExcludesReplen() throws Exception {
        doAnswer(inv -> {
            org.springframework.jdbc.core.RowCallbackHandler h = inv.getArgument(1);
            for (Object[] r : new Object[][]{{"PUTAWAY", "OPEN", 2}, {"REPLEN", "OPEN", 7}, {"REPLEN", "IN_PROGRESS", 4}, {"PICK", "OPEN", 3}}) {
                java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                when(rs.getString(1)).thenReturn((String) r[0]);
                when(rs.getString(2)).thenReturn((String) r[1]);
                when(rs.getInt(3)).thenReturn((Integer) r[2]);
                h.processRow(rs);
            }
            return null;
        }).when(jdbc).query(contains("GROUP BY task_type, status"), any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));
        WhseTaskService.Summary s = service.summary(t());
        assertEquals(2, s.openPutaways());
        assertEquals(0, s.inProgressPutaways());
        assertEquals(3, s.openPicks());
    }

    @Test @DisplayName("a put-away needs a destination, and refuses one that is full, a dock, or holds another client's stock")
    void putawayRefusals() {
        lockedTask("PUTAWAY", "OPEN", null, null, "6");
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("6"), null, null, user));
        lockedTask("PUTAWAY", "OPEN", null, dest, "6");
        candidate("B-01", "BIN", "10", "8", false);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("6"), null, null, user)).getMessage().contains("room for 2"));
        candidate("DOCK-2", "DOCK", null, "0", false);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("6"), null, null, user)).getMessage().contains("not storage"));
        candidate("C-01", "BIN", null, "3", true);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("6"), null, null, user)).getMessage().contains("another client"));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a partly done put-away is allowed: the rest stays to be put away by a later task")
    void partialPutaway() {
        lockedTask("PUTAWAY", "OPEN", null, dest, "6");
        candidate("A-02", "BIN", null, "0", false);
        taskAfterwards();
        service.complete(t(), taskId, bd("2"), null, null, user);
        verify(inventory).transfer(any(), any(), any(), any(), any(), any(), any(), eq(bd("2")), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("cancelling needs a reason and an unfinished task")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cancel() {
        assertThrows(IllegalArgumentException.class, () -> service.cancel(t(), taskId, "no", user));
        taskAfterwards();
        when(jdbc.update(contains("SET status = 'CANCELLED'"), any(Object[].class))).thenReturn(1);
        assertNotNull(service.cancel(t(), taskId, "stock was recalled", user));
        when(jdbc.update(contains("SET status = 'CANCELLED'"), any(Object[].class))).thenReturn(0);
        assertEquals("TASK_FINISHED", assertThrows(HandyFlowException.class, () -> service.cancel(t(), taskId, "stock was recalled", user)).getErrorCode());
    }

    @Test @DisplayName("pick tasks are not created twice for an order")
    void noDuplicatePickTasks() {
        when(jdbc.queryForObject(contains("count(*) FROM whse_tasks"), eq(Integer.class), any(Object[].class))).thenReturn(2);
        assertEquals(0, service.createPickTasks(t(), UUID.randomUUID()));
        verify(jdbc, never()).update(contains("INSERT INTO whse_tasks"), any(Object[].class));
    }

    @Test @DisplayName("an order's pick tasks are created from its lot allocations and its other allocated lines")
    void createsPickTasks() {
        when(jdbc.queryForObject(contains("count(*) FROM whse_tasks"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.update(contains("FROM whse_order_lot_allocations a JOIN"), any(Object[].class))).thenReturn(2);
        when(jdbc.update(contains("FROM whse_outbound_order_lines ol JOIN"), any(Object[].class))).thenReturn(1);
        assertEquals(3, service.createPickTasks(t(), UUID.randomUUID()));
    }

    @Test @DisplayName("unfinished picks count open, held and short tasks")
    void unfinished() {
        when(jdbc.queryForObject(contains("status IN ('OPEN','IN_PROGRESS','SHORT')"), eq(Integer.class), any(Object[].class))).thenReturn(3);
        assertEquals(3, service.unfinishedPickTasks(t(), UUID.randomUUID()));
        when(jdbc.queryForObject(contains("status IN ('OPEN','IN_PROGRESS','SHORT')"), eq(Integer.class), any(Object[].class))).thenReturn(null);
        assertEquals(0, service.unfinishedPickTasks(t(), UUID.randomUUID()));
    }

    // ── short picks ─────────────────────────────────────────────────────────────────────────────────────────────

    private final UUID orderId = UUID.randomUUID();
    private final UUID lot = UUID.randomUUID();
    private final UUID invId = UUID.randomUUID();
    private final UUID allocId = UUID.randomUUID();

    /** The locked row read in resolveShort(): type, status, client, item, lot, from, qty, qty done, order, reference, line, priority. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void shortTask(String status, UUID lotId, String qty, String done) {
        when(jdbc.query(contains("FROM whse_tasks WHERE tenant_id = ? AND id = ? FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<Object[]>of(new Object[]{"PICK", status, client, item, lotId, from, bd(qty), bd(done), orderId, "OUT-1", lineId, 5}));
        when(jdbc.queryForObject(contains("FROM whse_outbound_orders"), eq(String.class), any(Object[].class))).thenReturn("PICKING");
        taskAfterwards();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void allocation(String qty) {
        when(jdbc.query(contains("FROM whse_order_lot_allocations WHERE tenant_id = ? AND order_line_id = ? AND location_id"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<Object[]>of(new Object[]{allocId, invId, bd(qty)}));
    }

    @Test @DisplayName("finding none of a pick is a short pick with a reason; a put-away of none is refused")
    void zeroPick() {
        lockedTask("PICK", "OPEN", null, null, "4");
        taskAfterwards();
        service.complete(t(), taskId, bd("0"), "bin is empty", null, user);
        verify(jdbc).update(contains("SET status = ?, qty_done = ?"), eq("SHORT"), eq(bd("0")), eq("bin is empty"), eq(user), eq(user), eq(taskId));
        lockedTask("PUTAWAY", "OPEN", null, dest, "6");
        assertThrows(IllegalArgumentException.class, () -> service.complete(t(), taskId, bd("0"), "x", null, user));
    }

    @Test @DisplayName("shipping a line short releases the unfound quantity, records it on the line and closes the task")
    void shipShortLegacy() {
        shortTask("SHORT", null, "10", "6");
        service.resolveShort(t(), taskId, "SHIP_SHORT", "customer agreed to 6", user);
        verify(inventory).deallocateStock(eq(t()), eq(client), eq(item), eq(from), eq(bd("4")), eq("OUTBOUND_ORDER"), eq(orderId), eq("OUT-1"), eq(user));
        verify(jdbc).update(contains("SET qty_short = qty_short + ?"), eq(bd("4")), eq(lineId));
        verify(jdbc).update(contains("SET status = 'SHORT_CLOSED'"), eq("SHIP_SHORT"), eq("customer agreed to 6"), eq(user), eq(taskId));
        verify(jdbc).update(contains("audit_events"), any(Object[].class));
        verifyNoInteractions(cycleCounts);
    }

    @Test @DisplayName("shipping a lot line short shrinks its allocation, or releases it when none was found")
    void shipShortLot() {
        shortTask("SHORT", lot, "10", "6");
        allocation("10");
        service.resolveShort(t(), taskId, "SHIP_SHORT", "customer agreed to 6", user);
        verify(inventory).deallocateById(eq(t()), eq(invId), eq(bd("4")), eq("OUTBOUND_ORDER"), eq(orderId), eq("OUT-1"), eq(user));
        verify(jdbc).update(contains("SET qty = ?"), eq(bd("6")), eq(allocId));

        shortTask("SHORT", lot, "10", "0");
        allocation("10");
        service.resolveShort(t(), taskId, "SHIP_SHORT", "none on the shelf", user);
        verify(jdbc).update(contains("SET status = 'RELEASED'"), eq(allocId));
    }

    @Test @DisplayName("finding a lot line's shortfall elsewhere allocates it again without the positions the line already uses")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findElsewhereLot() {
        shortTask("SHORT", lot, "10", "6");
        allocation("10");
        when(jdbc.query(contains("SELECT DISTINCT inventory_id"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(invId));
        service.resolveShort(t(), taskId, "FIND_ELSEWHERE", "stock found in B-02", user);
        verify(lotAllocation).allocate(eq(t()), eq(orderId), eq("OUT-1"), eq(lineId), eq(client), eq(item), eq(bd("4")), eq(user), eq(java.util.Set.of(invId)));
        verify(jdbc).update(contains("SET status = 'SHORT_CLOSED'"), eq("SOURCE_ELSEWHERE"), eq("stock found in B-02"), eq(user), eq(taskId));
        verify(jdbc, never()).update(contains("qty_short = qty_short + ?"), any(Object[].class));
        verify(cycleCounts).raiseForLocation(eq(t()), eq(from), contains("OUT-1"), eq(user));
    }

    @Test @DisplayName("a line that is not lot tracked moves wholly to another location that holds all of it, or is refused")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findElsewhereLegacy() {
        shortTask("SHORT", null, "10", "6");
        when(jdbc.queryForObject(contains("qty_ordered - qty_short"), eq(BigDecimal.class), any(Object[].class))).thenReturn(bd("10"));
        when(jdbc.query(contains("i.location_id <> ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.resolveShort(t(), taskId, "FIND_ELSEWHERE", "look elsewhere", user));
        verifyNoInteractions(inventory);

        when(jdbc.query(contains("i.location_id <> ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(dest));
        service.resolveShort(t(), taskId, "FIND_ELSEWHERE", "look elsewhere", user);
        verify(inventory).deallocateStock(eq(t()), eq(client), eq(item), eq(from), eq(bd("10")), eq("OUTBOUND_ORDER"), eq(orderId), eq("OUT-1"), eq(user));
        verify(inventory).allocateStock(eq(t()), eq(client), eq(item), eq(dest), eq(bd("10")), eq("OUTBOUND_ORDER"), eq(orderId), eq("OUT-1"), eq(user));
        verify(jdbc).update(contains("SET location_id = ?"), eq(dest), eq(lineId));
        verify(jdbc).update(contains("SET status = 'SHORT_CLOSED'"), eq("MOVE_LINE"), eq("look elsewhere"), eq(user), eq(taskId));
        verify(cycleCounts).raiseForLocation(eq(t()), eq(from), contains("OUT-1"), eq(user));
    }

    @Test @DisplayName("only a short pick of an order still being picked can be resolved, with a reason and a known action")
    void resolveRefusals() {
        shortTask("DONE", null, "10", "10");
        assertEquals("TASK_NOT_SHORT", assertThrows(HandyFlowException.class, () -> service.resolveShort(t(), taskId, "SHIP_SHORT", "because", user)).getErrorCode());
        shortTask("SHORT", null, "10", "6");
        when(jdbc.queryForObject(contains("FROM whse_outbound_orders"), eq(String.class), any(Object[].class))).thenReturn("PACKED");
        assertEquals("ORDER_NOT_PICKING", assertThrows(HandyFlowException.class, () -> service.resolveShort(t(), taskId, "SHIP_SHORT", "because", user)).getErrorCode());
        assertThrows(IllegalArgumentException.class, () -> service.resolveShort(t(), taskId, "SHIP_SHORT", " ", user));
        assertThrows(IllegalArgumentException.class, () -> service.resolveShort(t(), taskId, "CANCEL", "because", user));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.resolveShort(t(), taskId, "SHIP_SHORT", "because", null));
        verifyNoInteractions(inventory);
    }
}
