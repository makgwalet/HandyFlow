package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageRules.Limit;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class WhsePackageServiceTest {

    private JdbcTemplate jdbc;
    private WhsePackageService service;
    private final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhsePackageService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    /** The tenant has Starter-like limits (1 warehouse) with the given enforcement. */
    private void assigned(String enforcement) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("code")).thenReturn("STARTER");
        when(rs.getString("name")).thenReturn("Starter");
        when(rs.getBigDecimal("monthly_price")).thenReturn(new BigDecimal("699.00"));
        when(rs.getObject("max_warehouses")).thenReturn(1);
        when(rs.getObject("max_client_accounts")).thenReturn(3);
        when(rs.getObject("max_monthly_transactions")).thenReturn(500);
        when(rs.getObject("max_webhooks")).thenReturn(0);
        when(rs.getBoolean("proposed")).thenReturn(true);
        when(rs.getString("enforcement")).thenReturn(enforcement);
        when(jdbc.query(contains("FROM whse_tenant_subscription"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> List.of(((RowMapper) inv.getArgument(1)).mapRow(rs, 0)));
    }

    private void used(long warehouses) {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("FROM whse_warehouses"), eq(Long.class), any(Object[].class))).thenReturn(warehouses);
    }

    @Test @DisplayName("a tenant with no package is never limited")
    void noPackage() {
        when(jdbc.query(contains("FROM whse_tenant_subscription"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertDoesNotThrow(() -> service.requireRoomFor(t(), Limit.WAREHOUSES));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test @DisplayName("MONITOR shows the overage but does not refuse")
    void reportOnly() throws Exception {
        assigned("MONITOR"); used(2);
        assertDoesNotThrow(() -> service.requireRoomFor(t(), Limit.WAREHOUSES));
        WhsePackageService.Usage u = service.usage(t());
        assertTrue(u.assigned());
        assertTrue(u.meters().stream().anyMatch(m -> m.key().equals("WAREHOUSES") && m.over()));
    }

    @Test @DisplayName("ENFORCE refuses a second warehouse on a one-warehouse package")
    void enforce() throws Exception {
        assigned("ENFORCE"); used(1);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.requireRoomFor(t(), Limit.WAREHOUSES));
        assertTrue(e.getMessage().contains("Starter"));
    }

    @Test @DisplayName("ENFORCE allows the first warehouse")
    void enforceRoom() throws Exception {
        assigned("ENFORCE"); used(0);
        assertDoesNotThrow(() -> service.requireRoomFor(t(), Limit.WAREHOUSES));
    }

    @Test @DisplayName("OFF records the package but shows no overage")
    void off() throws Exception {
        assigned("OFF"); used(5);
        assertTrue(service.usage(t()).meters().stream().noneMatch(WhsePackageService.Meter::over));
    }

    @Test @DisplayName("assigning needs an identified user and a known package")
    void assign() {
        assertThrows(AccessDeniedException.class, () -> service.assign(t(), "SME", "OFF", null));
        when(jdbc.queryForObject(contains("FROM whse_subscription_tiers WHERE code"), eq(Integer.class), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.assign(t(), "GOLD", "OFF", actor));
        verify(jdbc, never()).update(contains("INSERT INTO whse_tenant_subscription"), any(Object[].class));
    }

    @Test @DisplayName("clearing the package deletes the row")
    void clear() {
        when(jdbc.query(contains("FROM whse_tenant_subscription"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        service.assign(t(), " ", null, actor);
        verify(jdbc).update(contains("DELETE FROM whse_tenant_subscription"), any(Object.class));
    }

    @Test @DisplayName("a created receipt or order is written to the usage ledger once, whatever the tier")
    void recordsTransaction() {
        UUID source = UUID.randomUUID();
        service.recordTransaction(t(), "RECEIPT", source);
        verify(jdbc).update(contains("INSERT INTO whse_usage_events"), any(Object[].class));
        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class)); // recording does not look at limits
    }

    @Test @DisplayName("the monthly count comes from the ledger within the billing month, and the billing anchor decides the month")
    void countsFromLedger() throws Exception {
        assigned("ENFORCE");
        when(jdbc.query(contains("FROM tenant_modules"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(15));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("FROM whse_usage_events"), eq(Long.class), any(Object[].class))).thenReturn(500L);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.requireRoomFor(t(), Limit.MONTHLY_TRANSACTIONS));
        assertTrue(e.getMessage().contains("transactions this billing month"));
        WhsePackageService.Usage u = service.usage(t());
        assertEquals(15, u.periodStart().getDayOfMonth());
        assertEquals(14, u.periodEnd().getDayOfMonth());
    }

    @Test @DisplayName("an expired or not-yet-started tier applies no limits")
    void windowOutside() throws Exception {
        assigned("ENFORCE"); used(5);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("code")).thenReturn("STARTER");
        when(rs.getString("name")).thenReturn("Starter");
        when(rs.getObject("max_warehouses")).thenReturn(1);
        when(rs.getString("enforcement")).thenReturn("ENFORCE");
        when(rs.getDate("expires_on")).thenReturn(java.sql.Date.valueOf(java.time.LocalDate.now().minusDays(400)));
        when(jdbc.query(contains("FROM whse_tenant_subscription"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> List.of(((RowMapper) inv.getArgument(1)).mapRow(rs, 0)));
        assertDoesNotThrow(() -> service.requireRoomFor(t(), Limit.WAREHOUSES));
        WhsePackageService.Usage u = service.usage(t());
        assertEquals("EXPIRED", u.state());
        assertTrue(u.meters().stream().allMatch(m -> m.limit() == null && !m.over()));
    }

    @Test @DisplayName("an override in force raises the limit it names")
    void overrideRaisesLimit() throws Exception {
        assigned("ENFORCE"); used(1);
        ResultSet r = mock(ResultSet.class);
        when(r.getString(1)).thenReturn("WAREHOUSES");
        when(r.getLong(2)).thenReturn(2L);
        // query(String, RowCallbackHandler, Object...) returns void, so it is stubbed with doAnswer
        doAnswer(inv -> {
            ((org.springframework.jdbc.core.RowCallbackHandler) inv.getArgument(1)).processRow(r);
            return null;
        }).when(jdbc).query(contains("FROM whse_tier_overrides"), any(org.springframework.jdbc.core.RowCallbackHandler.class), any(Object[].class));
        assertDoesNotThrow(() -> service.requireRoomFor(t(), Limit.WAREHOUSES)); // limit 1 + 2 = 3, one in use
        WhsePackageService.Meter m = service.usage(t()).meters().stream().filter(x -> x.key().equals("WAREHOUSES")).findFirst().orElseThrow();
        assertEquals(3, m.limit());
        assertEquals(2, m.extra());
    }

    @Test @DisplayName("an override needs an identified administrator, a known limit and a valid reason")
    void overrideRefusals() {
        java.time.LocalDate soon = java.time.LocalDate.now().plusDays(10);
        assertThrows(AccessDeniedException.class, () -> service.addOverride(t(), "WAREHOUSES", 1, soon, "Agreed with the customer", null));
        assertThrows(IllegalArgumentException.class, () -> service.addOverride(t(), "USERS", 1, soon, "Agreed with the customer", actor));
        assertThrows(IllegalArgumentException.class, () -> service.addOverride(t(), "WAREHOUSES", 1, soon, "short", actor));
        verify(jdbc, never()).update(contains("INSERT INTO whse_tier_overrides"), any(Object[].class));
        service.addOverride(t(), "warehouses", 1, soon, "Agreed with the customer", actor);
        verify(jdbc).update(contains("INSERT INTO whse_tier_overrides"), any(Object[].class));
        verify(jdbc).update(contains("WHSE_TIER_OVERRIDE_ADDED"), any(Object[].class));
    }

    @Test @DisplayName("revoking an override that is not there is refused")
    void revoke() {
        UUID id = UUID.randomUUID();
        when(jdbc.update(contains("UPDATE whse_tier_overrides"), any(Object[].class))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> service.revokeOverride(t(), id, actor));
        when(jdbc.update(contains("UPDATE whse_tier_overrides"), any(Object[].class))).thenReturn(1);
        service.revokeOverride(t(), id, actor);
        verify(jdbc).update(contains("WHSE_TIER_OVERRIDE_REVOKED"), any(Object[].class));
    }

    @Test @DisplayName("assigning with dates stores them, and an expiry before the start is refused")
    void assignWithDates() {
        when(jdbc.queryForObject(contains("FROM whse_subscription_tiers WHERE code"), eq(Integer.class), any(Object[].class))).thenReturn(1);
        java.time.LocalDate from = java.time.LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> service.assign(t(), "SME", "OFF", from, from.minusDays(1), null, actor));
        service.assign(t(), "SME", "OFF", from, from.plusMonths(12), "Contract 14", actor);
        verify(jdbc).update(contains("expires_on = EXCLUDED.expires_on"), any(Object[].class));
    }
}
