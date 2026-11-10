package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseAllocationEvent;
import za.co.handyflow.platform.warehousing.domain.model.WhseInventory;
import za.co.handyflow.platform.warehousing.domain.model.WhseStockMovement;
import za.co.handyflow.platform.warehousing.domain.repository.WhseAllocationEventRepository;
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
    private WhseAllocationEventRepository allocationRepo;
    private WhseInventoryService service;
    private final List<WhseInventory> positions = new ArrayList<>();
    private final List<WhseStockMovement> ledger = new ArrayList<>();
    private final List<WhseAllocationEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inventoryRepo = mock(WhseInventoryRepository.class);
        movementRepo = mock(WhseStockMovementRepository.class);
        allocationRepo = mock(WhseAllocationEventRepository.class);
        when(inventoryRepo.findPosition(eq(tenant), eq(client), eq(item), eq(loc)))
                .thenAnswer(i -> positions.stream().filter(p -> "AVAILABLE".equals(p.getStatus())).findFirst());
        when(inventoryRepo.findPositionWithStatus(eq(tenant), eq(client), eq(item), eq(loc), any(String.class)))
                .thenAnswer(i -> positions.stream().filter(p -> p.getStatus().equals(i.getArgument(4))).findFirst());
        when(movementRepo.findFirstByTenantIdAndIdempotencyKey(eq(tenant), any(String.class)))
                .thenAnswer(i -> ledger.stream().filter(m -> i.getArgument(1).equals(m.getIdempotencyKey())).findFirst());
        when(allocationRepo.save(any(WhseAllocationEvent.class))).thenAnswer(i -> {
            WhseAllocationEvent e = i.getArgument(0);
            events.add(e);
            return e;
        });
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
        service = new WhseInventoryService(inventoryRepo, movementRepo, allocationRepo);
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

    private BigDecimal ledgerTotal(String status) {
        return ledger.stream().filter(m -> status.equals(m.getStatus())).map(WhseStockMovement::getQtyChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private WhseInventory position(String status) {
        return positions.stream().filter(p -> p.getStatus().equals(status)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("allocation, fulfilment and release each write an allocation event that chains")
    void allocationEventsChain() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        UUID order = UUID.randomUUID();
        service.allocateStock(t(), client, item, loc, q(6), "OUTBOUND_ORDER", order, "OUT-1", user);
        service.deallocateStock(t(), client, item, loc, q(2), "OUTBOUND_ORDER", order, "OUT-1", user);
        service.fulfillPick(t(), client, item, loc, q(4), "OUTBOUND_ORDER", order, "OUT-1", user);

        assertEquals(3, events.size());
        assertEquals("ALLOCATE", events.get(0).getEventType());
        assertEquals(0, q(0).compareTo(events.get(0).getAllocatedBefore()));
        assertEquals(0, q(6).compareTo(events.get(0).getAllocatedAfter()));
        assertEquals("DEALLOCATE", events.get(1).getEventType());
        assertEquals(0, q(4).compareTo(events.get(1).getAllocatedAfter()));
        assertEquals("FULFIL", events.get(2).getEventType());
        assertEquals(0, q(0).compareTo(events.get(2).getAllocatedAfter()));
        assertEquals(order, events.get(2).getReferenceId());
        assertEquals(0, positions.get(0).getQtyAllocated().compareTo(q(0)));
    }

    @Test
    @DisplayName("repeating an adjustment with the same idempotency key is applied once")
    void adjustmentIsIdempotent() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        WhseInventory inv = positions.get(0);
        when(inventoryRepo.findByTenantAndId(tenant, inv.getId())).thenReturn(Optional.of(inv));

        service.adjust(t(), inv.getId(), q(-3), "damaged", user, "key-1");
        service.adjust(t(), inv.getId(), q(-3), "damaged", user, "key-1");

        assertEquals(0, q(7).compareTo(inv.getQtyOnHand()));
        assertEquals(2, ledger.size());
    }

    @Test
    @DisplayName("an idempotency key reused for a different position is refused")
    void idempotencyKeyCannotBeReused() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user, "key-9");

        assertThrows(IllegalStateException.class, () -> service.receiveStock(t(), client, UUID.randomUUID(), loc,
                q(1), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-2", user, "key-9"));
        assertEquals(1, ledger.size());
    }

    @Test
    @DisplayName("quarantine moves stock to its own position as two movements and every position still reconciles")
    void quarantineKeepsLedgerBalanced() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(4), "awaiting inspection", user, null);

        assertEquals(0, q(6).compareTo(position("AVAILABLE").getQtyOnHand()));
        assertEquals(0, q(4).compareTo(position("QUARANTINE").getQtyOnHand()));
        assertEquals(3, ledger.size());
        assertEquals(0, position("AVAILABLE").getQtyOnHand().compareTo(ledgerTotal("AVAILABLE")));
        assertEquals(0, position("QUARANTINE").getQtyOnHand().compareTo(ledgerTotal("QUARANTINE")));
        assertEquals(0, q(0).compareTo(position("QUARANTINE").available()));
    }

    @Test
    @DisplayName("quarantined stock cannot be allocated")
    void quarantinedStockIsNotAllocatable() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(10), "recall hold", user, null);

        assertThrows(IllegalStateException.class, () -> service.allocateStock(t(), client, item, loc, q(1)));
        assertThrows(IllegalStateException.class, () -> position("QUARANTINE").allocate(q(1)));
    }

    @Test
    @DisplayName("allocated stock cannot be moved to quarantine, only the unallocated part")
    void allocatedStockStaysPut() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(8));

        assertThrows(IllegalStateException.class,
                () -> service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(3), "hold it", user, null));
        service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(2), "hold it", user, null);
        assertEquals(0, q(8).compareTo(position("AVAILABLE").getQtyOnHand()));
    }

    @Test
    @DisplayName("release returns quarantined stock to available; damaged stock cannot be released")
    void releaseAndDamaged() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(5), "inspection", user, null);
        service.changeStatus(t(), client, item, loc, "QUARANTINE", "AVAILABLE", q(3), "passed inspection", user, null);
        service.changeStatus(t(), client, item, loc, "QUARANTINE", "DAMAGED", q(2), "failed inspection", user, null);

        assertEquals(0, q(8).compareTo(position("AVAILABLE").getQtyOnHand()));
        assertEquals(0, q(0).compareTo(position("QUARANTINE").getQtyOnHand()));
        assertEquals(0, q(2).compareTo(position("DAMAGED").getQtyOnHand()));
        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(t(), client, item, loc, "DAMAGED", "AVAILABLE", q(1), "put it back", user, null));
        for (String status : List.of("AVAILABLE", "QUARANTINE", "DAMAGED")) {
            assertEquals(0, position(status).getQtyOnHand().compareTo(ledgerTotal(status)), status);
        }
    }

    @Test
    @DisplayName("a status change needs a reason")
    void statusChangeNeedsReason() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);

        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(1), " ", user, null));
        assertEquals(1, ledger.size());
    }

    @Test
    @DisplayName("repeating a status change with the same key moves the stock once")
    void statusChangeIsIdempotent() {
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(4), "inspection", user, "chg-1");
        service.changeStatus(t(), client, item, loc, "AVAILABLE", "QUARANTINE", q(4), "inspection", user, "chg-1");

        assertEquals(0, q(6).compareTo(position("AVAILABLE").getQtyOnHand()));
        assertEquals(0, q(4).compareTo(position("QUARANTINE").getQtyOnHand()));
        assertEquals(3, ledger.size());
    }

    private void locationAwareLookups() {
        when(inventoryRepo.findPositionWithStatus(eq(tenant), eq(client), eq(item), any(UUID.class), any(String.class)))
                .thenAnswer(i -> positions.stream().filter(p -> p.getLocationId().equals(i.getArgument(3)) && p.getStatus().equals(i.getArgument(4))).findFirst());
    }

    @Test
    @DisplayName("a transfer moves unallocated stock to another location with an out and an in row that net to zero")
    void transferMovesStock() {
        locationAwareLookups();
        UUID loc2 = UUID.randomUUID();
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.transfer(t(), client, item, loc, loc2, null, "AVAILABLE", q(6), "PUTAWAY", "WHSE_TASK", UUID.randomUUID(), "Put-away", null, user, "task:1");

        assertEquals(2, positions.size());
        assertEquals(0, q(4).compareTo(positions.get(0).getQtyOnHand()));
        assertEquals(0, q(6).compareTo(positions.get(1).getQtyOnHand()));
        assertEquals(3, ledger.size());
        assertEquals(0, q(10).compareTo(ledgerTotal()), "stock is conserved: the ledger still totals what was received");
        assertEquals("task:1", ledger.get(1).getIdempotencyKey());
        assertEquals("task:1:in", ledger.get(2).getIdempotencyKey());
    }

    @Test
    @DisplayName("a transfer tops up a position that already exists at the destination")
    void transferTopsUp() {
        locationAwareLookups();
        UUID loc2 = UUID.randomUUID();
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.receiveStock(t(), client, item, loc2, q(5), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-2", user);
        service.transfer(t(), client, item, loc, loc2, null, "AVAILABLE", q(3), "TRANSFER", null, null, null, null, user, null);
        assertEquals(2, positions.size());
        assertEquals(0, q(8).compareTo(positions.get(1).getQtyOnHand()));
    }

    @Test
    @DisplayName("allocated stock cannot be transferred, and neither can more than is there, nor to the same place")
    void transferRefusals() {
        locationAwareLookups();
        UUID loc2 = UUID.randomUUID();
        service.receiveStock(t(), client, item, loc, q(10), "INBOUND_SHIPMENT", UUID.randomUUID(), "IN-1", user);
        service.allocateStock(t(), client, item, loc, q(8));
        assertThrows(IllegalStateException.class, () -> service.transfer(t(), client, item, loc, loc2, null, "AVAILABLE", q(3), "PUTAWAY", null, null, null, null, user, null));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(t(), client, item, loc, loc, null, "AVAILABLE", q(1), "PUTAWAY", null, null, null, null, user, null));
        assertThrows(IllegalArgumentException.class, () -> service.transfer(t(), client, item, loc, loc2, null, "AVAILABLE", q(1), "RECEIPT", null, null, null, null, user, null));
        assertThrows(IllegalStateException.class, () -> service.transfer(t(), client, item, loc, loc2, null, "QUARANTINE", q(1), "PUTAWAY", null, null, null, null, user, null));
        assertEquals(1, ledger.size(), "nothing was posted by the refused transfers");
    }
}
