package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseStockMovement;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInventoryRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseStockMovementRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ledger rule from the WMS roadmap, engine 3: a stock balance must be explainable from its movements.
 * The repositories are in-memory so a whole receipt, allocation, pick and adjustment sequence runs through the
 * real service and entity code.
 */
class WhseInventoryServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID loc = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private WhseInventoryRepository inventoryRepo;
    private WhseStockMovementRepository movementRepo;
    private WhseInventoryService service;
    private final List<WhseInventory> positions = new ArrayList<>();
    private final List<WhseStockMovement> ledger = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inventoryRepo = mock(WhseInventoryRepository.class);
        movementRepo = mock(WhseStockMovementRepository.class);
        when(inventoryRepo.findPosition(eq(tenant), eq(client), eq(item), eq(loc)))
                .thenAnswer(i -> positions.stream().findFirst());
        when(inventoryRepo.save(any(WhseInventory.class))).thenAnswer(i -> {
            WhseInventory p = i.getArgument(0);
            if (!positions.contains(p)) positions.add(p);
            return p;
        });
        when(movementRepo.save(any(WhseStockMovement.class))).thenAnswer(i -> {
            WhseStockMovement m = i.getArgument(0);
            ledger.add(m);
            return m;
        });
        service = new WhseInventoryService(inventoryRepo, movementRepo);
    }

    private BigDecimal ledgerTotal() {
        return ledger.stream().map(WhseStockMovement::getQtyChange).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private TenantId t() { return TenantId.of(tenant); }

    private static BigDecimal q(int n) { return BigDecimal.valueOf(n); }

    @Test
    @DisplayName("balance equals the sum of movements after receipt, allocation, pick and adjustment")
    void balanceIsExplainedByLedger() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(4));
        service.fulfillPick(t(), client, item, loc, q(4), "OUTBOUND_ORDER", UUID.randomUUID(), "OUT-1", user);
        WhseInventory inv = positions.get(0);
        when(inventoryRepo.findByTenantAndId(tenant, inv.getId())).thenReturn(Optional.of(inv));
        service.adjust(t(), inv.getId(), q(-2), "damaged in rack", user);

        assertEquals(0, q(4).compareTo(inv.getQtyOnHand()));
        assertEquals(0, inv.getQtyOnHand().compareTo(ledgerTotal()));
        assertEquals(3, ledger.size());
    }

    @Test
    @DisplayName("every movement continues from the previous balance: before + change = after, and before = prior after")
    void movementsChain() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(6));
        service.fulfillPick(t(), client, item, loc, q(6), "OUTBOUND_ORDER", UUID.randomUUID(), "OUT-1", user);
        service.receiveStock(t(), client, item, loc, q(5), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-2", user);

        BigDecimal running = BigDecimal.ZERO;
        for (WhseStockMovement m : ledger) {
            assertEquals(0, running.compareTo(m.getQtyBefore()), "before must equal the previous after");
            assertEquals(0, m.getQtyBefore().add(m.getQtyChange()).compareTo(m.getQtyAfter()));
            running = m.getQtyAfter();
        }
        assertEquals(0, q(9).compareTo(running));
    }

    @Test
    @DisplayName("allocation is a reservation: it writes no movement and leaves on-hand unchanged (current behaviour)")
    void allocationWritesNoMovement() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(4));

        assertEquals(1, ledger.size());
        assertEquals(0, q(10).compareTo(positions.get(0).getQtyOnHand()));
        assertEquals(0, q(6).compareTo(positions.get(0).available()));
    }

    @Test
    @DisplayName("allocating more than is available is refused and changes nothing")
    void overAllocationRefused() {
        service.receiveStock(t(), client, item, loc, q(5), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(4));

        assertThrows(IllegalStateException.class, () -> service.allocateStock(t(), client, item, loc, q(2)));
        assertEquals(0, q(4).compareTo(positions.get(0).getQtyAllocated()));
        assertEquals(1, ledger.size());
    }

    @Test
    @DisplayName("an adjustment that would take on-hand below zero is refused and writes no movement")
    void adjustmentCannotGoNegative() {
        service.receiveStock(t(), client, item, loc, q(3), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        UUID id = positions.get(0).getId();
        when(inventoryRepo.findByTenantAndId(tenant, id)).thenReturn(Optional.of(positions.get(0)));

        assertThrows(IllegalStateException.class, () -> service.adjust(t(), id, q(-4), "typo", user));
        assertEquals(1, ledger.size());
        assertEquals(0, q(3).compareTo(positions.get(0).getQtyOnHand()));
    }

    @Test
    @DisplayName("a pick of stock that was never allocated is refused and writes no movement")
    void pickWithoutAllocationRefused() {
        service.receiveStock(t(), client, item, loc, q(5), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);

        assertThrows(IllegalStateException.class,
                () -> service.fulfillPick(t(), client, item, loc, q(1), "OUTBOUND_ORDER", UUID.randomUUID(), "OUT-1", user));
        assertEquals(1, ledger.size());
    }

    @Test
    @DisplayName("the recorded movement carries the actor, reference and a signed quantity")
    void movementDetails() {
        UUID ref = UUID.randomUUID();
        service.receiveStock(t(), client, item, loc, q(8), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(3));
        service.fulfillPick(t(), client, item, loc, q(3), "OUTBOUND_ORDER", ref, "OUT-9", user);

        WhseStockMovement pick = ledger.get(1);
        assertEquals("PICK", pick.getMovementType());
        assertEquals(0, q(-3).compareTo(pick.getQtyChange()));
        assertEquals(ref, pick.getReferenceId());
        assertEquals("OUT-9", pick.getReferenceNumber());
        assertEquals(user, pick.getRecordedByUserId());
    }
}
