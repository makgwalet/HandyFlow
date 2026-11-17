package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Forecourt replenishment: a supply run into a forecourt tank is ONE stock transfer linked to the dispatch. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDispatchIntoForecourtTankTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelTankRepository tankRepository;
    @Mock FuelDepotRepository depotRepository;
    @Mock FuelDispatchRepository dispatchRepository;
    @Mock FuelStockService stockService;
    @Mock FuelStockLedger ledger;
    @Mock CrmFacade crmFacade;
    @Mock ApprovalFacade approvalFacade;
    @InjectMocks FuelService service;

    FuelDepot terminal, forecourt;
    FuelTank source, dest;
    UUID terminalId = UUID.randomUUID(), forecourtId = UUID.randomUUID();
    UUID transferId = UUID.randomUUID();

    private FuelDepot depot(UUID id, String name, UUID entity) {
        FuelDepot d = mock(FuelDepot.class);
        when(d.getId()).thenReturn(id); when(d.getName()).thenReturn(name); when(d.getLegalEntityId()).thenReturn(entity);
        when(depotRepository.findActiveById(T, id)).thenReturn(Optional.of(d));
        return d;
    }
    private FuelTank tank(UUID depot, String fuel, String litres) {
        FuelTank t = mock(FuelTank.class);
        UUID id = UUID.randomUUID();
        when(t.getId()).thenReturn(id); when(t.getDepotId()).thenReturn(depot); when(t.getFuelType()).thenReturn(fuel); when(t.getName()).thenReturn("Tank " + fuel);
        when(t.getCurrentLitres()).thenReturn(bd(litres)); when(t.isLow()).thenReturn(false); when(t.getCostPerLitreWac()).thenReturn(bd("20"));
        when(tankRepository.findActiveById(T, id)).thenReturn(Optional.of(t));
        when(tankRepository.findActiveByIdForUpdate(T, id)).thenReturn(Optional.of(t));
        return t;
    }

    @BeforeEach void setUp() {
        terminal = depot(terminalId, "Terminal", null);
        forecourt = depot(forecourtId, "Forecourt", null);
        source = tank(terminalId, "DIESEL", "4000");
        dest = tank(forecourtId, "DIESEL", "1000");
        when(crmFacade.customerExists(any(), any())).thenReturn(true);
        when(stockService.transfer(eq(T), any())).thenReturn(new TransferResponse(transferId, source.getId(), dest.getId(), bd("1000"), "Supply run to Forecourt", Instant.now()));
        when(dispatchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private DispatchFuelRequest req(UUID destDepot, UUID destTank) {
        return new DispatchFuelRequest(bd("1000"), null, Instant.now(), null, null, null, "Supply run", null, null, null, null, destDepot, destTank);
    }

    @Test @DisplayName("a supply run into a forecourt tank makes one transfer, links the dispatch to it and posts no stock line of its own")
    void oneTransferLinkedToTheDispatch() {
        DispatchResponse r = service.dispatchFuel(T, source.getId(), req(forecourtId, dest.getId()), USER);
        var capt = ArgumentCaptor.forClass(TransferRequest.class);
        verify(stockService, times(1)).transfer(eq(T), capt.capture());
        assertEquals(source.getId(), capt.getValue().fromTankId());
        assertEquals(dest.getId(), capt.getValue().toTankId());
        assertEquals(0, bd("1000").compareTo(capt.getValue().litres()));
        assertEquals(transferId, r.transferId());
        assertEquals(forecourtId, r.destinationDepotId());
        verify(source, never()).removeStock(any());                 // the transfer moved the stock, the dispatch did not
        verify(ledger, never()).post(any(), eq(FuelStockMovement.Type.DISPATCH), any(), any(), any(), any());
    }

    @Test @DisplayName("the tank must be at the chosen forecourt, hold the same fuel, and not be at the depot the fuel leaves")
    void tankChecks() {
        FuelTank elsewhere = tank(UUID.randomUUID(), "DIESEL", "0");
        assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), req(forecourtId, elsewhere.getId()), USER));
        FuelTank petrol = tank(forecourtId, "PETROL", "0");
        assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), req(forecourtId, petrol.getId()), USER));
        FuelTank sameDepot = tank(terminalId, "DIESEL", "0");
        assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), req(terminalId, sameDepot.getId()), USER));
        verify(stockService, never()).transfer(any(), any());
        verify(dispatchRepository, never()).save(any());
    }

    @Test @DisplayName("a destination tank needs a destination forecourt, and cannot also be issued to a vehicle, asset or customer")
    void needsForecourtAndNoOtherRecipient() {
        assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), req(null, dest.getId()), USER));
        var withCustomer = new DispatchFuelRequest(bd("1000"), null, Instant.now(), null, null, UUID.randomUUID(), "X", null, null, null, null, forecourtId, dest.getId());
        assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), withCustomer, USER));
        verify(stockService, never()).transfer(any(), any());
    }

    @Test @DisplayName("depots of different legal entities are refused: that is an inter-entity transfer, which is not built")
    void interEntityRefused() {
        when(terminal.getLegalEntityId()).thenReturn(UUID.randomUUID());
        when(forecourt.getLegalEntityId()).thenReturn(UUID.randomUUID());
        var ex = assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), req(forecourtId, dest.getId()), USER));
        assertTrue(ex.getMessage().contains("inter-entity"));
        verify(stockService, never()).transfer(any(), any());
    }

    @Test @DisplayName("the same legal entity on both depots is fine")
    void sameEntityAllowed() {
        UUID entity = UUID.randomUUID();
        when(terminal.getLegalEntityId()).thenReturn(entity);
        when(forecourt.getLegalEntityId()).thenReturn(entity);
        assertEquals(transferId, service.dispatchFuel(T, source.getId(), req(forecourtId, dest.getId()), USER).transferId());
    }

    @Test @DisplayName("a short or reserved source tank refuses the transfer and no dispatch is recorded")
    void shortTankRecordsNothing() {
        when(stockService.transfer(eq(T), any())).thenThrow(new IllegalArgumentException("Insufficient stock"));
        assertThrows(IllegalArgumentException.class, () -> service.dispatchFuel(T, source.getId(), req(forecourtId, dest.getId()), USER));
        verify(dispatchRepository, never()).save(any());
    }

    @Test @DisplayName("reversing such a dispatch reverses the transfer and does not restore stock a second time")
    void reversalReversesTheTransfer() {
        FuelDispatch d = FuelDispatch.create(T, source.getId(), null, null, null, "Supply run", bd("1000"), null, Instant.now(), null, null, null, null, bd("4000"), bd("3000"));
        d.setDestinationDepot(forecourtId); d.linkTransfer(transferId);
        when(dispatchRepository.findActiveById(T, d.getId())).thenReturn(Optional.of(d));
        DispatchResponse r = service.reverseDispatch(T, d.getId(), new ReverseRequest("Wrong forecourt"), USER);
        verify(stockService).reverseTransfer(T, transferId, "Wrong forecourt");
        verify(source, never()).restoreStock(any());
        verify(ledger, never()).post(any(), eq(FuelStockMovement.Type.REVERSAL), any(), eq("DISPATCH"), any(), any());
        assertNotNull(r.reversedAt());
        assertThrows(IllegalStateException.class, () -> service.reverseDispatch(T, d.getId(), new ReverseRequest("Again"), USER));
    }

    @Test @DisplayName("a dispatch with no destination tank behaves exactly as before: the dispatch removes the stock itself")
    void plainDispatchUnchanged() {
        service.dispatchFuel(T, source.getId(), new DispatchFuelRequest(bd("500"), null, Instant.now(), null, null, null, "Yard use", null, null, null, null, null, null), USER);
        verify(stockService, never()).transfer(any(), any());
        verify(source).removeStock(bd("500"));
        verify(ledger).post(eq(source), eq(FuelStockMovement.Type.DISPATCH), any(), eq("DISPATCH"), any(), any());
    }
}
