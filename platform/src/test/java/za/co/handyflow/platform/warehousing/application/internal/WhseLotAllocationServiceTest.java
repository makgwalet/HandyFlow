package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseLotAllocationServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID line = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseInventoryService inventory;
    private WhseLotAllocationService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventory = mock(WhseInventoryService.class);
        service = new WhseLotAllocationService(jdbc, inventory);
        ReflectionTestUtils.setField(service, "zoneId", "Africa/Johannesburg");
    }

    private TenantId t() { return TenantId.of(tenant); }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private WhseFefo.Candidate cand(String lot, LocalDate expiry, boolean blocked, String qty) {
        return new WhseFefo.Candidate(UUID.randomUUID(), UUID.randomUUID(), lot, UUID.randomUUID(), expiry, blocked, new BigDecimal(qty));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void candidates(WhseFefo.Candidate... cs) {
        when(jdbc.queryForObject(contains("min_shelf_life_days"), eq(Integer.class), any(Object[].class))).thenReturn(null);
        when(jdbc.query(contains("FROM whse_inventory i JOIN whse_lots l"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(cs));
    }

    @Test @DisplayName("allocates the earliest lot first and records each piece")
    void allocatesFefo() {
        WhseFefo.Candidate late = cand("LATE", today().plusDays(200), false, "10");
        WhseFefo.Candidate soon = cand("SOON", today().plusDays(20), false, "3");
        candidates(late, soon);
        UUID loc = service.allocate(t(), order, "OUT-1", line, client, item, new BigDecimal("5"), user);
        assertEquals(soon.locationId(), loc);
        verify(inventory).allocateById(eq(t()), eq(soon.inventoryId()), eq(new BigDecimal("3")), eq("OUTBOUND_ORDER"), eq(order), eq("OUT-1"), eq(user));
        verify(inventory).allocateById(eq(t()), eq(late.inventoryId()), eq(new BigDecimal("2")), eq("OUTBOUND_ORDER"), eq(order), eq("OUT-1"), eq(user));
        verify(jdbc, times(2)).update(contains("INSERT INTO whse_order_lot_allocations"), any(Object[].class));
    }

    @Test @DisplayName("a shortfall allocates nothing and names the item")
    void shortfall() {
        candidates(cand("BLK", today().plusDays(20), true, "50"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.allocate(t(), order, "OUT-1", line, client, item, new BigDecimal("5"), user));
        assertTrue(e.getMessage().contains(item.toString()));
        assertTrue(e.getMessage().contains("blocked"));
        verifyNoInteractions(inventory);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void openAllocations(boolean blocked, LocalDate expiry) {
        when(jdbc.query(contains("a.status = 'ALLOCATED' ORDER BY a.created_at"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4"), "L9", blocked, expiry}));
    }

    @Test @DisplayName("shipping posts the lot's stock and closes the allocation")
    void fulfils() {
        openAllocations(false, today().plusDays(10));
        service.fulfil(t(), order, "OUT-1", user);
        verify(inventory).fulfillById(eq(t()), any(UUID.class), eq(new BigDecimal("4")), eq("OUTBOUND_ORDER"), eq(order), eq("OUT-1"), eq(user));
        verify(jdbc).update(contains("SET status = 'SHIPPED'"), any(Object[].class));
    }

    @Test @DisplayName("a blocked lot cannot ship, and nothing is posted")
    void refusesBlocked() {
        openAllocations(true, today().plusDays(10));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.fulfil(t(), order, "OUT-1", user));
        assertTrue(e.getMessage().contains("blocked"));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a lot that expired since allocation cannot ship")
    void refusesExpired() {
        openAllocations(false, today().minusDays(1));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.fulfil(t(), order, "OUT-1", user));
        assertTrue(e.getMessage().contains("expired"));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("releasing gives the stock back and closes each allocation")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void releases() {
        when(jdbc.query(contains("status = 'ALLOCATED' ORDER BY created_at"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4")},
                                              new Object[]{UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1")}));
        service.release(t(), order, "OUT-1", user);
        verify(inventory, times(2)).deallocateById(eq(t()), any(UUID.class), any(BigDecimal.class), eq("OUTBOUND_ORDER"), eq(order), eq("OUT-1"), eq(user));
        verify(jdbc, times(2)).update(contains("SET status = 'RELEASED'"), any(Object[].class));
    }
}
