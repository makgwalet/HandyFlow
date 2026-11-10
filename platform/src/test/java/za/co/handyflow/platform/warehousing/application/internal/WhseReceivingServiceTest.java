package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseReceivingServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID location = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();
    private final UUID inspector = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseInboundShipmentRepository shipments;
    private WhseInboundShipmentLineRepository lines;
    private WhseInventoryService inventory;
    private SeparationOfDutiesChecker duties;
    private WhseLotService lotService;
    private WhseReceivingService service;
    private WhseInboundShipment shipment;
    private WhseInboundShipmentLine line;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        shipments = mock(WhseInboundShipmentRepository.class);
        lines = mock(WhseInboundShipmentLineRepository.class);
        inventory = mock(WhseInventoryService.class);
        duties = mock(SeparationOfDutiesChecker.class);
        lotService = mock(WhseLotService.class);
        service = new WhseReceivingService(jdbc, shipments, lines, inventory, duties, lotService);
        shipment = WhseInboundShipment.create(tenant, client, "ASN-1", null, null);
        line = WhseInboundShipmentLine.create(tenant, shipment.getId(), item, new BigDecimal("10"), null);
        when(shipments.findByTenantAndId(tenant, shipment.getId())).thenReturn(Optional.of(shipment));
        when(lines.findByTenantAndId(tenant, line.getId())).thenReturn(Optional.of(line));
        when(lines.findByShipment(tenant, shipment.getId())).thenReturn(List.of(line));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("closed_at IS NOT NULL"), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(contains("whse_receipt_events WHERE shipment_id"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.query(contains("idempotency_key"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    }

    private TenantId t() { return TenantId.of(tenant); }
    private static BigDecimal n(String v) { return new BigDecimal(v); }

    @Test @DisplayName("a receipt splits into good, damaged and held stock, each posted in its own status")
    void splitsByStatus() {
        service.receive(t(), shipment.getId(), line.getId(), n("5"), n("2"), n("3"), location, "crushed cartons", null, null, null, null, receiver);

        verify(inventory).receiveStockInStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq(n("5")), eq("AVAILABLE"), eq("RECEIPT"), anyString(), any(), any(), any(), eq(receiver), anyString());
        verify(inventory).receiveStockInStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq(n("2")), eq("DAMAGED"), eq("RECEIPT"), anyString(), any(), any(), any(), eq(receiver), anyString());
        verify(inventory).receiveStockInStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq(n("3")), eq("QUARANTINE"), eq("RECEIPT"), anyString(), any(), any(), any(), eq(receiver), anyString());
        assertEquals(0, n("10").compareTo(line.getReceivedQty()));
        assertEquals("RECEIVED", shipment.getStatus());
    }

    @Test @DisplayName("a zero part is not posted")
    void zeroPartsSkipped() {
        service.receive(t(), shipment.getId(), line.getId(), n("4"), null, null, location, null, null, null, null, null, receiver);
        verify(inventory, times(1)).receiveStockInStatus(any(), any(), any(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any(), any(), any());
        assertEquals("PARTIALLY_RECEIVED", shipment.getStatus());
    }

    @Test @DisplayName("damaged or held stock needs a reason; nothing is posted without one")
    void reasonRequired() {
        assertThrows(IllegalArgumentException.class, () -> service.receive(t(), shipment.getId(), line.getId(), n("5"), n("1"), null, location, " no", null, null, null, null, receiver));
        assertThrows(IllegalArgumentException.class, () -> service.receive(t(), shipment.getId(), line.getId(), null, null, null, location, null, null, null, null, null, receiver));
        assertThrows(IllegalArgumentException.class, () -> service.receive(t(), shipment.getId(), line.getId(), n("-1"), null, null, location, null, null, null, null, null, receiver));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a repeated request with the same idempotency key returns the first receipt and posts nothing")
    void idempotent() {
        UUID first = UUID.randomUUID();
        when(jdbc.query(contains("idempotency_key"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(first));
        assertEquals(first, service.receive(t(), shipment.getId(), line.getId(), n("5"), null, null, location, null, null, null, null, "scan-1", receiver));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a closed shipment cannot be received against")
    void closedRefused() {
        when(jdbc.queryForObject(contains("closed_at IS NOT NULL"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        assertThrows(HandyFlowException.class, () -> service.receive(t(), shipment.getId(), line.getId(), n("5"), null, null, location, null, null, null, null, null, receiver));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("the person who received the stock cannot inspect it; the checker's refusal stops the status change")
    void receiverCannotInspect() {
        stubReceipt(n("3"), receiver);
        when(jdbc.queryForObject(contains("sum(qty)"), eq(BigDecimal.class), any(Object[].class))).thenReturn(BigDecimal.ZERO);
        doThrow(new HandyFlowException("no", org.springframework.http.HttpStatus.FORBIDDEN, "SEPARATION_OF_DUTIES"))
                .when(duties).require(any(), anyString(), any(), any(), anyString(), anyString(), any(), anyString());
        assertThrows(HandyFlowException.class, () -> service.inspect(t(), UUID.randomUUID(), "PASS", n("3"), "all good here", receiver));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("passing held stock releases it to available; failing it moves it to damaged")
    void passAndFail() {
        stubReceipt(n("3"), receiver);
        when(jdbc.queryForObject(contains("sum(qty)"), eq(BigDecimal.class), any(Object[].class))).thenReturn(BigDecimal.ZERO);
        service.inspect(t(), UUID.randomUUID(), "PASS", n("2"), "label and seal intact", inspector);
        verify(inventory).changeStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq("QUARANTINE"), eq("AVAILABLE"), eq(n("2")), contains("pass"), eq(inspector), startsWith("insp:"));
        service.inspect(t(), UUID.randomUUID(), "FAIL", n("1"), "seal broken on opening", inspector);
        verify(inventory).changeStatus(eq(t()), eq(client), eq(item), eq(location), any(), eq("QUARANTINE"), eq("DAMAGED"), eq(n("1")), contains("fail"), eq(inspector), startsWith("insp:"));
    }

    @Test @DisplayName("cannot inspect more than is still held, or when nothing is held")
    void overInspect() {
        stubReceipt(n("3"), receiver);
        when(jdbc.queryForObject(contains("sum(qty)"), eq(BigDecimal.class), any(Object[].class))).thenReturn(n("2"));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(t(), UUID.randomUUID(), "PASS", n("2"), "all good here", inspector));
        when(jdbc.queryForObject(contains("sum(qty)"), eq(BigDecimal.class), any(Object[].class))).thenReturn(n("3"));
        assertThrows(HandyFlowException.class, () -> service.inspect(t(), UUID.randomUUID(), "PASS", n("1"), "all good here", inspector));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a bad outcome, no quantity or a short reason are refused")
    void inspectValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.inspect(t(), UUID.randomUUID(), "MAYBE", n("1"), "all good here", inspector));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(t(), UUID.randomUUID(), "PASS", null, "all good here", inspector));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(t(), UUID.randomUUID(), "PASS", n("1"), "ok", inspector));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubReceipt(BigDecimal held, UUID receivedBy) {
        try {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject(1, UUID.class)).thenReturn(shipment.getId());
            when(rs.getObject(2, UUID.class)).thenReturn(client);
            when(rs.getObject(3, UUID.class)).thenReturn(item);
            when(rs.getObject(4, UUID.class)).thenReturn(location);
            when(rs.getBigDecimal(5)).thenReturn(held);
            when(rs.getObject(6, UUID.class)).thenReturn(receivedBy);
            when(rs.getString(7)).thenReturn("ASN-1");
            when(rs.getObject(8, UUID.class)).thenReturn(null);
            when(jdbc.query(contains("FOR UPDATE OF e"), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
                RowMapper m = inv.getArgument(1);
                return List.of(m.mapRow(rs, 0));
            });
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test @DisplayName("no user means nothing is recorded")
    void noActor() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.receive(t(), shipment.getId(), line.getId(), n("1"), null, null, location, null, null, null, null, null, null));
    }
}
