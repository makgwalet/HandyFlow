package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseSlottingServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseSlottingService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseSlottingService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void sourceStock(Object[]... positions) {
        when(jdbc.query(contains("FROM whse_inventory WHERE tenant_id = ? AND client_id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(positions));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void destination(String type, String capacity, boolean active) {
        when(jdbc.query(contains("SELECT location_type, capacity_units, active"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(type == null ? List.of() : List.<Object[]>of(new Object[]{type, capacity == null ? null : new BigDecimal(capacity), active}));
    }

    private void busy(int n) { when(jdbc.queryForObject(contains("count(*) FROM whse_inventory WHERE location_id"), eq(Integer.class), any(Object[].class))).thenReturn(n); }

    @Test @DisplayName("a move task is created for all the available stock, and audited")
    void creates() {
        sourceStock(new Object[]{null, new BigDecimal("12")});
        destination("BIN", "20", true);
        busy(0);
        UUID id = service.createMove(t(), client, item, from, to, actor);
        verify(jdbc).update(contains("INSERT INTO whse_tasks"), eq(id), eq(tenant), eq(client), eq(item), isNull(), eq(from), eq(to), eq(new BigDecimal("12")), eq(from));
        verify(jdbc).update(contains("INSERT INTO audit_events"), eq(tenant), eq(actor), anyString());
    }

    @Test @DisplayName("nothing to move, or stock in several lots, is refused")
    void sourceProblems() {
        sourceStock();
        assertThrows(IllegalStateException.class, () -> service.createMove(t(), client, item, from, to, actor));
        sourceStock(new Object[]{UUID.randomUUID(), BigDecimal.ONE}, new Object[]{UUID.randomUUID(), BigDecimal.ONE});
        assertTrue(assertThrows(IllegalStateException.class, () -> service.createMove(t(), client, item, from, to, actor)).getMessage().contains("several positions"));
    }

    @Test @DisplayName("the destination must exist, be active storage, have room and still be empty")
    void destinationProblems() {
        sourceStock(new Object[]{null, new BigDecimal("12")});
        destination(null, null, true);
        assertThrows(ResourceNotFoundException.class, () -> service.createMove(t(), client, item, from, to, actor));
        destination("DOCK", null, true);
        assertThrows(IllegalArgumentException.class, () -> service.createMove(t(), client, item, from, to, actor));
        destination("BIN", null, false);
        assertThrows(IllegalArgumentException.class, () -> service.createMove(t(), client, item, from, to, actor));
        destination("BIN", "5", true);
        assertThrows(IllegalArgumentException.class, () -> service.createMove(t(), client, item, from, to, actor));
        destination("BIN", "50", true);
        busy(1);
        assertTrue(assertThrows(IllegalStateException.class, () -> service.createMove(t(), client, item, from, to, actor)).getMessage().contains("no longer empty"));
        verify(jdbc, never()).update(contains("INSERT INTO whse_tasks"), any(Object[].class));
    }

    @Test @DisplayName("moving to where it already is, or without a user, is refused")
    void basics() {
        assertThrows(IllegalArgumentException.class, () -> service.createMove(t(), client, item, from, from, actor));
        assertThrows(AccessDeniedException.class, () -> service.createMove(t(), client, item, from, to, null));
    }

    @Test @DisplayName("a move already waiting is reported, not duplicated")
    void duplicate() {
        sourceStock(new Object[]{null, new BigDecimal("12")});
        destination("BIN", null, true);
        busy(0);
        when(jdbc.update(contains("INSERT INTO whse_tasks"), any(Object[].class))).thenThrow(new DuplicateKeyException("dup"));
        assertTrue(assertThrows(IllegalStateException.class, () -> service.createMove(t(), client, item, from, to, actor)).getMessage().contains("already waiting"));
    }

    @Test @DisplayName("the period and the minimum gain are checked")
    void parameters() {
        assertThrows(IllegalArgumentException.class, () -> service.overview(t(), 3, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> service.overview(t(), 800, 5, 10));
        assertThrows(IllegalArgumentException.class, () -> service.overview(t(), 90, 0, 10));
    }

    private ResultSet rs(Object... vals) throws Exception {
        ResultSet r = mock(ResultSet.class);
        when(r.getObject(1, UUID.class)).thenReturn((UUID) vals[0]);
        if (vals.length > 1 && vals[1] instanceof BigDecimal b) when(r.getBigDecimal(2)).thenReturn(b);
        if (vals.length > 1 && vals[1] instanceof String s) { when(r.getString(2)).thenReturn(s); when(r.getString(3)).thenReturn((String) vals[2]); when(r.getString(4)).thenReturn((String) vals[3]); }
        return r;
    }

    @Test @DisplayName("the overview ranks by picks, offers a nearer empty location, and counts the classes")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void overview() throws Exception {
        UUID slow = UUID.randomUUID(), emptyLoc = UUID.randomUUID(), slowLoc = UUID.randomUUID();
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(rs(item, new BigDecimal("900")));
            h.processRow(rs(slow, new BigDecimal("1")));
            return null;
        }).when(jdbc).query(contains("FROM whse_tasks WHERE tenant_id = ? AND task_type = 'PICK'"), any(RowCallbackHandler.class), any(Object[].class));
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            h.processRow(rs(item, "FAST-1", "Fast thing", "Acme"));
            h.processRow(rs(slow, "SLOW-1", "Slow thing", "Acme"));
            return null;
        }).when(jdbc).query(contains("FROM whse_items i JOIN whse_clients c"), any(RowCallbackHandler.class), any(Object[].class));
        when(jdbc.query(contains("FROM whse_inventory i JOIN whse_locations loc"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                new WhseSlottingRules.Placed(client, item, null, from, "Z-99", 90, new BigDecimal("30"), BigDecimal.ZERO, "C"),
                new WhseSlottingRules.Placed(client, slow, null, slowLoc, "Z-98", 80, new BigDecimal("5"), BigDecimal.ZERO, "C")));
        when(jdbc.query(contains("NOT EXISTS (SELECT 1 FROM whse_inventory v"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhseSlottingRules.Empty(emptyLoc, "A-01", 1, null)));
        WhseSlottingService.Overview o = service.overview(t(), 90, 5, 50);
        assertEquals(2, o.itemsWithStock());
        assertEquals(1, o.classA());
        assertEquals(1, o.classC());
        assertEquals(1, o.suggestions().size());
        WhseSlottingService.Suggestion s = o.suggestions().get(0);
        assertEquals("FAST-1", s.sku());
        assertEquals("A", s.abc());
        assertEquals(emptyLoc, s.toLocationId());
        assertEquals(from, s.fromLocationId());
        assertEquals(89, s.improvement());
        assertEquals(0, new BigDecimal("30").compareTo(s.qty()));
    }
}
