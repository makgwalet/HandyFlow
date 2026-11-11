package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Stage 1B: deliveries, delivery sites and credit hold. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelServiceCustomerTest {

    @Mock FuelTankRepository tankRepository;
    @Mock FuelSupplierRepository supplierRepository;
    @Mock FuelReceiptRepository receiptRepository;
    @Mock FuelDispatchRepository dispatchRepository;
    @Mock DipReadingRepository dipRepository;
    @Mock FuelDeliveryRepository deliveryRepository;
    @Mock ReceiptNumberGenerator receiptNumberGenerator;
    @Mock za.co.handyflow.platform.notifications.application.internal.NotificationService notificationService;
    @Mock CrmFacade crmFacade;
    @Mock FuelStockLedger ledger;
    @Mock FuelCustomerAccountRepository accountRepository;
    @Mock FuelCustomerSiteRepository siteRepository;
    @Mock FuelOrderFulfilment orderFulfilment;
    @InjectMocks FuelService service;

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    final UUID crmId = UUID.randomUUID();
    final UUID siteId = UUID.randomUUID();
    final UUID accountId = UUID.randomUUID();
    FuelCustomerAccount account;
    FuelCustomerSite site;

    @BeforeEach
    void setUp() {
        FuelTank tank = FuelTank.create(TENANT, "Diesel 1", "DIESEL", new BigDecimal("10000"), "Yard", null);
        when(tankRepository.findActiveById(any(), any())).thenReturn(Optional.of(tank));
        when(crmFacade.customerExists(any(), any())).thenReturn(true);
        account = FuelCustomerAccount.create(TENANT, crmId, "MINING_COMPANY", new BigDecimal("1000"), 30, null, null);
        site = mock(FuelCustomerSite.class);
        when(site.getId()).thenReturn(siteId);
        when(site.getAccountId()).thenReturn(accountId);
        when(siteRepository.findActiveById(TENANT, siteId)).thenReturn(Optional.of(site));
        when(accountRepository.findActiveById(TENANT, accountId)).thenReturn(Optional.of(account));
        when(accountRepository.findByCrmCustomerId(TENANT, crmId)).thenReturn(Optional.of(account));
        when(deliveryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private CreateDeliveryRequest req(UUID customer, UUID site) {
        return new CreateDeliveryRequest(UUID.randomUUID(), customer, Map.of(), "DIESEL", new BigDecimal("500"),
                new BigDecimal("22"), Instant.now(), null, null, site);
    }

    @Test @DisplayName("a site fills in the customer from its account")
    void siteSetsCustomer() {
        DeliveryResponse r = service.scheduleDelivery(TENANT, req(null, siteId));
        assertEquals(crmId, r.customerId());
        assertEquals(siteId, r.siteId());
    }

    @Test @DisplayName("a site that belongs to a different customer is refused")
    void siteOfOtherCustomer() {
        assertThrows(IllegalArgumentException.class, () -> service.scheduleDelivery(TENANT, req(UUID.randomUUID(), siteId)));
        verify(deliveryRepository, never()).save(any());
    }

    @Test @DisplayName("a site from another tenant is not found")
    void siteOfOtherTenant() {
        UUID foreign = UUID.randomUUID();
        when(siteRepository.findActiveById(TENANT, foreign)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.scheduleDelivery(TENANT, req(null, foreign)));
    }

    @Test @DisplayName("no delivery is scheduled for an account on credit hold; releasing the hold allows it")
    void creditHoldBlocksScheduling() {
        account.placeOnHold("Overdue 60 days");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.scheduleDelivery(TENANT, req(crmId, null)));
        assertTrue(e.getMessage().contains("credit hold"));
        verify(deliveryRepository, never()).save(any());
        account.releaseHold();
        assertDoesNotThrow(() -> service.scheduleDelivery(TENANT, req(crmId, null)));
    }

    @Test @DisplayName("a scheduled delivery cannot be dispatched once its customer is put on hold")
    void creditHoldBlocksDispatch() {
        FuelDelivery d = FuelDelivery.create(TENANT, UUID.randomUUID(), crmId, Map.of(), "DIESEL",
                new BigDecimal("500"), new BigDecimal("22"), Instant.now(), null, null);
        when(deliveryRepository.findActiveByIdForUpdate(eq(TENANT), any())).thenReturn(Optional.of(d));
        account.placeOnHold("Overdue 60 days");
        assertThrows(IllegalStateException.class,
                () -> service.dispatchDelivery(TENANT, UUID.randomUUID(), new DispatchDeliveryRequest("Moses", "GP 1")));
        assertEquals("SCHEDULED", d.getStatus());
    }

    @Test @DisplayName("a walk-in delivery with no account is unaffected")
    void noAccountNoHold() {
        when(accountRepository.findByCrmCustomerId(any(), any())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.scheduleDelivery(TENANT, req(UUID.randomUUID(), null)));
    }

    @Test @DisplayName("an invoiced delivery cannot be reversed until its invoice is credited")
    void invoicedDeliveryNotReversible() {
        FuelDelivery d = FuelDelivery.create(TENANT, UUID.randomUUID(), crmId, Map.of(), "DIESEL",
                new BigDecimal("500"), new BigDecimal("22"), Instant.now(), null, null);
        d.complete(new BigDecimal("500"), "P Botha", null, null, null, false, null, null);
        d.markInvoiced(UUID.randomUUID(), "INV-00042");
        when(deliveryRepository.findActiveByIdForUpdate(eq(TENANT), any())).thenReturn(Optional.of(d));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.reverseDelivery(TENANT, UUID.randomUUID(), new ReverseRequest("Wrong customer"), UUID.randomUUID()));
        assertTrue(e.getMessage().contains("INV-00042"));
        verify(tankRepository, never()).save(any());
    }

    @Test @DisplayName("loading is recorded only for loads still on the way, with positive litres")
    void loadingRules() {
        FuelDelivery d = FuelDelivery.create(TENANT, UUID.randomUUID(), crmId, Map.of(), "DIESEL",
                new BigDecimal("500"), new BigDecimal("22"), Instant.now(), null, null);
        when(deliveryRepository.findActiveByIdForUpdate(eq(TENANT), any())).thenReturn(Optional.of(d));
        assertThrows(IllegalArgumentException.class, () -> service.recordLoading(TENANT, UUID.randomUUID(), new LoadingRequest(BigDecimal.ZERO, null)));
        DeliveryResponse r = service.recordLoading(TENANT, UUID.randomUUID(), new LoadingRequest(new BigDecimal("505"), " S-101, S-102 "));
        assertEquals(0, new BigDecimal("505").compareTo(r.loadedLitres()));
        assertEquals("S-101, S-102", r.sealNumbers());
        d.cancel();
        assertThrows(IllegalStateException.class, () -> service.recordLoading(TENANT, UUID.randomUUID(), new LoadingRequest(new BigDecimal("1"), null)));
    }
}
