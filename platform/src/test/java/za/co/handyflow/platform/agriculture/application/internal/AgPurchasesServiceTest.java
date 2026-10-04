package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgStockMovementRepository;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierOption;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpend;
import za.co.handyflow.platform.agriculture.dto.PurchasesDtos.SupplierSpendResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.SupplierFacade;
import za.co.handyflow.platform.supplychain.application.SupplierFacade.SupplierSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgPurchasesServiceTest {

    @Mock SupplierFacade supplierFacade;
    @Mock AgFarmRepository farmRepository;
    @Mock AgStockMovementRepository stockMovementRepository;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final LocalDate TO = LocalDate.of(2026, 9, 30);
    final UUID farmId = UUID.randomUUID(), agri = UUID.randomUUID(), feed = UUID.randomUUID();

    private AgPurchasesService service() { return new AgPurchasesService(supplierFacade, farmRepository, stockMovementRepository); }
    private static BigDecimal bd(String s) { return new BigDecimal(s); }
    private void farmExists() { when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class))); }
    private static Object[] dbRow(UUID supplier, long receipts, String spend, Long withoutCost) { return new Object[] {supplier, receipts, bd(spend), withoutCost}; }

    @Test
    @DisplayName("the picker lists active suppliers by id and name only")
    void suppliersPicker() {
        when(supplierFacade.listActive(eq(TENANT), anyInt())).thenReturn(List.of(new SupplierSummary(agri, "AgriSupplies", "ACTIVE")));

        List<SupplierOption> out = service().suppliers(TENANT);

        assertEquals(List.of(new SupplierOption(agri, "AgriSupplies")), out);
    }

    @Test
    @DisplayName("spend is read from the database rows and named in one lookup of only the suppliers that appear")
    void spendBySupplier() {
        farmExists();
        when(stockMovementRepository.receiptSpendBySupplier(eq(TENANT), eq(farmId), any(), any())).thenReturn(List.of(dbRow(agri, 3, "1500.50", 0L), dbRow(feed, 2, "500.25", 1L), dbRow(null, 1, "10", 1L)));
        when(supplierFacade.findAll(eq(TENANT), any())).thenReturn(Map.of(agri, new SupplierSummary(agri, "AgriSupplies", "ACTIVE"), feed, new SupplierSummary(feed, "Feed Co", "INACTIVE")));

        SupplierSpendResponse r = service().bySupplier(TENANT, farmId, LocalDate.of(2026, 1, 1), TO);

        assertEquals(List.of("AgriSupplies", "Feed Co", "No supplier recorded"), r.suppliers().stream().map(SupplierSpend::supplierName).toList());
        assertEquals(0, bd("2010.75").compareTo(r.totalSpend())); assertEquals(6, r.receipts()); assertEquals(2, r.receiptsWithoutCost());
        verify(supplierFacade, times(1)).findAll(eq(TENANT), eq(Set.of(agri, feed)));
    }

    @Test
    @DisplayName("no receipts means no supplier lookup")
    void noReceipts() {
        farmExists();
        when(stockMovementRepository.receiptSpendBySupplier(any(), any(), any(), any())).thenReturn(List.of());

        SupplierSpendResponse r = service().bySupplier(TENANT, farmId, LocalDate.of(2026, 1, 1), TO);

        assertTrue(r.suppliers().isEmpty());
        verify(supplierFacade, never()).findAll(any(), any());
    }

    @Test
    @DisplayName("a receipt group with no supplier needs no lookup either, and a null no-cost count is zero")
    void onlyUnsupplied() {
        farmExists();
        when(stockMovementRepository.receiptSpendBySupplier(any(), any(), any(), any())).thenReturn(List.<Object[]>of(new Object[] {null, 2L, bd("40"), null}));      // one array argument must be typed, or List.of reads it as varargs

        SupplierSpendResponse r = service().bySupplier(TENANT, farmId, LocalDate.of(2026, 1, 1), TO);

        assertEquals("No supplier recorded", r.suppliers().get(0).supplierName()); assertEquals(0, r.receiptsWithoutCost());
        verify(supplierFacade, never()).findAll(any(), any());
    }

    @Test
    @DisplayName("the dates asked for are the dates searched; with none given it is the 365 days up to the end date")
    void datesPassedThrough() {
        farmExists();
        when(stockMovementRepository.receiptSpendBySupplier(any(), any(), any(), any())).thenReturn(List.of());

        service().bySupplier(TENANT, farmId, LocalDate.of(2026, 3, 1), TO);
        verify(stockMovementRepository).receiptSpendBySupplier(eq(TENANT), eq(farmId), eq(LocalDate.of(2026, 3, 1)), eq(TO));

        service().bySupplier(TENANT, farmId, null, TO);
        verify(stockMovementRepository).receiptSpendBySupplier(eq(TENANT), eq(farmId), eq(TO.minusDays(365)), eq(TO));
    }

    @Test
    @DisplayName("a bad range is refused before anything is read")
    void badRange() {
        farmExists();

        assertThrows(IllegalArgumentException.class, () -> service().bySupplier(TENANT, farmId, TO, LocalDate.of(2026, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> service().bySupplier(TENANT, farmId, LocalDate.of(2020, 1, 1), TO));

        verifyNoInteractions(stockMovementRepository, supplierFacade);
    }

    @Test
    @DisplayName("an unknown farm is a 404 and nothing else is read")
    void unknownFarm() {
        assertThrows(ResourceNotFoundException.class, () -> service().bySupplier(TENANT, UUID.randomUUID(), null, TO));
        verifyNoInteractions(stockMovementRepository, supplierFacade);
    }
}
