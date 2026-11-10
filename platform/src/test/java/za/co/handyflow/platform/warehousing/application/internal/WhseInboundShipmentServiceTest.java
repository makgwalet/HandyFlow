package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentLineRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WhseInboundShipmentServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID loc = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private WhseInboundShipmentRepository shipments;
    private WhseInboundShipmentLineRepository lines;
    private WhseInventoryService inventory;
    private JdbcTemplate jdbc;
    private WhseInboundShipmentService service;
    private WhseInboundShipment shipment;
    private WhseInboundShipmentLine line;

    @BeforeEach
    void setUp() {
        shipments = mock(WhseInboundShipmentRepository.class);
        lines = mock(WhseInboundShipmentLineRepository.class);
        inventory = mock(WhseInventoryService.class);
        jdbc = mock(JdbcTemplate.class);
        service = new WhseInboundShipmentService(shipments, lines, inventory, mock(WhseClientService.class), jdbc);
        shipment = WhseInboundShipment.create(tenant, client, "IN-1", null, null);
        line = WhseInboundShipmentLine.create(tenant, UUID.randomUUID(), item, new BigDecimal("10"), null);
        when(shipments.findByTenantAndId(eq(tenant), any())).thenReturn(Optional.of(shipment));
        when(lines.findByTenantAndId(eq(tenant), any())).thenReturn(Optional.of(line));
    }

    @Test
    @DisplayName("quick receive of a lot-tracked item is refused with a pointer to the lot receipt, and posts nothing")
    void lotTrackedItemRefused() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(true);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.receiveLine(TenantId.of(tenant), line.getShipmentId(), UUID.randomUUID(), new BigDecimal("5"), loc, user));
        assertTrue(e.getMessage().contains("lot"));
        verifyNoInteractions(inventory);
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getReceivedQty()));
    }

    @Test
    @DisplayName("quick receive of an untracked item still posts stock")
    void untrackedItemReceived() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(Object[].class))).thenReturn(false);
        service.receiveLine(TenantId.of(tenant), line.getShipmentId(), UUID.randomUUID(), new BigDecimal("5"), loc, user);
        verify(inventory).receiveStock(any(), eq(client), eq(item), eq(loc), eq(new BigDecimal("5")), eq("INBOUND_SHIPMENT"), any(), eq("IN-1"), eq(user));
    }
}
