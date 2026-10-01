package za.co.handyflow.platform.agriculture.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.agriculture.domain.model.*;
import za.co.handyflow.platform.agriculture.domain.repository.*;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.AllocateSaleRequest;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SaleLineResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SaleShare;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesAllocationResponse;
import za.co.handyflow.platform.agriculture.dto.SalesDtos.SalesTotalsResponse;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.SaleLine;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgSalesAllocationServiceTest {

    @Mock AgSalesAllocationRepository allocationRepository;
    @Mock AgFarmRepository farmRepository;
    @Mock AgCropCycleRepository cropCycleRepository;
    @Mock AgGroupRepository groupRepository;
    @Mock AgAnimalRepository animalRepository;
    @Mock AgEnterpriseRepository enterpriseRepository;
    @Mock InvoicingFacade invoicingFacade;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID farmId = UUID.randomUUID();
    final UUID otherFarm = UUID.randomUUID();
    final UUID user = UUID.randomUUID();
    final UUID invoiceId = UUID.randomUUID();
    final UUID lineId = UUID.randomUUID();
    final UUID groupA = UUID.randomUUID();
    final UUID groupB = UUID.randomUUID();
    final UUID groupC = UUID.randomUUID();
    /** What the repository "holds": saveAll adds to it and the revenue queries read from it. */
    final List<AgSalesAllocation> stored = new ArrayList<>();

    private AgSalesAllocationService service() {
        return new AgSalesAllocationService(allocationRepository, farmRepository,
                new AgTargetOwnership(cropCycleRepository, groupRepository, animalRepository, enterpriseRepository), invoicingFacade);
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private SaleLine line(String status, String qty, String lineTotal, String invoiceSubtotal, String credited) {
        return new SaleLine(invoiceId, "INV-0042", status, null, "ABC Foods", Instant.parse("2026-09-18T08:00:00Z"), "ZAR", lineId, "Broiler chicken", "kg",
                new BigDecimal(qty), new BigDecimal("42"), new BigDecimal(lineTotal), new BigDecimal(invoiceSubtotal), new BigDecimal(credited));
    }

    private void invoiceLine(SaleLine l) {
        when(invoicingFacade.findSaleLine(eq(TENANT), eq(lineId))).thenReturn(Optional.of(l));
        when(invoicingFacade.findSaleLines(eq(TENANT), any())).thenReturn(List.of(l));
    }

    private void farmExists() {
        when(farmRepository.findActiveById(eq(TENANT), eq(farmId))).thenReturn(Optional.of(mock(AgFarm.class)));
    }

    private void groupOn(UUID group, UUID owner) {
        AgGroup g = mock(AgGroup.class);
        when(g.getFarmId()).thenReturn(owner);
        when(groupRepository.findActiveById(eq(TENANT), eq(group))).thenReturn(Optional.of(g));
    }

    @SuppressWarnings("unchecked")
    private void inMemoryRepository() {
        when(allocationRepository.saveAll(any())).thenAnswer(inv -> {
            List<AgSalesAllocation> l = (List<AgSalesAllocation>) inv.getArgument(0);
            stored.addAll(l);
            return l;
        });
        when(allocationRepository.findActiveByInvoiceLines(eq(TENANT), any())).thenAnswer(inv -> stored.stream().filter(a -> "ACTIVE".equals(a.getStatus())).toList());
        when(allocationRepository.sumActiveQuantityByInvoiceLine(eq(TENANT), any())).thenAnswer(inv -> {
            BigDecimal sum = BigDecimal.ZERO;
            for (AgSalesAllocation a : stored) if ("ACTIVE".equals(a.getStatus())) sum = sum.add(a.getQuantity());
            return sum.signum() == 0 ? List.<Object[]>of() : List.<Object[]>of(new Object[] {lineId, sum});
        });
    }

    private AllocateSaleRequest request(SaleShare... shares) {
        return new AllocateSaleRequest(lineId, null, "to ABC Foods", List.of(shares));
    }

    private static SaleShare share(UUID group, String qty) { return new SaleShare("GROUP", group, new BigDecimal(qty), null); }

    @Test
    @DisplayName("allocating a whole invoice line to a group attributes the line's ex-VAT revenue to it")
    void allocatesWholeLine() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId);
        invoiceLine(line("ISSUED", "2100", "88200.00", "88200.00", "0"));

        List<SalesAllocationResponse> rows = service().allocate(TENANT, farmId, user, request(share(groupA, "2100")));

        assertEquals(1, rows.size());
        assertNumber("88200.00", rows.get(0).revenue());
        assertTrue(rows.get(0).counted());
        assertEquals("ISSUED", rows.get(0).invoiceStatus());
        assertEquals("ABC Foods", rows.get(0).customerName());
        assertEquals(LocalDate.of(2026, 9, 18), rows.get(0).soldOn());              // defaults to the invoice's issue date
        assertEquals("kg", rows.get(0).unit());
        assertEquals("INV-0042", rows.get(0).invoiceNumber());
        verify(allocationRepository).saveAll(any());
    }

    @Test
    @DisplayName("a line shared across targets splits its revenue exactly to the cent")
    void splitsRevenueExactly() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId); groupOn(groupB, farmId); groupOn(groupC, farmId);
        invoiceLine(line("PAID", "3", "100.00", "100.00", "0"));

        List<SalesAllocationResponse> rows = service().allocate(TENANT, farmId, user, request(share(groupA, "1"), share(groupB, "1"), share(groupC, "1")));

        assertNumber("33.34", rows.get(0).revenue());
        assertNumber("33.33", rows.get(1).revenue());
        assertNumber("33.33", rows.get(2).revenue());
        assertNumber("100.00", rows.get(0).revenue().add(rows.get(1).revenue()).add(rows.get(2).revenue()));
    }

    @Test
    @DisplayName("credit notes reduce revenue in proportion: a quarter of the invoice credited leaves three quarters of the line")
    void creditNotesReduceRevenue() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId);
        invoiceLine(line("PARTIALLY_PAID", "10", "1000.00", "2000.00", "500.00"));

        List<SalesAllocationResponse> rows = service().allocate(TENANT, farmId, user, request(share(groupA, "10")));

        assertNumber("750.00", rows.get(0).revenue());
    }

    @Test
    @DisplayName("only issued invoices count: a draft or cancelled invoice cannot be allocated")
    void refusesNonRevenueInvoices() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId);
        for (String status : List.of("DRAFT", "CANCELLED")) {
            invoiceLine(line(status, "10", "1000", "1000", "0"));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupA, "10"))));
            assertTrue(ex.getMessage().contains(status), ex.getMessage());
        }
        verify(allocationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("an allocation cannot take more than is left on the line, counting earlier allocations")
    void refusesOverAllocation() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId); groupOn(groupB, farmId);
        invoiceLine(line("ISSUED", "2100", "88200.00", "88200.00", "0"));
        service().allocate(TENANT, farmId, user, request(share(groupA, "1500")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupB, "700"))));

        assertTrue(ex.getMessage().contains("only 600 is left"), ex.getMessage());
        assertEquals(1, stored.size());
        assertDoesNotThrow(() -> service().allocate(TENANT, farmId, user, request(share(groupB, "600"))));
    }

    @Test
    @DisplayName("the quantities of one request are checked together, not one by one")
    void checksTheRequestTotal() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId); groupOn(groupB, farmId);
        invoiceLine(line("ISSUED", "10", "100", "100", "0"));

        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupA, "6"), share(groupB, "6"))));
        assertEquals(0, stored.size());
    }

    @Test
    @DisplayName("a target on another farm, an unknown target and a repeated target are all refused, saving nothing")
    void refusesBadTargets() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId); groupOn(groupB, otherFarm);
        invoiceLine(line("ISSUED", "10", "100", "100", "0"));

        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupB, "5"))));
        assertThrows(ResourceNotFoundException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupC, "5"))));
        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupA, "2"), share(groupA, "3"))));
        verify(allocationRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("an unknown invoice line is a 404, a future sale date is refused, and a request with no targets is refused")
    void otherRefusals() {
        farmExists(); inMemoryRepository(); groupOn(groupA, farmId);
        assertThrows(ResourceNotFoundException.class, () -> service().allocate(TENANT, farmId, user, request(share(groupA, "1"))));

        invoiceLine(line("ISSUED", "10", "100", "100", "0"));
        AllocateSaleRequest future = new AllocateSaleRequest(lineId, LocalDate.now().plusDays(2), null, List.of(share(groupA, "1")));
        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, future));
        assertThrows(IllegalArgumentException.class, () -> service().allocate(TENANT, farmId, user, new AllocateSaleRequest(lineId, null, null, List.of())));
        assertThrows(ResourceNotFoundException.class, () -> service().allocate(TENANT, UUID.randomUUID(), user, request(share(groupA, "1"))));
        verify(allocationRepository, never()).saveAll(any());
    }

    private AgSalesAllocation existing(UUID group, String qty) {
        return AgSalesAllocation.create(TENANT, farmId, invoiceId, lineId, "INV-0042", "Broiler chicken", "GROUP", group, new BigDecimal(qty), "kg", null, LocalDate.of(2026, 9, 18), null, user);
    }

    @Test
    @DisplayName("an allocation on an invoice that was cancelled adds nothing to revenue and says why")
    void cancelledInvoiceAddsNothing() {
        farmExists();
        AgSalesAllocation a = existing(groupA, "10");
        stored.add(a);
        when(allocationRepository.findActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<AgSalesAllocation>(List.of(a)));
        when(allocationRepository.findActiveByInvoiceLines(eq(TENANT), any())).thenReturn(List.of(a));
        invoiceLine(line("CANCELLED", "10", "1000", "1000", "0"));

        SalesTotalsResponse totals = service().totals(TENANT, farmId, null, null);
        SalesAllocationResponse row = service().list(TENANT, farmId, null, null, PageRequest.of(0, 50)).getContent().get(0);

        assertNumber("0", totals.revenue());
        assertEquals(1, totals.notCountedCount());
        assertEquals(1, totals.allocationCount());
        assertFalse(row.counted());
        assertTrue(row.notCountedReason().contains("CANCELLED"), row.notCountedReason());
        assertNumber("0", row.revenue());
    }

    @Test
    @DisplayName("totals add up the live revenue, broken down by target, biggest first")
    void totalsByTarget() {
        farmExists();
        AgSalesAllocation small = existing(groupA, "2");
        AgSalesAllocation big = existing(groupB, "8");
        when(allocationRepository.findActiveForFarm(eq(TENANT), eq(farmId), any())).thenReturn(new PageImpl<AgSalesAllocation>(List.of(small, big)));
        when(allocationRepository.findActiveByInvoiceLines(eq(TENANT), any())).thenReturn(List.of(small, big));
        invoiceLine(line("PAID", "10", "1000.00", "1000.00", "0"));

        SalesTotalsResponse totals = service().totals(TENANT, farmId, null, null);

        assertNumber("1000.00", totals.revenue());
        assertEquals(2, totals.byTarget().size());
        assertEquals(groupB, totals.byTarget().get(0).targetId());
        assertNumber("800.00", totals.byTarget().get(0).revenue());
        assertNumber("8", totals.byTarget().get(0).quantity());
        assertNumber("200.00", totals.byTarget().get(1).revenue());
        assertEquals(0, totals.notCountedCount());
    }

    @Test
    @DisplayName("a target filter needs both type and id, and a known type")
    void targetFilterIsValidated() {
        farmExists();
        assertThrows(IllegalArgumentException.class, () -> service().totals(TENANT, farmId, "GROUP", null));
        assertThrows(IllegalArgumentException.class, () -> service().list(TENANT, farmId, "TRACTOR", groupA, PageRequest.of(0, 50)));
    }

    @Test
    @DisplayName("the picker shows how much of each line is already allocated and what is left, and filters by text")
    void searchLines() {
        farmExists();
        SaleLine l = line("ISSUED", "2100", "88200.00", "88200.00", "0");
        when(invoicingFacade.searchSaleLines(eq(TENANT), any(), any(), any(), anyInt())).thenReturn(List.of(l));
        when(allocationRepository.sumActiveQuantityByInvoiceLine(eq(TENANT), any())).thenReturn(List.<Object[]>of(new Object[] {lineId, new BigDecimal("1500")}));

        List<SaleLineResponse> all = service().searchLines(TENANT, farmId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null);
        assertEquals(1, all.size());
        assertNumber("1500", all.get(0).allocatedQuantity());
        assertNumber("600", all.get(0).remainingQuantity());
        assertNumber("88200.00", all.get(0).netRevenue());
        assertEquals(LocalDate.of(2026, 9, 18), all.get(0).issuedOn());

        assertEquals(1, service().searchLines(TENANT, farmId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "abc foods").size());
        assertEquals(1, service().searchLines(TENANT, farmId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "inv-0042").size());
        assertEquals(0, service().searchLines(TENANT, farmId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "no such thing").size());
    }

    @Test
    @DisplayName("the picker's date range must be in order and at most a year")
    void searchRange() {
        farmExists();
        assertThrows(IllegalArgumentException.class, () -> service().searchLines(TENANT, farmId, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null));
        assertThrows(IllegalArgumentException.class, () -> service().searchLines(TENANT, farmId, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 1), null));
        assertThrows(ResourceNotFoundException.class, () -> service().searchLines(TENANT, UUID.randomUUID(), null, null, null));
    }

    @Test
    @DisplayName("removing an allocation takes it out of revenue; an unknown one is a 404; it cannot be removed twice")
    void remove() {
        AgSalesAllocation a = existing(groupA, "10");
        when(allocationRepository.findForTenantById(eq(TENANT), eq(a.getId()))).thenReturn(Optional.of(a));

        service().remove(TENANT, a.getId(), user);

        assertEquals("REMOVED", a.getStatus());
        verify(allocationRepository).save(any(AgSalesAllocation.class));
        assertThrows(IllegalStateException.class, () -> service().remove(TENANT, a.getId(), user));
        assertThrows(ResourceNotFoundException.class, () -> service().remove(TENANT, UUID.randomUUID(), user));
    }
}
