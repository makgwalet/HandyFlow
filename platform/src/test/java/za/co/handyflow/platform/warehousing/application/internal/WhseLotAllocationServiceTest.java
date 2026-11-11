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

    @Test @DisplayName("the candidate query reads the date each lot first came in, from its first receipt or else when the lot was created")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void candidateQueryHasReceivedDate() {
        candidates(cand("L", today().plusDays(20), false, "10"));
        service.allocate(t(), order, "OUT-1", line, client, item, new BigDecimal("1"), user);
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("min(m.created_at)"));
        assertTrue(sql.getValue().contains("m.movement_type = 'RECEIPT'"));
        assertTrue(sql.getValue().contains("l.created_at"));
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void plainPositions(WhseSplitPlan.Source... ss) {
        when(jdbc.query(contains("i.lot_id IS NULL"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(ss));
    }

    @Test @DisplayName("a line no single location covers is split, one allocation row per piece with no lot")
    void splitsAcrossLocations() {
        WhseSplitPlan.Source big = new WhseSplitPlan.Source(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("6"));
        WhseSplitPlan.Source small = new WhseSplitPlan.Source(UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("3"));
        plainPositions(small, big);
        WhseLotAllocationService.Split r = service.allocateSplit(t(), order, "OUT-1", line, client, item, new BigDecimal("9"), user, false, java.util.Set.of());
        assertEquals(big.locationId(), r.firstLocationId());
        assertEquals(0, r.unallocated().signum());
        verify(inventory).allocateById(eq(t()), eq(big.inventoryId()), eq(new BigDecimal("6")), eq("OUTBOUND_ORDER"), eq(order), eq("OUT-1"), eq(user));
        verify(inventory).allocateById(eq(t()), eq(small.inventoryId()), eq(new BigDecimal("3")), eq("OUTBOUND_ORDER"), eq(order), eq("OUT-1"), eq(user));
        verify(jdbc, times(2)).update(contains("INSERT INTO whse_order_lot_allocations"), any(Object[].class));
    }

    @Test @DisplayName("without partial allowed a shortage allocates nothing")
    void splitShortageFails() {
        plainPositions(new WhseSplitPlan.Source(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("4")));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.allocateSplit(t(), order, "OUT-1", line, client, item, new BigDecimal("9"), user, false, java.util.Set.of()));
        assertTrue(e.getMessage().contains("only 4"));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("with partial allowed the stock there is gets allocated and the rest is reported")
    void splitPartial() {
        plainPositions(new WhseSplitPlan.Source(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("4")));
        WhseLotAllocationService.Split r = service.allocateSplit(t(), order, "OUT-1", line, client, item, new BigDecimal("9"), user, true, java.util.Set.of());
        assertEquals(0, new BigDecimal("4").compareTo(r.allocated()));
        assertEquals(0, new BigDecimal("5").compareTo(r.unallocated()));
    }

    @Test @DisplayName("with partial allowed and no stock at all nothing is allocated")
    void splitNothing() {
        plainPositions();
        WhseLotAllocationService.Split r = service.allocateSplit(t(), order, "OUT-1", line, client, item, new BigDecimal("9"), user, true, java.util.Set.of());
        assertNull(r.firstLocationId());
        assertEquals(0, new BigDecimal("9").compareTo(r.unallocated()));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("excluded positions are not offered again")
    void splitExcludes() {
        WhseSplitPlan.Source used = new WhseSplitPlan.Source(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("9"));
        plainPositions(used);
        assertThrows(IllegalStateException.class,
                () -> service.allocateSplit(t(), order, "OUT-1", line, client, item, new BigDecimal("2"), user, false, java.util.Set.of(used.inventoryId())));
    }

    @Test @DisplayName("lot-tracked partial allocation takes the sellable lots and reports the rest")
    void lotPartial() {
        WhseFefo.Candidate a = cand("A", today().plusDays(20), false, "3");
        candidates(a, cand("BLK", today().plusDays(5), true, "40"));
        WhseLotAllocationService.Split r = service.allocateUpTo(t(), order, "OUT-1", line, client, item, new BigDecimal("8"), user);
        assertEquals(a.locationId(), r.firstLocationId());
        assertEquals(0, new BigDecimal("5").compareTo(r.unallocated()));
    }
}
