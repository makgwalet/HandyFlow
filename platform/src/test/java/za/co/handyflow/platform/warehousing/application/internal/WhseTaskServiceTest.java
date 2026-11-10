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
    private WhseTaskService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventory = mock(WhseInventoryService.class);
        service = new WhseTaskService(jdbc, inventory);
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
                        bd("4"), bd("4"), 1, null, "OUT-1", user, null)));
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
                        bd("4"), bd("0"), 1, null, "OUT-1", other, null)));
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
}
