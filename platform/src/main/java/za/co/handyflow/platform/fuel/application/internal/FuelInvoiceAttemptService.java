package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelInvoiceAttempt;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelInvoiceAttemptRepository;
import za.co.handyflow.platform.fuel.dto.FuelInvoiceAttemptDtos.AttemptView;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

/**
 * G02: shows the trail of invoice hand-offs and lets a person finish the two cases that cannot finish themselves.
 * There is deliberately no automatic retry: Invoicing has no idempotency key, so a blind retry could bill twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelInvoiceAttemptService {

    private final FuelInvoiceAttemptRepository attempts;
    private final FuelDeliveryRepository deliveries;
    private final InvoicingFacade invoicing;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<AttemptView> list(TenantId tenantId, boolean attentionOnly) {
        Instant now = Instant.now();
        List<FuelInvoiceAttempt> rows = attentionOnly ? attempts.findOpen(tenantId, PageRequest.of(0, 200)) : attempts.findRecent(tenantId, PageRequest.of(0, 100));
        Map<UUID, String> names = new HashMap<>();
        List<AttemptView> out = new ArrayList<>();
        for (FuelInvoiceAttempt a : rows) {
            boolean attention = a.needsAttention(now, FuelInvoicingTx.STALE_CLAIM);
            if (attentionOnly && !attention) continue;
            out.add(view(tenantId, a, attention, names));
        }
        return out;
    }

    /** How many attempts need a person right now. */
    @Transactional(readOnly = true)
    public int attentionCount(TenantId tenantId) {
        return list(tenantId, true).size();
    }

    /**
     * The invoice was created but the deliveries were never tied to it. Check Invoicing still has that invoice, then tie them.
     * Refuses if any delivery has meanwhile been invoiced some other way.
     */
    @Transactional
    public AttemptView relink(TenantId tenantId, UUID attemptId, UUID by, String note) {
        FuelInvoiceAttempt a = attempts.findOwnedForUpdate(tenantId, attemptId).orElseThrow(() -> new ResourceNotFoundException("Invoice attempt", attemptId.toString()));
        if (!"LINK_FAILED".equals(a.getStatus())) throw new IllegalStateException("Only an attempt whose invoice was created but not linked can be relinked.");
        if (a.getInvoiceId() == null) throw new IllegalStateException("This attempt has no invoice recorded, so there is nothing to link.");
        if (invoicing.findInvoiceStatus(tenantId, a.getInvoiceId()).isEmpty()) {
            throw new IllegalStateException("Invoicing cannot find invoice " + (a.getInvoiceNumber() == null ? "" : a.getInvoiceNumber() + " ") + "any more. Check Invoicing before releasing these deliveries.");
        }
        List<FuelDelivery> list = deliveries.findAllByIds(tenantId, a.getDeliveryIds());
        for (FuelDelivery d : list) {
            if ("INVOICED".equals(d.getInvoiceState()) && !a.getInvoiceId().equals(d.getInvoiceId())) {
                throw new IllegalStateException("Delivery " + d.getReceiptNumber() + " has since been invoiced on another invoice. Do not link it again; check Invoicing.");
            }
        }
        for (FuelDelivery d : list) {
            d.markInvoiced(a.getInvoiceId(), a.getInvoiceNumber());
            deliveries.save(d);
        }
        a.resolve(by, note == null || note.isBlank() ? "Linked to " + a.getInvoiceNumber() : note);
        attempts.save(a);
        return view(tenantId, a, false, new HashMap<>());
    }

    /**
     * An attempt that never finished (for example the server stopped). The person has checked Invoicing and no invoice exists,
     * so the deliveries go back to the queue. The note is required and kept.
     */
    @Transactional
    public AttemptView release(TenantId tenantId, UUID attemptId, UUID by, String note) {
        FuelInvoiceAttempt a = attempts.findOwnedForUpdate(tenantId, attemptId).orElseThrow(() -> new ResourceNotFoundException("Invoice attempt", attemptId.toString()));
        if (!"STARTED".equals(a.getStatus())) throw new IllegalStateException("Only an attempt that never finished can be released.");
        if (!a.needsAttention(Instant.now(), FuelInvoicingTx.STALE_CLAIM)) throw new IllegalStateException("This attempt may still be running. Wait a few minutes, then check again.");
        List<FuelDelivery> list = deliveries.findAllByIds(tenantId, a.getDeliveryIds());
        for (FuelDelivery d : list) if ("INVOICING".equals(d.getInvoiceState())) { d.releaseInvoiceClaim(); deliveries.save(d); }
        a.resolve(by, note);
        attempts.save(a);
        log.warn("Invoice attempt {} released by {} for deliveries {}: {}", attemptId, by, a.getDeliveryIds(), note);
        return view(tenantId, a, false, new HashMap<>());
    }

    private AttemptView view(TenantId tenantId, FuelInvoiceAttempt a, boolean attention, Map<UUID, String> names) {
        String name = a.getCustomerId() == null ? null
                : names.computeIfAbsent(a.getCustomerId(), id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)"));
        List<String> receipts = deliveries.findAllByIds(tenantId, a.getDeliveryIds()).stream().map(FuelDelivery::getReceiptNumber).sorted().toList();
        return new AttemptView(a.getId(), a.getCustomerId(), name, a.getStatus(), attention, receipts, a.getDeliveryIds().size(), a.getInvoiceId(), a.getInvoiceNumber(),
                a.getError(), a.getAttemptedBy(), a.getStartedAt(), a.getFinishedAt(), a.getResolvedBy(), a.getResolvedAt(), a.getResolutionNote());
    }
}
