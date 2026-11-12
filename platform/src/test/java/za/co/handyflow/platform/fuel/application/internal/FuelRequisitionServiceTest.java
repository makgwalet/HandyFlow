package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseRequisition;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.fuel.domain.repository.FuelPurchaseOrderRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPurchaseRequisitionRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderRequest;
import za.co.handyflow.platform.fuel.dto.PurchaseOrderResponse;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.ConvertRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionRequest;
import za.co.handyflow.platform.fuel.dto.RequisitionDtos.RequisitionView;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelRequisitionServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID ALICE = UUID.randomUUID(), SUP = UUID.randomUUID(), SUP2 = UUID.randomUUID();

    @Mock FuelPurchaseRequisitionRepository requisitions;
    @Mock FuelSupplierRepository suppliers;
    @Mock FuelPurchaseOrderRepository orders;
    @Mock FuelPurchasingService purchasing;
    @Mock RequisitionNumberGenerator numbers;
    @Mock ApprovalFacade approvals;
    FuelRequisitionService service;

    @BeforeEach
    void setUp() {
        service = new FuelRequisitionService(requisitions, suppliers, orders, purchasing, numbers, approvals);
        when(numbers.generate(any(TenantId.class))).thenReturn("REQ-2026-00001");
        when(requisitions.save(any())).thenAnswer(i -> i.getArgument(0));
        FuelSupplier ok = mock(FuelSupplier.class); when(ok.isApproved()).thenReturn(true); when(ok.getName()).thenReturn("Acme");
        when(suppliers.findActiveById(any(TenantId.class), any(UUID.class))).thenReturn(Optional.of(ok));
        when(suppliers.findAllActive(any(TenantId.class))).thenReturn(List.of());
        when(orders.findAllActive(any(TenantId.class))).thenReturn(List.of());
    }

    private static RequisitionRequest req(String price) {
        return new RequisitionRequest("DIESEL", new BigDecimal("10000"), null, SUP, price == null ? null : new BigDecimal(price), null, "Tank 2 is below reorder level");
    }

    private FuelPurchaseRequisition stored(RequisitionRequest r) {
        FuelPurchaseRequisition x = FuelPurchaseRequisition.create(T, "REQ-2026-00001", r.fuelType(), r.litres(), r.tankId(), r.supplierId(), r.estimatedPrice(), r.neededBy(), r.reason(), ALICE);
        when(requisitions.findByIdForUpdate(any(TenantId.class), eq(x.getId()))).thenReturn(Optional.of(x));
        return x;
    }

    private static ApprovalRequestResponse resp(String status) {
        return new ApprovalRequestResponse(UUID.randomUUID(), "fuel", "REQUISITION", UUID.randomUUID(), status, null, ALICE, null, null, null, null);
    }

    @Test @DisplayName("a requisition starts as a draft with a number, and shows its estimated value")
    void createDraft() {
        RequisitionView v = service.create(T, req("21.50"), ALICE);
        assertEquals("DRAFT", v.status()); assertEquals("REQ-2026-00001", v.reqNumber()); assertEquals(new BigDecimal("215000.00"), v.estimatedValue());
    }

    @Test @DisplayName("fuel type, litres and a reason are required; a supplier that is not approved is refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(T, new RequisitionRequest(" ", BigDecimal.TEN, null, null, null, null, "Needed for the week"), ALICE));
        assertThrows(IllegalArgumentException.class, () -> service.create(T, new RequisitionRequest("DIESEL", BigDecimal.ZERO, null, null, null, null, "Needed for the week"), ALICE));
        assertThrows(IllegalArgumentException.class, () -> service.create(T, new RequisitionRequest("DIESEL", BigDecimal.TEN, null, null, null, null, "no"), ALICE));
        FuelSupplier pending = mock(FuelSupplier.class); when(pending.isApproved()).thenReturn(false); when(pending.getName()).thenReturn("New Co");
        when(suppliers.findActiveById(any(TenantId.class), eq(SUP2))).thenReturn(Optional.of(pending));
        assertThrows(IllegalStateException.class, () -> service.create(T, new RequisitionRequest("DIESEL", BigDecimal.TEN, null, SUP2, null, null, "Needed for the week"), ALICE));
    }

    @Test @DisplayName("submitting needs an estimated price so approval can judge the value, and nothing is sent without one")
    void submitNeedsPrice() {
        FuelPurchaseRequisition r = stored(req(null));
        assertThrows(IllegalStateException.class, () -> service.submit(T, r.getId(), ALICE));
        verify(approvals, never()).submit(any(), any(), any(), any(), any(), anyMap());
    }

    @Test @DisplayName("with no approval rule the requisition is approved straight away; the engine is told the value, litres and fuel type")
    void submitAutoApproved() {
        FuelPurchaseRequisition r = stored(req("20"));
        when(approvals.submit(any(TenantId.class), eq("fuel"), eq("REQUISITION"), eq(r.getId()), eq(ALICE), anyMap())).thenReturn(resp("APPROVED"));
        assertEquals("APPROVED", service.submit(T, r.getId(), ALICE).status());
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(approvals).submit(any(TenantId.class), eq("fuel"), eq("REQUISITION"), eq(r.getId()), eq(ALICE), meta.capture());
        assertEquals(200000.0, meta.getValue().get("totalAmount")); assertEquals("DIESEL", meta.getValue().get("fuelType"));
    }

    @Test @DisplayName("with a rule it waits for approval and cannot be edited or submitted again meanwhile")
    void submitWaits() {
        FuelPurchaseRequisition r = stored(req("20"));
        when(approvals.submit(any(TenantId.class), any(), any(), any(UUID.class), any(UUID.class), anyMap())).thenReturn(resp("PENDING"));
        assertEquals("PENDING_APPROVAL", service.submit(T, r.getId(), ALICE).status());
        assertThrows(IllegalStateException.class, () -> service.update(T, r.getId(), req("25")));
        assertThrows(IllegalStateException.class, () -> service.submit(T, r.getId(), ALICE));
    }

    @Test @DisplayName("the engine's outcome approves or sends it back to draft with the reason; a repeat or a late outcome changes nothing")
    void outcomes() {
        FuelPurchaseRequisition a = stored(req("20")); a.submitForApproval(UUID.randomUUID());
        service.applyApprovalOutcome(T, a.getId(), "APPROVED"); assertEquals("APPROVED", a.getStatus());
        service.applyApprovalOutcome(T, a.getId(), "REJECTED"); assertEquals("APPROVED", a.getStatus());
        FuelPurchaseRequisition b = stored(req("20")); b.submitForApproval(UUID.randomUUID());
        service.applyApprovalOutcome(T, b.getId(), "REJECTED"); assertEquals("DRAFT", b.getStatus()); assertNotNull(b.getApprovalNote());
        service.applyApprovalOutcome(T, UUID.randomUUID(), "APPROVED");   // unknown id: nothing happens
    }

    @Test @DisplayName("an approved requisition becomes one draft purchase order, using its own supplier and estimated price when none are given")
    void convert() {
        FuelPurchaseRequisition r = stored(req("20")); r.approveDirectly();
        UUID poId = UUID.randomUUID();
        PurchaseOrderResponse po = new PurchaseOrderResponse(poId, "PO-2026-00007", SUP, "Acme", "DIESEL", new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"), new BigDecimal("20"), new BigDecimal("200000"), "DRAFT", null, false, null, null, null, null);
        when(purchasing.create(any(TenantId.class), any(PurchaseOrderRequest.class), eq(ALICE))).thenReturn(po);
        RequisitionView v = service.convert(T, r.getId(), null, ALICE);
        assertEquals("CONVERTED", v.status()); assertEquals(poId, v.purchaseOrderId());
        ArgumentCaptor<PurchaseOrderRequest> cap = ArgumentCaptor.forClass(PurchaseOrderRequest.class);
        verify(purchasing).create(any(TenantId.class), cap.capture(), eq(ALICE));
        assertEquals(SUP, cap.getValue().supplierId()); assertEquals(new BigDecimal("20"), cap.getValue().pricePerLitre()); assertTrue(cap.getValue().notes().contains("REQ-2026-00001"));
        assertThrows(IllegalStateException.class, () -> service.convert(T, r.getId(), null, ALICE));   // a second click makes no second order
        verify(purchasing, times(1)).create(any(), any(), any());
    }

    @Test @DisplayName("what is given at conversion wins; a supplier and a price must exist one way or the other")
    void convertOverridesAndNeeds() {
        FuelPurchaseRequisition r = stored(new RequisitionRequest("DIESEL", BigDecimal.TEN, null, null, null, null, "Needed for the week")); r.approveDirectly();
        assertThrows(IllegalArgumentException.class, () -> service.convert(T, r.getId(), null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> service.convert(T, r.getId(), new ConvertRequest(SUP2, null, null), ALICE));
        verify(purchasing, never()).create(any(), any(), any());
        PurchaseOrderResponse po = new PurchaseOrderResponse(UUID.randomUUID(), "PO-1", SUP2, "B", "DIESEL", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("19"), new BigDecimal("190"), "DRAFT", null, false, null, null, null, null);
        when(purchasing.create(any(TenantId.class), any(PurchaseOrderRequest.class), any())).thenReturn(po);
        service.convert(T, r.getId(), new ConvertRequest(SUP2, new BigDecimal("19"), null), ALICE);
        ArgumentCaptor<PurchaseOrderRequest> cap = ArgumentCaptor.forClass(PurchaseOrderRequest.class);
        verify(purchasing).create(any(TenantId.class), cap.capture(), any());
        assertEquals(SUP2, cap.getValue().supplierId()); assertEquals(new BigDecimal("19"), cap.getValue().pricePerLitre());
    }

    @Test @DisplayName("only an approved requisition converts; a refused purchase order leaves it approved")
    void convertOnlyApproved() {
        FuelPurchaseRequisition draft = stored(req("20"));
        assertThrows(IllegalStateException.class, () -> service.convert(T, draft.getId(), null, ALICE));
        FuelPurchaseRequisition r = stored(req("20")); r.approveDirectly();
        when(purchasing.create(any(TenantId.class), any(PurchaseOrderRequest.class), any())).thenThrow(new IllegalStateException("Acme is suspended"));
        assertThrows(IllegalStateException.class, () -> service.convert(T, r.getId(), null, ALICE));
        assertEquals("APPROVED", r.getStatus()); assertNull(r.getPurchaseOrderId());
    }

    @Test @DisplayName("cancelling needs a reason and works until it is converted")
    void cancel() {
        FuelPurchaseRequisition r = stored(req("20"));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(T, r.getId(), "no"));
        assertEquals("CANCELLED", service.cancel(T, r.getId(), "Stock arrived early").status());
        assertThrows(IllegalStateException.class, () -> service.cancel(T, r.getId(), "Again please"));
        FuelPurchaseRequisition c = stored(req("20")); c.approveDirectly(); c.convertedTo(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> service.cancel(T, c.getId(), "Too late now"));
    }

    @Test @DisplayName("an unknown requisition is not found")
    void notFound() {
        UUID none = UUID.randomUUID();
        when(requisitions.findByIdForUpdate(any(TenantId.class), eq(none))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.submit(T, none, ALICE));
    }
}
