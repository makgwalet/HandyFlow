package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Orders and shipments belong to a warehouse; the dashboard counts and the reservation list can be cut per warehouse. */
@SuppressWarnings({"unchecked", "rawtypes"})
class WhseDocumentWarehouseTest {

    private JdbcTemplate jdbc;
    private final UUID tenant = UUID.randomUUID(), wh = UUID.randomUUID(), doc = UUID.randomUUID();

    @BeforeEach
    void setUp() { jdbc = mock(JdbcTemplate.class); }

    private TenantId t() { return TenantId.of(tenant); }

    @Test @DisplayName("moves an order to another active warehouse of the same tenant")
    void assignsOrder() {
        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        new WhseDocumentWarehouse(jdbc).assign(t(), WhseDocumentWarehouse.Kind.OUTBOUND, doc, wh);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().startsWith("UPDATE whse_outbound_orders SET warehouse_id"));
        assertTrue(sql.getValue().contains("tenant_id = ?"));
    }

    @Test @DisplayName("moves a shipment in the shipments table")
    void assignsShipment() {
        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        new WhseDocumentWarehouse(jdbc).assign(t(), WhseDocumentWarehouse.Kind.INBOUND, doc, wh);
        verify(jdbc).update(startsWith("UPDATE whse_inbound_shipments SET"), any(Object[].class));
    }

    @Test @DisplayName("no warehouse chosen means nothing is touched")
    void nullIsNoop() {
        new WhseDocumentWarehouse(jdbc).assign(t(), WhseDocumentWarehouse.Kind.OUTBOUND, doc, null);
        verifyNoInteractions(jdbc);
    }

    @Test @DisplayName("a warehouse that is not the tenant's, or not active, is refused before anything changes")
    void refusesUnknownWarehouse() {
        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WhseDocumentWarehouse(jdbc).assign(t(), WhseDocumentWarehouse.Kind.OUTBOUND, doc, wh));
        assertTrue(e.getMessage().contains("warehouse"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test @DisplayName("a document that does not exist is reported")
    void refusesUnknownDocument() {
        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> new WhseDocumentWarehouse(jdbc).assign(t(), WhseDocumentWarehouse.Kind.INBOUND, doc, wh));
    }

    @Test @DisplayName("the order and shipment counts on the dashboard carry the warehouse clause and its argument")
    void dashboardCountsScoped() {
        WhseOperationsService ops = new WhseOperationsService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new WhseOperationsService.TodayActivity(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
        ops.summary(t(), wh);
        ArgumentCaptor<String> counts = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForObject(counts.capture(), eq(Long.class), any(Object[].class));
        long docQueries = counts.getAllValues().stream().filter(q -> q.contains("whse_inbound_shipments") || q.contains("whse_outbound_orders")).count();
        assertEquals(4, docQueries);
        counts.getAllValues().stream().filter(q -> q.contains("whse_inbound_shipments") || q.contains("whse_outbound_orders"))
                .forEach(q -> assertTrue(q.contains("warehouse_id = ?::uuid"), q));
        ArgumentCaptor<String> statuses = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).query(statuses.capture(), any(RowCallbackHandler.class), any(Object[].class));
        statuses.getAllValues().stream().filter(q -> q.contains("whse_inbound_shipments") || q.contains("whse_outbound_orders"))
                .forEach(q -> assertTrue(q.contains("warehouse_id = ?::uuid"), q));
    }

    @Test @DisplayName("without a warehouse the same counts still run, with a null for the clause")
    void dashboardCountsUnscoped() {
        WhseOperationsService ops = new WhseOperationsService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(new WhseOperationsService.TodayActivity(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0));
        assertDoesNotThrow(() -> ops.summary(t()));
    }

    @Test @DisplayName("the reservation list passes the warehouse and its SQL limits reservations to the warehouse's locations")
    void reservationsScoped() {
        WhseOperationsService ops = new WhseOperationsService(jdbc);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        ops.reservations(t(), null, wh);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(eq(WhseOperationsService.SQL_RESERVATIONS), any(RowMapper.class), args.capture());
        assertEquals(2, java.util.Arrays.asList(args.getValue()).stream().filter(wh::equals).count());
        assertTrue(WhseOperationsService.SQL_RESERVATIONS.contains("e.location_id IN (SELECT id FROM whse_locations WHERE warehouse_id = ?::uuid)"));
    }
}
