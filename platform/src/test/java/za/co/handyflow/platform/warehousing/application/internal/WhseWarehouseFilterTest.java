package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The warehouse filter narrows the task list and the stock list; with no warehouse the queries are unchanged. */
@SuppressWarnings({"unchecked", "rawtypes"})
class WhseWarehouseFilterTest {

    private JdbcTemplate jdbc;
    private final UUID tenant = UUID.randomUUID(), wh = UUID.randomUUID();

    @BeforeEach
    void setUp() { jdbc = mock(JdbcTemplate.class); }

    private TenantId t() { return TenantId.of(tenant); }

    @Test @DisplayName("the task list is limited to the warehouse's locations when one is chosen")
    void taskListFiltered() {
        WhseTaskService tasks = new WhseTaskService(jdbc, mock(WhseInventoryService.class), mock(WhseLotAllocationService.class), mock(WhseCycleCountService.class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        tasks.list(t(), null, List.of("OPEN"), null, null, 50, wh);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("fl.warehouse_id = ?"));
        assertTrue(java.util.Arrays.asList(args.getValue()).contains(wh));
    }

    @Test @DisplayName("without a warehouse the task list has no warehouse clause")
    void taskListUnfiltered() {
        WhseTaskService tasks = new WhseTaskService(jdbc, mock(WhseInventoryService.class), mock(WhseLotAllocationService.class), mock(WhseCycleCountService.class));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        tasks.list(t(), null, List.of("OPEN"), null, null, 50);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertFalse(sql.getValue().contains("warehouse_id"));
    }

    @Test @DisplayName("the task summary scopes by warehouse only when one is given")
    void taskSummaryScope() {
        WhseTaskService tasks = new WhseTaskService(jdbc, mock(WhseInventoryService.class), mock(WhseLotAllocationService.class), mock(WhseCycleCountService.class));
        tasks.summary(t(), wh);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowCallbackHandler.class), args.capture());
        assertTrue(sql.getValue().contains("warehouse_id = ?"));
        assertEquals(3, args.getValue().length);
    }

    @Test @DisplayName("the stock list passes the warehouse to its query, and the SQL has the warehouse clause")
    void stockPassesWarehouse() {
        WhseOperationsService ops = new WhseOperationsService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        ops.stock(t(), null, null, null, true, 0, 50, wh);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(contains("warehouse_id = ?::uuid"), eq(Long.class), args.capture());
        List<Object> a = java.util.Arrays.asList(args.getValue());
        assertEquals(2, a.stream().filter(wh::equals).count());
    }

    @Test @DisplayName("every dashboard stock query carries the warehouse clause")
    void dashboardQueriesAreScoped() {
        for (String q : List.of(WhseOperationsService.SQL_STOCK_BY_STATUS, WhseOperationsService.SQL_TODAY, WhseOperationsService.SQL_TOP_MOVERS,
                WhseOperationsService.SQL_TREND, WhseOperationsService.SQL_STOCK_VALUE, WhseOperationsService.SQL_STOCK_BASE, WhseOperationsService.SQL_MOVEMENTS_BASE)) {
            assertTrue(q.contains("warehouse_id = ?::uuid"), q);
        }
    }
}
