package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseLotServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();
    private static final LocalDate EXPIRY = LocalDate.of(2027, 3, 31);

    private JdbcTemplate jdbc;
    private WhseLotService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new WhseLotService(jdbc);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void tracking(boolean lot, boolean expiry) {
        when(jdbc.query(contains("lot_tracked, expiry_tracked, min_shelf_life_days FROM whse_items WHERE id = ? AND tenant_id"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(i -> List.of(new WhseLotService.Tracking(lot, expiry, null)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void existingLot(UUID id, LocalDate expiry) { existingLot(id, expiry, false); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void existingLot(UUID id, LocalDate expiry, boolean blocked) {
        when(jdbc.query(contains("FROM whse_lots WHERE tenant_id"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(expiry == null && id == null ? List.of() : List.<Object[]>of(new Object[]{id, expiry, null, blocked}));
    }

    @Test @DisplayName("an item that is not lot tracked resolves to no lot and creates nothing")
    void notTracked() {
        tracking(false, false);
        assertNull(service.resolveForReceipt(t(), client, item, null, null, null, actor));
        verify(jdbc, never()).update(contains("INSERT INTO whse_lots"), any(Object[].class));
        assertThrows(IllegalArgumentException.class, () -> service.resolveForReceipt(t(), client, item, "L1", null, null, actor));
    }

    @Test @DisplayName("a new lot is created with its expiry date")
    void createsLot() {
        tracking(true, true);
        existingLot(null, null);
        UUID id = service.resolveForReceipt(t(), client, item, " L7 ", EXPIRY, null, actor);
        assertNotNull(id);
        verify(jdbc).update(contains("INSERT INTO whse_lots"), eq(id), eq(tenant), eq(client), eq(item), eq("L7"), eq(EXPIRY), isNull(), eq(actor));
    }

    @Test @DisplayName("receiving more of an existing lot reuses it")
    void reusesLot() {
        tracking(true, true);
        UUID lot = UUID.randomUUID();
        existingLot(lot, EXPIRY);
        assertEquals(lot, service.resolveForReceipt(t(), client, item, "L7", EXPIRY, null, actor));
        verify(jdbc, never()).update(contains("INSERT INTO whse_lots"), any(Object[].class));
    }

    @Test @DisplayName("the same lot number with a different expiry date is refused")
    void expiryMismatch() {
        tracking(true, true);
        existingLot(UUID.randomUUID(), EXPIRY);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.resolveForReceipt(t(), client, item, "L7", EXPIRY.plusDays(1), null, actor));
        assertEquals("LOT_EXPIRY_MISMATCH", e.getErrorCode());
    }

    @Test @DisplayName("a return must name a lot that exists")
    void returnNeedsExistingLot() {
        tracking(true, true);
        existingLot(null, null);
        assertThrows(IllegalArgumentException.class, () -> service.requireExisting(t(), client, item, "NOPE"));
        assertThrows(IllegalArgumentException.class, () -> service.requireExisting(t(), client, item, null));
    }

    @Test @DisplayName("tracking cannot be switched while stock is on hand, but a shelf-life change alone can")
    void trackingNeedsEmptyStock() {
        when(jdbc.queryForObject(contains("FROM whse_items WHERE id = ? AND tenant_id"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("FROM whse_inventory WHERE item_id"), eq(Long.class), any(Object[].class))).thenReturn(4L);
        when(jdbc.query(contains("SELECT lot_tracked, expiry_tracked, min_shelf_life_days FROM whse_items WHERE id = ?"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(i -> List.of(new WhseLotService.Tracking(false, false, null)));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.setTracking(t(), item, true, true, 30, actor));
        assertEquals("ITEM_HAS_STOCK", e.getErrorCode());

        when(jdbc.query(contains("SELECT lot_tracked, expiry_tracked, min_shelf_life_days FROM whse_items WHERE id = ?"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(i -> List.of(new WhseLotService.Tracking(true, true, 30)));
        assertDoesNotThrow(() -> service.setTracking(t(), item, true, true, 60, actor));
    }

    @Test @DisplayName("tracking rules: expiry needs lot, shelf life needs expiry")
    void trackingValidation() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.setTracking(t(), item, false, true, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.setTracking(t(), item, true, false, 10, actor));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.setTracking(t(), item, true, true, null, null));
    }

    @Test @DisplayName("stock cannot be received into a blocked lot")
    void blockedLotRefusesReceipt() {
        tracking(true, true);
        existingLot(UUID.randomUUID(), EXPIRY, true);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.resolveForReceipt(t(), client, item, "L7", EXPIRY, null, actor));
        assertEquals("LOT_BLOCKED", e.getErrorCode());
    }

    @Test @DisplayName("blocking needs a reason and a known user")
    void blockNeedsReason() {
        assertThrows(IllegalArgumentException.class, () -> service.block(t(), UUID.randomUUID(), "  ", actor));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.block(t(), UUID.randomUUID(), "supplier recall", null));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test @DisplayName("blocking an unknown lot is a not-found and writes no audit")
    void blockUnknownLot() {
        when(jdbc.update(contains("SET blocked = TRUE"), any(Object[].class))).thenReturn(0);
        assertThrows(za.co.handyflow.platform.shared.ResourceNotFoundException.class, () -> service.block(t(), UUID.randomUUID(), "supplier recall", actor));
        verify(jdbc, never()).update(contains("audit_events"), any(Object[].class));
    }

    @Test @DisplayName("blocking a lot audits it and returns the orders holding its stock")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void blockAudits() {
        UUID lot = UUID.randomUUID();
        when(jdbc.update(contains("SET blocked = TRUE"), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("whse_order_lot_allocations"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new WhseLotService.AffectedOrder(UUID.randomUUID(), "OUT-1", new java.math.BigDecimal("4"))));
        List<WhseLotService.AffectedOrder> affected = service.block(t(), lot, "supplier recall", actor);
        assertEquals(1, affected.size());
        verify(jdbc).update(contains("audit_events"), any(Object[].class));
    }

    @Test @DisplayName("releasing a lot that is not blocked is a conflict")
    void unblockNotBlocked() {
        when(jdbc.update(contains("SET blocked = FALSE"), any(Object[].class))).thenReturn(0);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.unblock(t(), UUID.randomUUID(), "cleared by QA", actor));
        assertEquals("LOT_NOT_BLOCKED", e.getErrorCode());
    }
}
