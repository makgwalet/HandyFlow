package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class WhseWarehouseServiceTest {

    private JdbcTemplate jdbc;
    private WhseWarehouseService service;
    private final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), wh = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseWarehouseService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    private WhseWarehouseService.Warehouse w(boolean isDefault, String stock) {
        return new WhseWarehouseService.Warehouse(wh, "CPT", "Cape Town", null, true, isDefault, 4, new BigDecimal(stock), Instant.now());
    }

    private void existing(WhseWarehouseService.Warehouse w) {
        when(jdbc.query(contains("AND w.id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(w));
    }

    @Test @DisplayName("the first warehouse a tenant makes is its default; the code is stored upper case")
    void firstIsDefault() {
        when(jdbc.queryForObject(contains("WHERE tenant_id = ? AND code = ?"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("deleted_at IS NULL"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        existing(w(true, "0"));
        service.create(t(), "cpt", "Cape Town", null, actor);
        verify(jdbc).update(contains("INSERT INTO whse_warehouses"), any(), eq(tenant), eq("CPT"), eq("Cape Town"), isNull(), eq(true));
    }

    @Test @DisplayName("a later warehouse is not the default")
    void laterIsNotDefault() {
        when(jdbc.queryForObject(contains("WHERE tenant_id = ? AND code = ?"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("deleted_at IS NULL"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        existing(w(false, "0"));
        service.create(t(), "JHB", "Johannesburg", " Gauteng ", actor);
        verify(jdbc).update(contains("INSERT INTO whse_warehouses"), any(), eq(tenant), eq("JHB"), eq("Johannesburg"), eq("Gauteng"), eq(false));
    }

    @Test @DisplayName("a code already used is refused and nothing is written")
    void duplicateCode() {
        when(jdbc.queryForObject(contains("WHERE tenant_id = ? AND code = ?"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.create(t(), "CPT", "Again", null, actor));
        verify(jdbc, never()).update(contains("INSERT INTO whse_warehouses"), any(Object[].class));
    }

    @Test @DisplayName("every change needs an identified user")
    void needsActor() {
        assertThrows(AccessDeniedException.class, () -> service.create(t(), "CPT", "Cape Town", null, null));
        assertThrows(AccessDeniedException.class, () -> service.makeDefault(t(), wh, null));
        assertThrows(AccessDeniedException.class, () -> service.setActive(t(), wh, false, null));
    }

    @Test @DisplayName("the default warehouse and one with stock cannot be switched off")
    void deactivateRules() {
        existing(w(true, "0"));
        assertThrows(IllegalStateException.class, () -> service.setActive(t(), wh, false, actor));
        existing(w(false, "12"));
        assertThrows(IllegalStateException.class, () -> service.setActive(t(), wh, false, actor));
        verify(jdbc, never()).update(contains("SET active"), any(Object[].class));
        existing(w(false, "0"));
        service.setActive(t(), wh, false, actor);
        verify(jdbc).update(contains("SET active = ?"), eq(false), eq(tenant), eq(wh));
    }

    @Test @DisplayName("making another warehouse the default clears the old default first")
    void makeDefault() {
        existing(w(false, "0"));
        service.makeDefault(t(), wh, actor);
        org.mockito.InOrder o = inOrder(jdbc);
        o.verify(jdbc).update(contains("SET is_default = FALSE"), eq(tenant), eq(wh));
        o.verify(jdbc).update(contains("SET is_default = TRUE"), eq(tenant), eq(wh));
    }

    @Test @DisplayName("resolve: the one asked for, else the default, else nothing")
    void resolve() {
        when(jdbc.query(contains("SELECT active FROM whse_warehouses"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(true));
        assertEquals(wh, service.resolve(t(), wh));
        when(jdbc.query(contains("SELECT active FROM whse_warehouses"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(false));
        assertThrows(IllegalStateException.class, () -> service.resolve(t(), wh));
        when(jdbc.query(contains("SELECT active FROM whse_warehouses"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.resolve(t(), wh));
        UUID d = UUID.randomUUID();
        when(jdbc.query(contains("AND is_default"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(d));
        assertEquals(d, service.resolve(t(), null));
        when(jdbc.query(contains("AND is_default"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertNull(service.resolve(t(), null));
    }

    @Test @DisplayName("a warehouse of another tenant is not found")
    void otherTenant() {
        when(jdbc.query(contains("AND w.id = ?"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.get(t(), wh));
    }
}
