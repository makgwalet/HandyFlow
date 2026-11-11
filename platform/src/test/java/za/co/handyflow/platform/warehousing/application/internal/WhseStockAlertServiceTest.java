package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Figures;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertRules.Kind;
import za.co.handyflow.platform.warehousing.application.internal.WhseStockAlertService.Condition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Alerts are kept in step with the stock: raised once, replaced when the kind changes, closed when the stock recovers, and not re-raised after a person resolved them. */
@SuppressWarnings({"unchecked", "rawtypes"})
class WhseStockAlertServiceTest {

    private JdbcTemplate jdbc;
    private WhseStockAlertService service;
    private final UUID tenant = UUID.randomUUID(), wh = UUID.randomUUID(), item = UUID.randomUUID(), client = UUID.randomUUID(), actor = UUID.randomUUID(), alertId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = spy(new WhseStockAlertService(jdbc));
    }

    private TenantId t() { return TenantId.of(tenant); }

    private Condition cond(Kind kind) {
        Figures f = Figures.of(new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO, BigDecimal.ZERO);
        return new Condition(wh, "JHB", client, "Acme", item, "FILTER", "Diesel Filter", kind, f,
                WhseStockAlertRules.resolve(new BigDecimal("100"), new BigDecimal("500"), null, null), new BigDecimal("480"));
    }

    /** What the saved-alerts query returns: id, warehouse, item, kind, status, suppressed. */
    private void saved(String kind, String status, boolean suppressed) {
        when(jdbc.query(contains("FROM whse_stock_alerts WHERE tenant_id = ?"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> {
                    RowMapper<?> rm = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject(1)).thenReturn(alertId);
                    when(rs.getObject(2)).thenReturn(wh);
                    when(rs.getObject(3)).thenReturn(item);
                    when(rs.getString(4)).thenReturn(kind);
                    when(rs.getString(5)).thenReturn(status);
                    when(rs.getBoolean(6)).thenReturn(suppressed);
                    return List.of(rm.mapRow(rs, 0));
                });
    }

    private void live(Condition... cs) { doReturn(List.of(cs)).when(service).conditions(any(), any(), any()); }

    @Test @DisplayName("a new condition raises one alert")
    void raises() {
        live(cond(Kind.BELOW_MIN));
        when(jdbc.query(contains("FROM whse_stock_alerts WHERE tenant_id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class))).thenReturn(1);
        assertEquals(1, service.sync(t()));
        verify(jdbc).update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class));
    }

    @Test @DisplayName("a condition that already has a live alert of the same kind only refreshes it: no duplicate")
    void noDuplicate() {
        live(cond(Kind.BELOW_MIN));
        saved("BELOW_MIN", "OPEN", false);
        assertEquals(0, service.sync(t()));
        verify(jdbc).update(contains("SET last_seen = now()"), any(Object[].class));
        verify(jdbc, never()).update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class));
    }

    @Test @DisplayName("an acknowledged alert stays acknowledged while the condition lasts")
    void acknowledgedStays() {
        live(cond(Kind.BELOW_MIN));
        saved("BELOW_MIN", "ACKNOWLEDGED", false);
        service.sync(t());
        verify(jdbc, never()).update(contains("status = 'RESOLVED'"), any(Object[].class));
        verify(jdbc, never()).update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class));
    }

    @Test @DisplayName("an alert whose stock has recovered resolves itself as CLEARED")
    void clears() {
        live();
        saved("BELOW_MIN", "OPEN", false);
        service.sync(t());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("status = 'RESOLVED'"), args.capture());
        assertEquals("CLEARED", args.getValue()[0]);
    }

    @Test @DisplayName("an alert whose kind changed is closed as CHANGED and replaced by the new one")
    void kindChanges() {
        live(cond(Kind.OUT_OF_STOCK));
        saved("BELOW_MIN", "OPEN", false);
        when(jdbc.update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class))).thenReturn(1);
        assertEquals(1, service.sync(t()));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("status = 'RESOLVED'"), args.capture());
        assertEquals("CHANGED", args.getValue()[0]);
        verify(jdbc).update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class));
    }

    @Test @DisplayName("an alert a person resolved is not raised again while the same condition goes on")
    void manualStaysResolved() {
        live(cond(Kind.BELOW_MIN));
        saved("BELOW_MIN", "RESOLVED", true);
        assertEquals(0, service.sync(t()));
        verify(jdbc, never()).update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class));
        verify(jdbc, never()).update(contains("suppressed = FALSE"), any(Object[].class));
    }

    @Test @DisplayName("once the condition has cleared the hand-resolved alert stops suppressing, and a return raises a new one")
    void manualReleasedWhenCleared() {
        live();
        saved("BELOW_MIN", "RESOLVED", true);
        service.sync(t());
        verify(jdbc).update(contains("suppressed = FALSE"), any(Object[].class));
        // the stock then falls again
        reset(jdbc);
        live(cond(Kind.BELOW_MIN));
        when(jdbc.query(contains("FROM whse_stock_alerts WHERE tenant_id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO whse_stock_alerts"), any(Object[].class))).thenReturn(1);
        assertEquals(1, service.sync(t()));
    }

    @Test @DisplayName("acknowledging needs an identified user and an open alert, and is audited")
    void acknowledge() {
        assertThrows(AccessDeniedException.class, () -> service.acknowledge(t(), alertId, null));
        when(jdbc.update(contains("status = 'ACKNOWLEDGED'"), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.acknowledge(t(), alertId, actor));
        when(jdbc.update(contains("status = 'ACKNOWLEDGED'"), any(Object[].class))).thenReturn(1);
        service.acknowledge(t(), alertId, actor);
        verify(jdbc).update(contains("INSERT INTO audit_events"), any(Object[].class));
    }

    @Test @DisplayName("resolving needs a note of at least 5 characters, at most 300, and is audited")
    void resolve() {
        assertThrows(AccessDeniedException.class, () -> service.resolve(t(), alertId, "stock on its way", null));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(t(), alertId, "  no ", actor));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(t(), alertId, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(t(), alertId, "x".repeat(301), actor));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        when(jdbc.update(contains("resolution = 'MANUAL'"), any(Object[].class))).thenReturn(1);
        service.resolve(t(), alertId, " stock on its way ", actor);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("resolution = 'MANUAL'"), args.capture());
        assertEquals("stock on its way", args.getValue()[1]);
        verify(jdbc).update(contains("INSERT INTO audit_events"), any(Object[].class));
    }

    @Test @DisplayName("a threshold is refused for a bad level, an unknown warehouse or item, or defaults that would cross, before anything is written")
    void thresholdRefusals() {
        assertThrows(AccessDeniedException.class, () -> service.setThreshold(t(), wh, item, new BigDecimal("30"), new BigDecimal("150"), null));
        assertThrows(IllegalArgumentException.class, () -> service.setThreshold(t(), wh, item, null, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.setThreshold(t(), wh, item, new BigDecimal("150"), new BigDecimal("30"), actor));

        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.setThreshold(t(), wh, item, new BigDecimal("30"), new BigDecimal("150"), actor));

        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("FROM whse_items"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.setThreshold(t(), wh, item, new BigDecimal("30"), new BigDecimal("150"), actor));

        when(jdbc.query(contains("FROM whse_items"), any(RowMapper.class), any(Object[].class))).thenReturn(List.<BigDecimal[]>of(new BigDecimal[]{new BigDecimal("50"), new BigDecimal("250")}));
        assertThrows(IllegalArgumentException.class, () -> service.setThreshold(t(), wh, item, new BigDecimal("300"), null, actor)); // 300 over the item's own maximum of 250
        verify(jdbc, never()).update(contains("whse_stock_thresholds"), any(Object[].class));
    }

    @Test @DisplayName("a valid threshold is saved as an upsert and audited")
    void thresholdSaved() {
        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("FROM whse_items"), any(RowMapper.class), any(Object[].class))).thenReturn(List.<BigDecimal[]>of(new BigDecimal[]{new BigDecimal("50"), new BigDecimal("250")}));
        service.setThreshold(t(), wh, item, new BigDecimal("100"), new BigDecimal("500"), actor);
        verify(jdbc).update(contains("ON CONFLICT (tenant_id, warehouse_id, item_id) DO UPDATE"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO audit_events"), any(Object[].class));
    }

    @Test @DisplayName("clearing a threshold the warehouse does not have is reported")
    void clearUnknown() {
        when(jdbc.update(contains("DELETE FROM whse_stock_thresholds"), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.clearThreshold(t(), wh, item, actor));
        when(jdbc.update(contains("DELETE FROM whse_stock_thresholds"), any(Object[].class))).thenReturn(1);
        service.clearThreshold(t(), wh, item, actor);
        verify(jdbc).update(contains("INSERT INTO audit_events"), any(Object[].class));
    }

    @Test @DisplayName("the conditions query is scoped to the tenant and checks the item defaults only where an item is held when there are several warehouses")
    void conditionsSql() {
        String q = WhseStockAlertService.SQL_CONDITIONS;
        assertTrue(q.contains("whse_stock_thresholds"));
        assertTrue(q.contains("(SELECT n FROM cnt) = 1 OR pos.item_id IS NOT NULL OR t.id IS NOT NULL"));
        assertTrue(q.contains("inv.status = 'AVAILABLE'"));
        assertTrue(q.contains("s.status IN ('EXPECTED', 'PARTIALLY_RECEIVED')"));
        assertEquals(9, q.chars().filter(c -> c == '?').count());
    }
}
