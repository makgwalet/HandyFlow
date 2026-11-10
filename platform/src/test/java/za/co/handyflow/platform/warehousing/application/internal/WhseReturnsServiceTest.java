package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseReturnsServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID ret = UUID.randomUUID();
    private final UUID line = UUID.randomUUID();
    private final UUID location = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseInventoryService inventory;
    private TenantSequenceService sequences;
    private WhseLotService lotService;
    private WhseReturnsService service;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventory = mock(WhseInventoryService.class);
        sequences = mock(TenantSequenceService.class);
        lotService = mock(WhseLotService.class);
        service = new WhseReturnsService(jdbc, inventory, sequences, lotService);
        when(sequences.nextValue(any(), anyString())).thenReturn(7L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.query(contains("idempotency_key"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    }

    private TenantId t() { return TenantId.of(tenant); }
    private static BigDecimal n(String v) { return new BigDecimal(v); }

    /** The linked-order cap: shipped quantity and what earlier returns already took. */
    private void shipped(String shipped, String other) {
        when(jdbc.queryForObject(contains("movement_type = 'PICK'"), eq(BigDecimal.class), any(Object[].class))).thenReturn(n(shipped));
        when(jdbc.queryForObject(contains("greatest(x.received"), eq(BigDecimal.class), any(Object[].class))).thenReturn(n(other));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void openReturn(String status, UUID orderId) throws Exception {
        java.sql.ResultSet hdr = mock(java.sql.ResultSet.class);
        when(hdr.getObject(1, UUID.class)).thenReturn(client);
        when(hdr.getObject(2, UUID.class)).thenReturn(orderId);
        when(hdr.getString(3)).thenReturn(status);
        when(hdr.getString(4)).thenReturn("RMA-00007");
        when(jdbc.query(contains("FROM whse_returns WHERE id = ? AND tenant_id = ? FOR UPDATE"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(i -> List.of(((RowMapper) i.getArgument(1)).mapRow(hdr, 0)));
        when(jdbc.query(contains("FROM whse_return_lines WHERE id"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(item));
        when(jdbc.queryForObject(contains("FROM whse_return_receipts WHERE line_id"), eq(BigDecimal.class), any(Object[].class))).thenReturn(BigDecimal.ZERO);
    }

    @Test @DisplayName("a return is numbered from the tenant sequence and its lines are stored")
    void creates() {
        UUID id = service.create(t(), client, null, "faulty on arrival", null, List.of(new WhseReturnsService.NewLine(item, n("2"))), actor);
        assertNotNull(id);
        verify(jdbc).update(contains("INSERT INTO whse_returns"), eq(id), eq(tenant), eq("RMA-00007"), eq(client), isNull(), eq("faulty on arrival"), isNull(), eq(actor));
        verify(jdbc).update(contains("INSERT INTO whse_return_lines"), any(), eq(tenant), eq(id), eq(item), eq(n("2")));
    }

    @Test @DisplayName("a line cannot ask for more than the linked order still has to come back")
    void cannotExceedShipped() {
        when(jdbc.query(contains("status || ':'"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("SHIPPED:" + client));
        shipped("10", "8");
        assertThrows(IllegalArgumentException.class, () -> service.create(t(), client, order, "faulty", null, List.of(new WhseReturnsService.NewLine(item, n("3"))), actor));
        assertDoesNotThrow(() -> service.create(t(), client, order, "faulty", null, List.of(new WhseReturnsService.NewLine(item, n("2"))), actor));
    }

    @Test @DisplayName("only a shipped order of the same client can be linked")
    void orderMustBeShipped() {
        when(jdbc.query(contains("status || ':'"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("PICKING:" + client));
        assertThrows(HandyFlowException.class, () -> service.create(t(), client, order, "faulty", null, List.of(new WhseReturnsService.NewLine(item, n("1"))), actor));
        when(jdbc.query(contains("status || ':'"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("SHIPPED:" + UUID.randomUUID()));
        assertThrows(HandyFlowException.class, () -> service.create(t(), client, order, "faulty", null, List.of(new WhseReturnsService.NewLine(item, n("1"))), actor));
    }

    @Test @DisplayName("validation: reason, lines, duplicate items and quantities")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(t(), client, null, " ", null, List.of(new WhseReturnsService.NewLine(item, n("1"))), actor));
        assertThrows(IllegalArgumentException.class, () -> service.create(t(), client, null, "faulty", null, List.of(), actor));
        assertThrows(IllegalArgumentException.class, () -> service.create(t(), client, null, "faulty", null, List.of(new WhseReturnsService.NewLine(item, n("0"))), actor));
        assertThrows(IllegalArgumentException.class, () -> service.create(t(), client, null, "faulty", null,
                List.of(new WhseReturnsService.NewLine(item, n("1")), new WhseReturnsService.NewLine(item, n("2"))), actor));
    }

    @Test @DisplayName("received goods are posted as RETURN movements in restock, quarantine and damaged status")
    void receivesInThreeStatuses() throws Exception {
        openReturn("EXPECTED", null);
        service.receive(t(), ret, line, n("2"), n("1"), n("1"), location, "box crushed in transit", null, null, actor);
        verify(inventory).receiveStockInStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq(n("2")), eq("AVAILABLE"), eq("RETURN"), eq("RETURN"), eq(ret), eq("RMA-00007"), any(), eq(actor), anyString());
        verify(inventory).receiveStockInStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq(n("1")), eq("QUARANTINE"), eq("RETURN"), eq("RETURN"), eq(ret), eq("RMA-00007"), any(), eq(actor), anyString());
        verify(inventory).receiveStockInStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq(n("1")), eq("DAMAGED"), eq("RETURN"), eq("RETURN"), eq(ret), eq("RMA-00007"), any(), eq(actor), anyString());
        verify(jdbc).update(contains("status = 'RECEIVING'"), any(Object[].class));
    }

    @Test @DisplayName("held or damaged goods need a reason; an empty receipt is refused; nothing is posted")
    void receiveValidation() throws Exception {
        openReturn("EXPECTED", null);
        assertThrows(IllegalArgumentException.class, () -> service.receive(t(), ret, line, n("1"), n("1"), null, location, "no", null, null, actor));
        assertThrows(IllegalArgumentException.class, () -> service.receive(t(), ret, line, null, null, null, location, null, null, null, actor));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("receiving beyond what the linked order shipped is refused")
    void receiveCap() throws Exception {
        openReturn("EXPECTED", order);
        shipped("10", "8");
        assertThrows(IllegalArgumentException.class, () -> service.receive(t(), ret, line, n("3"), null, null, location, null, null, null, actor));
        verifyNoInteractions(inventory);
        assertDoesNotThrow(() -> service.receive(t(), ret, line, n("2"), null, null, location, null, null, null, actor));
    }

    @Test @DisplayName("a closed return cannot be received against")
    void closedRefused() throws Exception {
        openReturn("CLOSED", null);
        assertThrows(HandyFlowException.class, () -> service.receive(t(), ret, line, n("1"), null, null, location, null, null, null, actor));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a repeated idempotency key returns the first receipt and posts nothing")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void idempotent() {
        UUID first = UUID.randomUUID();
        when(jdbc.query(contains("idempotency_key"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(first));
        assertEquals(first, service.receive(t(), ret, line, n("1"), null, null, location, null, null, "scan-9", actor));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("only an unreceived return can be cancelled")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void cancelRules() {
        when(jdbc.query(contains("SELECT status FROM whse_returns"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("RECEIVING"));
        assertThrows(HandyFlowException.class, () -> service.cancel(t(), ret, actor));
        when(jdbc.query(contains("SELECT status FROM whse_returns"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("EXPECTED"));
        service.cancel(t(), ret, actor);
        verify(jdbc).update(contains("status = 'CANCELLED'"), any(Object[].class));
    }

    @Test @DisplayName("no user means nothing is recorded")
    void noActor() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.cancel(t(), ret, null));
    }
}
