package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.warehousing.domain.model.*;
import za.co.handyflow.platform.warehousing.domain.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Free storage days and the fixed pallet/bin fee, as the daily accrual applies them. */
class WhseFreeDaysAndFeesTest {

    private final UUID tenant = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 11, 5);

    private WhseStorageAccrualRepository accruals;
    private WhseLocationAccrualRepository locationAccruals;
    private WhseInventoryRepository inventoryRepo;
    private WhseClient client;
    private WhseItem item;

    @BeforeEach
    void setUp() {
        accruals = mock(WhseStorageAccrualRepository.class);
        locationAccruals = mock(WhseLocationAccrualRepository.class);
        inventoryRepo = mock(WhseInventoryRepository.class);
        client = WhseClient.create(tenant, "Acme", null, null, null, null, null, null, null, null, null);
        item = WhseItem.create(tenant, client.getId(), "SKU1", "Widget", "EACH", null);
        when(accruals.findAccruedInventoryIds(tenant, day)).thenReturn(List.of());
    }

    private WhseStorageAccrualService service(int freeDays, String locationFee) {
        WhseClientRepository clients = mock(WhseClientRepository.class);
        WhseItemRepository items = mock(WhseItemRepository.class);
        WhseProfileRepository profiles = mock(WhseProfileRepository.class);
        when(clients.findById(client.getId())).thenReturn(Optional.of(client));
        when(items.findAllActiveForClient(tenant, client.getId())).thenReturn(List.of(item));
        when(profiles.findByTenantId(tenant)).thenReturn(Optional.of(
                WhseProfile.create(tenant, "WH", null, new BigDecimal("3.0000"), null, null, null, null, null, null)));
        WhseTariffBook.TermsVersion terms = new WhseTariffBook.TermsVersion(UUID.randomUUID(), client.getId(), BigDecimal.ZERO,
                freeDays, new BigDecimal(locationFee), day.minusDays(60), Instant.parse("2026-01-01T00:00:00Z"));
        return new WhseStorageAccrualService(accruals, clients, items, profiles, inventoryRepo,
                tenantId -> new WhseTariffBook(List.of(), List.of(terms)), locationAccruals);
    }

    private WhseInventory position(UUID location, int qtyHeld, List<Object[]> heldRows) {
        WhseInventory inv = WhseInventory.create(tenant, client.getId(), item.getId(), location);
        heldRows.add(new Object[]{inv.getId(), BigDecimal.valueOf(qtyHeld)});
        return inv;
    }

    private void ledger(List<Object[]> heldRows, List<WhseInventory> positions, int receivedInWindow) {
        when(accruals.billableQuantities(eq(tenant), any(Instant.class), any(Instant.class))).thenReturn(heldRows);
        when(inventoryRepo.findAllById(any())).thenReturn(positions);
        List<Object[]> received = receivedInWindow > 0
                ? java.util.Collections.singletonList(new Object[]{client.getId(), item.getId(), null, BigDecimal.valueOf(receivedInWindow)})
                : List.of();
        when(accruals.receivedBetween(eq(tenant), any(Instant.class), any(Instant.class))).thenReturn(received);
    }

    @SuppressWarnings("unchecked")
    private List<WhseStorageAccrual> savedUnits() {
        ArgumentCaptor<List<WhseStorageAccrual>> c = ArgumentCaptor.forClass(List.class);
        verify(accruals).saveAll(c.capture());
        return c.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<WhseLocationAccrual> savedLocations() {
        ArgumentCaptor<List<WhseLocationAccrual>> c = ArgumentCaptor.forClass(List.class);
        verify(locationAccruals).saveAll(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("stock received inside the free window is not charged; older stock still is")
    void partlyFree() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory p = position(UUID.randomUUID(), 100, rows);
        ledger(rows, List.of(p), 40);                       // 100 held, 40 of it arrived inside the window

        service(14, "0").accrueForTenant(tenant, day);

        WhseStorageAccrual a = savedUnits().get(0);
        assertEquals(0, new BigDecimal("40").compareTo(a.getFreeQty()));
        assertEquals(0, new BigDecimal("6.0000").compareTo(a.getAmount()));   // (100 - 40) x 3 / 30
        assertEquals(0, new BigDecimal("100").compareTo(a.getQtyOnHand()), "the full quantity held is still recorded");
    }

    @Test
    @DisplayName("everything received inside the window is free: amount is zero")
    void allFree() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory p = position(UUID.randomUUID(), 30, rows);
        ledger(rows, List.of(p), 500);

        service(7, "0").accrueForTenant(tenant, day);

        assertEquals(0, new BigDecimal("30").compareTo(savedUnits().get(0).getFreeQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(savedUnits().get(0).getAmount()));
    }

    @Test
    @DisplayName("with no free days nothing is free and the receipts are not even queried")
    void noFreeDays() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory p = position(UUID.randomUUID(), 30, rows);
        ledger(rows, List.of(p), 500);

        service(0, "0").accrueForTenant(tenant, day);

        assertEquals(0, BigDecimal.ZERO.compareTo(savedUnits().get(0).getFreeQty()));
        verify(accruals, never()).receivedBetween(any(), any(), any());
    }

    @Test
    @DisplayName("free stock is shared out over a lot's positions without ever exceeding what was received")
    void sharedAcrossPositions() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory a = position(UUID.randomUUID(), 30, rows);
        WhseInventory b = position(UUID.randomUUID(), 30, rows);
        ledger(rows, List.of(a, b), 40);

        service(14, "0").accrueForTenant(tenant, day);

        BigDecimal free = savedUnits().stream().map(WhseStorageAccrual::getFreeQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("40").compareTo(free));
    }

    @Test
    @DisplayName("a fixed fee is accrued once per occupied position, at fee / 30 a day")
    void locationFeePerPosition() {
        List<Object[]> rows = new ArrayList<>();
        UUID bin1 = UUID.randomUUID(), bin2 = UUID.randomUUID();
        WhseInventory a = position(bin1, 10, rows);
        WhseInventory b = position(bin1, 5, rows);         // second item in the same bin: still one bin
        WhseInventory c = position(bin2, 8, rows);
        ledger(rows, List.of(a, b, c), 0);

        int written = service(0, "300").accrueForTenant(tenant, day);

        List<WhseLocationAccrual> fees = savedLocations();
        assertEquals(2, fees.size());
        assertEquals(5, written, "three unit rows plus two position rows");
        fees.forEach(f -> assertEquals(0, new BigDecimal("10.0000").compareTo(f.getAmount())));
        assertTrue(fees.stream().noneMatch(WhseLocationAccrual::isFreeDaysWaived));
    }

    @Test
    @DisplayName("the fixed fee is waived on a day when everything in the position is inside its free days")
    void feeWaivedWhileAllFree() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory a = position(UUID.randomUUID(), 20, rows);
        ledger(rows, List.of(a), 20);

        service(10, "300").accrueForTenant(tenant, day);

        WhseLocationAccrual f = savedLocations().get(0);
        assertTrue(f.isFreeDaysWaived());
        assertEquals(0, BigDecimal.ZERO.compareTo(f.getAmount()));
        assertEquals(0, new BigDecimal("300").compareTo(f.getMonthlyFee()), "the fee that would have applied is still recorded");
    }

    @Test
    @DisplayName("the fee is charged in full when any stock in the position is past its free days")
    void feeChargedWhenAnyStockIsOld() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory a = position(UUID.randomUUID(), 20, rows);
        ledger(rows, List.of(a), 5);

        service(10, "300").accrueForTenant(tenant, day);

        assertFalse(savedLocations().get(0).isFreeDaysWaived());
        assertEquals(0, new BigDecimal("10.0000").compareTo(savedLocations().get(0).getAmount()));
    }

    @Test
    @DisplayName("a position already accrued for the day is not accrued twice")
    void locationFeeIdempotent() {
        List<Object[]> rows = new ArrayList<>();
        WhseInventory a = position(UUID.randomUUID(), 20, rows);
        ledger(rows, List.of(a), 0);
        when(locationAccruals.findAccruedKeys(tenant, day)).thenReturn(
                java.util.Collections.singletonList(new Object[]{client.getId(), a.getLocationId()}));

        service(0, "300").accrueForTenant(tenant, day);

        assertTrue(savedLocations().isEmpty());
    }

    @Test
    @DisplayName("an accrual cannot be given more free stock than was held")
    void freeCannotExceedHeld() {
        WhseInventory inv = WhseInventory.create(tenant, client.getId(), item.getId(), UUID.randomUUID());
        WhseStorageAccrual a = WhseStorageAccrual.of(inv, day, new BigDecimal("10"), new BigDecimal("3"), "PROFILE");
        assertThrows(IllegalArgumentException.class, () -> a.withFreeQty(new BigDecimal("11")));
        assertThrows(IllegalArgumentException.class, () -> a.withFreeQty(new BigDecimal("-1")));
    }
}
