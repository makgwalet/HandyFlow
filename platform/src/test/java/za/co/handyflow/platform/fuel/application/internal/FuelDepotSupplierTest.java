package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.DepotRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Phase 8d: a depot may record the depot that supplies it. The link is checked and can never loop. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDepotSupplierTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());

    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock FuelStockMovementRepository movements;
    @Mock FuelTransferRepository transfers;
    @Mock FuelReservationRepository reservations;
    @Mock FuelStockLedger ledger;
    @Mock CrmFacade crm;
    FuelStockService service;

    @BeforeEach void setUp() {
        service = new FuelStockService(tanks, depots, movements, transfers, reservations, ledger, crm);
        when(depots.save(any(FuelDepot.class))).thenAnswer(i -> i.getArgument(0));
        when(depots.codeInUse(any(), any(), any())).thenReturn(false);
        when(tanks.findAllActive(T)).thenReturn(List.of());
    }

    private FuelDepot depot(String name) {
        FuelDepot d = FuelDepot.create(T, name, name.substring(0, 3), null);
        when(depots.findActiveById(T, d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    @Test @DisplayName("a new depot can name its supplier, and the response carries it")
    void createWithSupplier() {
        FuelDepot terminal = depot("Terminal");
        var out = service.createDepot(T, new DepotRequest("Forecourt One", "FC1", null, terminal.getId()));
        assertEquals(terminal.getId(), out.supplyingDepotId());
    }

    @Test @DisplayName("the three-argument request still works and sets no supplier")
    void legacyRequest() {
        assertNull(service.createDepot(T, new DepotRequest("Plain", "PLN", null)).supplyingDepotId());
    }

    @Test @DisplayName("a supplier that does not exist is refused")
    void unknownSupplier() {
        when(depots.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createDepot(T, new DepotRequest("F", "FC1", null, UUID.randomUUID())));
    }

    @Test @DisplayName("a depot cannot supply itself")
    void selfSupply() {
        FuelDepot d = depot("Forecourt");
        assertThrows(IllegalArgumentException.class, () -> service.updateDepot(T, d.getId(), new DepotRequest("Forecourt", "FOR", null, d.getId())));
    }

    @Test @DisplayName("a loop in the supply chain is refused (A supplied by B, then B supplied by A)")
    void loopRefused() {
        FuelDepot a = depot("Alpha"), b = depot("Bravo");
        a.setSupplier(b.getId());
        assertThrows(IllegalArgumentException.class, () -> service.updateDepot(T, b.getId(), new DepotRequest("Bravo", "BRA", null, a.getId())));
    }

    @Test @DisplayName("a longer loop is refused too (A <- B <- C, then C supplied by A would close it)")
    void longLoopRefused() {
        FuelDepot a = depot("Alpha"), b = depot("Bravo"), c = depot("Charlie");
        a.setSupplier(b.getId()); b.setSupplier(c.getId());
        assertThrows(IllegalArgumentException.class, () -> service.updateDepot(T, c.getId(), new DepotRequest("Charlie", "CHA", null, a.getId())));
    }

    @Test @DisplayName("updating with no supplier clears the link")
    void clearSupplier() {
        FuelDepot a = depot("Alpha"), b = depot("Bravo");
        a.setSupplier(b.getId());
        var out = service.updateDepot(T, a.getId(), new DepotRequest("Alpha", "ALP", null, null));
        assertNull(out.supplyingDepotId());
    }

    @Test @DisplayName("a valid supplier can be set on update")
    void setOnUpdate() {
        FuelDepot a = depot("Alpha"), b = depot("Bravo");
        assertEquals(b.getId(), service.updateDepot(T, a.getId(), new DepotRequest("Alpha", "ALP", null, b.getId())).supplyingDepotId());
    }
}
