package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelInvoiceAttempt;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelInvoiceAttemptRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** G02: finishing the two attempts that cannot finish themselves. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelInvoiceAttemptServiceTest {

    @Mock FuelInvoiceAttemptRepository attempts;
    @Mock FuelDeliveryRepository deliveries;
    @Mock InvoicingFacade invoicing;
    @Mock CrmFacade crm;
    @InjectMocks FuelInvoiceAttemptService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    final UUID customer = UUID.randomUUID();

    private FuelDelivery delivery(String receipt) {
        FuelDelivery d = FuelDelivery.create(T, UUID.randomUUID(), customer, Map.of(), "DIESEL", new BigDecimal("1000"), new BigDecimal("22.50"), Instant.now(), null, null);
        d.complete(new BigDecimal("1000"), "P Botha", null, null, null, false, null, null);
        d.assignReceiptNumber(receipt);
        return d;
    }

    private FuelInvoiceAttempt attempt(FuelDelivery d) { return FuelInvoiceAttempt.start(T, customer, List.of(d.getId()), BY); }

    @Test @DisplayName("an attempt moves STARTED to CREATED, FAILED or LINK_FAILED, and only open ones can be resolved")
    void lifecycle() {
        FuelDelivery d = delivery("FDR-1");
        FuelInvoiceAttempt a = attempt(d);
        assertEquals("STARTED", a.getStatus());
        assertFalse(a.needsAttention(Instant.now(), Duration.ofMinutes(15)), "a fresh attempt is just running");
        assertTrue(a.needsAttention(Instant.now().plus(Duration.ofMinutes(16)), Duration.ofMinutes(15)), "an old unfinished one needs a person");
        a.linkFailed(UUID.randomUUID(), "INV-1", "db down");
        assertTrue(a.needsAttention(Instant.now(), Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class, () -> a.resolve(BY, "no"));
        a.resolve(BY, "Linked by hand");
        assertEquals("RESOLVED", a.getStatus());
        assertThrows(IllegalStateException.class, () -> a.resolve(BY, "again please"));
        FuelInvoiceAttempt ok = attempt(d);
        ok.created(UUID.randomUUID(), "INV-2");
        assertThrows(IllegalStateException.class, () -> ok.resolve(BY, "nothing to do"));
        assertThrows(IllegalArgumentException.class, () -> FuelInvoiceAttempt.start(T, customer, List.of(), BY));
    }

    @Test @DisplayName("relink ties the deliveries to the invoice Invoicing still has, and resolves the attempt")
    void relinkHappy() {
        FuelDelivery d = delivery("FDR-2");
        FuelInvoiceAttempt a = attempt(d);
        UUID inv = UUID.randomUUID();
        a.linkFailed(inv, "INV-5", "db down");
        when(attempts.findOwnedForUpdate(T, a.getId())).thenReturn(Optional.of(a));
        when(invoicing.findInvoiceStatus(T, inv)).thenReturn(Optional.of("DRAFT"));
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(d)));
        service.relink(T, a.getId(), BY, null);
        assertEquals("INVOICED", d.getInvoiceState());
        assertEquals("INV-5", d.getInvoiceNumber());
        assertEquals("RESOLVED", a.getStatus());
        verify(deliveries).save(d);
    }

    @Test @DisplayName("relink is refused when Invoicing no longer has the invoice, and nothing is changed")
    void relinkInvoiceGone() {
        FuelDelivery d = delivery("FDR-3");
        FuelInvoiceAttempt a = attempt(d);
        a.linkFailed(UUID.randomUUID(), "INV-6", "x");
        when(attempts.findOwnedForUpdate(T, a.getId())).thenReturn(Optional.of(a));
        when(invoicing.findInvoiceStatus(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.relink(T, a.getId(), BY, null));
        verify(deliveries, never()).save(any());
        assertEquals("LINK_FAILED", a.getStatus());
    }

    @Test @DisplayName("relink is refused when a delivery has meanwhile been invoiced on a different invoice")
    void relinkConflict() {
        FuelDelivery d = delivery("FDR-4");
        d.markInvoiced(UUID.randomUUID(), "INV-OTHER");
        FuelInvoiceAttempt a = attempt(d);
        a.linkFailed(UUID.randomUUID(), "INV-7", "x");
        when(attempts.findOwnedForUpdate(T, a.getId())).thenReturn(Optional.of(a));
        when(invoicing.findInvoiceStatus(eq(T), any())).thenReturn(Optional.of("DRAFT"));
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(new ArrayList<>(List.of(d)));
        assertThrows(IllegalStateException.class, () -> service.relink(T, a.getId(), BY, null));
        assertEquals("INV-OTHER", d.getInvoiceNumber());
    }

    @Test @DisplayName("an attempt in another tenant is not found, and a finished one cannot be relinked")
    void isolationAndState() {
        when(attempts.findOwnedForUpdate(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.relink(T, UUID.randomUUID(), BY, null));
        assertThrows(ResourceNotFoundException.class, () -> service.release(T, UUID.randomUUID(), BY, "checked it"));
        FuelInvoiceAttempt ok = attempt(delivery("FDR-5"));
        ok.created(UUID.randomUUID(), "INV-8");
        when(attempts.findOwnedForUpdate(T, ok.getId())).thenReturn(Optional.of(ok));
        assertThrows(IllegalStateException.class, () -> service.relink(T, ok.getId(), BY, null));
    }

    @Test @DisplayName("release is refused for a fresh attempt that may still be running")
    void releaseTooSoon() {
        FuelInvoiceAttempt a = attempt(delivery("FDR-6"));
        when(attempts.findOwnedForUpdate(T, a.getId())).thenReturn(Optional.of(a));
        assertThrows(IllegalStateException.class, () -> service.release(T, a.getId(), BY, "checked Invoicing"));
        assertEquals("STARTED", a.getStatus());
    }

    @Test @DisplayName("a claimed delivery goes back to the queue when released, and an unclaimed one refuses")
    void deliveryRelease() {
        FuelDelivery d = delivery("FDR-7");
        assertThrows(IllegalStateException.class, d::releaseInvoiceClaim);
        d.markInvoiceFailed("x");
        assertThrows(IllegalStateException.class, d::releaseInvoiceClaim);
    }

    @Test @DisplayName("the attention list shows only attempts a person must look at")
    void attentionList() {
        FuelDelivery d = delivery("FDR-8");
        FuelInvoiceAttempt linkFailed = attempt(d); linkFailed.linkFailed(UUID.randomUUID(), "INV-9", "x");
        FuelInvoiceAttempt running = attempt(d);
        when(attempts.findOpen(eq(T), any())).thenReturn(List.of(linkFailed, running));
        when(deliveries.findAllByIds(eq(T), any())).thenReturn(List.of(d));
        var out = service.list(T, true);
        assertEquals(1, out.size());
        assertEquals("LINK_FAILED", out.get(0).status());
        assertEquals(List.of("FDR-8"), out.get(0).receiptNumbers());
        assertEquals(1, service.attentionCount(T));
    }
}
