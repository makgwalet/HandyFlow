package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.agriculture.domain.model.AgInventoryItem;
import za.co.handyflow.platform.agriculture.domain.model.AgStockMovement;
import za.co.handyflow.platform.agriculture.domain.repository.AgInventoryItemRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.InventoryItemResponse;
import za.co.handyflow.platform.agriculture.dto.ReceiveInventoryRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.SupplierFacade;
import za.co.handyflow.platform.supplychain.application.SupplierFacade.SupplierSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgInventoryItemServiceSupplierTest {

    @Mock AgInventoryItemRepository itemRepository;
    @Mock AgStockMovementRepository movementRepository;
    @Mock HrFacade hrFacade;
    @Mock SupplierFacade supplierFacade;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID(), agri = UUID.randomUUID(), other = UUID.randomUUID();
    final AgStockMovement[] saved = new AgStockMovement[1];

    private AgInventoryItemService service() { return new AgInventoryItemService(itemRepository, movementRepository, hrFacade, supplierFacade); }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private AgInventoryItem item() {
        AgInventoryItem i = AgInventoryItem.create(TENANT, farmId, "Maize seed", "FEED", "kg", bd("10"), bd("12.50"), "typed supplier text");
        when(itemRepository.findActiveById(eq(TENANT), eq(i.getId()))).thenReturn(Optional.of(i));
        when(movementRepository.save(any())).thenAnswer(inv -> { saved[0] = inv.getArgument(0); return inv.getArgument(0); });
        return i;
    }
    private void supplier(UUID id, String name, String status) { when(supplierFacade.find(eq(TENANT), eq(id))).thenReturn(Optional.of(new SupplierSummary(id, name, status))); }
    private static ReceiveInventoryRequest receipt(UUID supplier) { return new ReceiveInventoryRequest(bd("50"), bd("13.00"), null, null, supplier); }

    // ---- receiving ----------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a receipt records the supplier it was bought from")
    void receiptRecordsSupplier() {
        AgInventoryItem i = item(); supplier(agri, "AgriSupplies", "ACTIVE");

        service().receive(TENANT, i.getId(), receipt(agri));

        assertEquals("RECEIPT", saved[0].getMovementType());
        assertEquals(agri, saved[0].getSupplierId());
        assertEquals(0, bd("650.00").compareTo(saved[0].getTotalCost()));
    }

    @Test
    @DisplayName("with no supplier given, a receipt takes the item's usual supplier")
    void receiptTakesUsualSupplier() {
        AgInventoryItem i = item(); i.assignSupplier(agri); supplier(agri, "AgriSupplies", "ACTIVE");

        service().receive(TENANT, i.getId(), receipt(null));

        assertEquals(agri, saved[0].getSupplierId());
    }

    @Test
    @DisplayName("a supplier chosen for the receipt wins over the usual one")
    void explicitBeatsUsual() {
        AgInventoryItem i = item(); i.assignSupplier(agri); supplier(other, "Other Co", "ACTIVE");

        service().receive(TENANT, i.getId(), receipt(other));

        assertEquals(other, saved[0].getSupplierId());
        verify(supplierFacade, never()).find(eq(TENANT), eq(agri));
    }

    @Test
    @DisplayName("with no supplier at all, the receipt is recorded with none and Supply Chain is not asked")
    void noSupplier() {
        AgInventoryItem i = item();

        service().receive(TENANT, i.getId(), receipt(null));

        assertNull(saved[0].getSupplierId());
        verifyNoInteractions(supplierFacade);
    }

    @Test
    @DisplayName("nothing is received from a blacklisted supplier: the message names it, and neither stock nor the ledger changes")
    void blacklistedIsRefused() {
        AgInventoryItem i = item(); supplier(agri, "Dodgy Ltd", "BLACKLISTED");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().receive(TENANT, i.getId(), receipt(agri)));

        assertTrue(ex.getMessage().contains("Dodgy Ltd") && ex.getMessage().contains("blacklisted") && ex.getMessage().contains("can't be used for new purchases"), ex.getMessage());
        verify(movementRepository, never()).save(any());
        assertEquals(0, BigDecimal.ZERO.compareTo(i.getCurrentQuantity()));
    }

    @Test
    @DisplayName("an inactive supplier is refused too")
    void inactiveIsRefused() {
        AgInventoryItem i = item(); supplier(agri, "Dormant Ltd", "INACTIVE");

        assertTrue(assertThrows(IllegalArgumentException.class, () -> service().receive(TENANT, i.getId(), receipt(agri))).getMessage().contains("inactive"));
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("a usual supplier that has since been blacklisted is not silently used: the receipt is refused and says it is the usual supplier")
    void inheritedBlacklistedIsRefused() {
        AgInventoryItem i = item(); i.assignSupplier(agri); supplier(agri, "Dodgy Ltd", "BLACKLISTED");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().receive(TENANT, i.getId(), receipt(null)));

        assertTrue(ex.getMessage().contains("usual supplier") && ex.getMessage().contains("clear the usual supplier"), ex.getMessage());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("a supplier that does not exist is refused, and so is a usual supplier that was removed")
    void unknownSupplierIsRefused() {
        AgInventoryItem i = item();

        assertTrue(assertThrows(IllegalArgumentException.class, () -> service().receive(TENANT, i.getId(), receipt(agri))).getMessage().contains("no longer exists"));

        i.assignSupplier(other);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service().receive(TENANT, i.getId(), receipt(null))).getMessage().contains("usual supplier"));
        verify(movementRepository, never()).save(any());
    }

    // ---- the usual supplier -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("setting the usual supplier stores it and returns its name")
    void setsUsualSupplier() {
        AgInventoryItem i = item(); supplier(agri, "AgriSupplies", "ACTIVE");
        when(supplierFacade.findAll(eq(TENANT), any())).thenReturn(Map.of(agri, new SupplierSummary(agri, "AgriSupplies", "ACTIVE")));

        InventoryItemResponse r = service().setUsualSupplier(TENANT, i.getId(), agri);

        assertEquals(agri, i.getSupplierId()); assertEquals(agri, r.supplierId()); assertEquals("AgriSupplies", r.supplierName());
    }

    @Test
    @DisplayName("null clears the usual supplier without asking Supply Chain anything")
    void clearsUsualSupplier() {
        AgInventoryItem i = item(); i.assignSupplier(agri);

        InventoryItemResponse r = service().setUsualSupplier(TENANT, i.getId(), null);

        assertNull(i.getSupplierId()); assertNull(r.supplierId()); assertNull(r.supplierName());
        verifyNoInteractions(supplierFacade);
    }

    @Test
    @DisplayName("a blacklisted or unknown supplier cannot be made the usual supplier, and the item keeps what it had")
    void usualSupplierMustBeUsable() {
        AgInventoryItem i = item(); i.assignSupplier(other); supplier(agri, "Dodgy Ltd", "BLACKLISTED");

        assertThrows(IllegalArgumentException.class, () -> service().setUsualSupplier(TENANT, i.getId(), agri));
        assertThrows(IllegalArgumentException.class, () -> service().setUsualSupplier(TENANT, i.getId(), UUID.randomUUID()));

        assertEquals(other, i.getSupplierId());
    }

    @Test
    @DisplayName("an unknown item is a 404")
    void unknownItem() {
        assertThrows(ResourceNotFoundException.class, () -> service().setUsualSupplier(TENANT, UUID.randomUUID(), null));
    }

    // ---- listing ------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a page of items is named in one lookup of only the suppliers that appear")
    void listNamesInOneLookup() {
        AgInventoryItem a = AgInventoryItem.create(TENANT, farmId, "A", "FEED", "kg", null, null, null); a.assignSupplier(agri);
        AgInventoryItem b = AgInventoryItem.create(TENANT, farmId, "B", "FEED", "kg", null, null, null); b.assignSupplier(agri);
        AgInventoryItem c = AgInventoryItem.create(TENANT, farmId, "C", "FEED", "kg", null, null, null);
        Page<AgInventoryItem> page = new PageImpl<>(List.of(a, b, c));
        when(itemRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(page);
        when(supplierFacade.findAll(eq(TENANT), any())).thenReturn(Map.of(agri, new SupplierSummary(agri, "AgriSupplies", "ACTIVE")));

        List<InventoryItemResponse> out = service().getItemsForFarm(TENANT, farmId, PageRequest.of(0, 20)).getContent();

        assertEquals("AgriSupplies", out.get(0).supplierName()); assertEquals("AgriSupplies", out.get(1).supplierName()); assertNull(out.get(2).supplierName()); assertNull(out.get(2).supplierId());
        verify(supplierFacade, times(1)).findAll(eq(TENANT), eq(Set.of(agri)));
    }

    @Test
    @DisplayName("a page with no suppliers asks Supply Chain nothing")
    void listWithoutSuppliers() {
        AgInventoryItem c = AgInventoryItem.create(TENANT, farmId, "C", "FEED", "kg", null, null, null);
        when(itemRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(c)));

        service().getItemsForFarm(TENANT, farmId, PageRequest.of(0, 20));

        verifyNoInteractions(supplierFacade);
    }

    @Test
    @DisplayName("an item whose supplier was removed keeps its supplier id and has no name")
    void removedSupplierKeepsItsId() {
        AgInventoryItem a = AgInventoryItem.create(TENANT, farmId, "A", "FEED", "kg", null, null, null); a.assignSupplier(agri);
        when(itemRepository.findAllActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<>(List.of(a)));
        when(supplierFacade.findAll(eq(TENANT), any())).thenReturn(Map.of());

        InventoryItemResponse r = service().getItemsForFarm(TENANT, farmId, PageRequest.of(0, 20)).getContent().get(0);

        assertEquals(agri, r.supplierId()); assertNull(r.supplierName());
    }
}
