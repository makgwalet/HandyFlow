package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.CreateInvoiceFromDeliveriesRequest;
import za.co.handyflow.platform.fuel.dto.InvoiceCreatedResponse;
import za.co.handyflow.platform.fuel.dto.InvoiceQueueLine;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.DraftInvoice;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.DraftInvoiceLine;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.DraftInvoiceResult;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Stage 2C: "delivered, not invoiced" queue and the hand-off to Invoicing. Fuel only ever creates a DRAFT invoice;
 * issuing it (and everything after: ledger, VAT, payment) stays with Invoicing. A delivery that cannot be invoiced
 * stays in the queue with the reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelInvoicingService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerAccountRepository accounts;
    private final FuelInvoicingTx tx;
    private final InvoicingFacade invoicing;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<InvoiceQueueLine> queue(TenantId tenantId) {
        Map<UUID, String> names = new HashMap<>();
        List<InvoiceQueueLine> out = new ArrayList<>();
        for (FuelDelivery d : deliveries.findNotInvoiced(tenantId)) {
            String name = d.getCustomerId() == null ? null
                    : names.computeIfAbsent(d.getCustomerId(), id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)"));
            out.add(new InvoiceQueueLine(d.getId(), d.getReceiptNumber(), d.getCustomerId(), name, d.getFuelType(),
                    d.getLitresDelivered(), d.getPricePerLitre(), d.getTotalAmount(), d.getDeliveredAt(), d.getInvoiceState(), d.getInvoiceError()));
        }
        return out;
    }

    /** Not transactional on purpose: each step commits on its own so a failure leaves a visible trail. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InvoiceCreatedResponse invoice(TenantId tenantId, CreateInvoiceFromDeliveriesRequest req) {
        Set<UUID> ids = new LinkedHashSet<>(req.deliveryIds());
        List<FuelDelivery> list = deliveries.findAllByIds(tenantId, ids);
        if (list.size() != ids.size()) throw new ResourceNotFoundException("Delivery", "one or more of the chosen deliveries");
        UUID customerId = list.get(0).getCustomerId();
        for (FuelDelivery d : list) {
            if (!"DELIVERED".equals(d.getStatus())) throw new IllegalStateException("Only completed deliveries can be invoiced.");
            if (d.getCustomerId() == null) throw new IllegalArgumentException("Delivery " + d.getReceiptNumber() + " has no customer, so it cannot be invoiced. Walk-in sales are billed at the point of sale.");
            if (!d.getCustomerId().equals(customerId)) throw new IllegalArgumentException("Choose deliveries for one customer at a time.");
        }
        tx.claim(tenantId, ids);

        DraftInvoiceResult result;
        try {
            LocalDate today = LocalDate.now(SAST);
            var vat = invoicing.vatTreatment(tenantId, today);
            BigDecimal vatRate = vat.registered() && vat.ratePercent() != null ? vat.ratePercent() : BigDecimal.ZERO;
            int terms = accounts.findByCrmCustomerId(tenantId, customerId).map(FuelCustomerAccount::getPaymentTermsDays).orElse(30);
            list.sort(Comparator.comparing(FuelDelivery::getDeliveredAt));
            List<DraftInvoiceLine> lines = new ArrayList<>();
            StringBuilder notes = new StringBuilder("Fuel deliveries: ");
            for (FuelDelivery d : list) {
                lines.add(new DraftInvoiceLine(
                        "Fuel delivery " + d.getReceiptNumber() + " - " + d.getFuelType() + " - " + DAY.format(d.getDeliveredAt().atZone(SAST)),
                        "L", d.getLitresDelivered(), d.getPricePerLitre(), vatRate));
                notes.append(d.getReceiptNumber()).append(' ');
            }
            String customerName = crm.findCustomerById(tenantId, customerId).map(CustomerSummary::name).orElse("customer");
            result = invoicing.createDraftInvoice(tenantId,
                    new DraftInvoice(customerId, "Fuel supply - " + customerName, notes.toString().trim(), today.plusDays(terms), lines));
        } catch (RuntimeException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Fuel invoicing failed for customer={} deliveries={}: {}", customerId, ids, why);
            tx.fail(tenantId, ids, why);
            throw new IllegalStateException("The invoice could not be created: " + why + " The deliveries stay in the queue.");
        }
        try {
            tx.link(tenantId, ids, result.id(), result.invoiceNumber());
        } catch (RuntimeException e) {
            // The invoice exists; the claim will go stale and show up again. Say so rather than hide it.
            log.error("Invoice {} was created but could not be linked to deliveries {}", result.invoiceNumber(), ids, e);
            throw new IllegalStateException("Invoice " + result.invoiceNumber() + " was created in Invoicing but could not be linked here. Do not invoice these deliveries again; contact support.");
        }
        return new InvoiceCreatedResponse(result.id(), result.invoiceNumber(), result.subtotal(), result.vatTotal(), result.total(), ids.size());
    }
}
