package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopStay;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopStayRepository;
import za.co.handyflow.platform.fuel.dto.StayDtos.StayView;
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
import java.util.List;
import java.util.UUID;

/**
 * Charges a checked-out stay to its customer account as a DRAFT invoice in Invoicing; nothing is issued here. VAT comes from the company's own
 * settings and the due date from the account's payment terms. Not transactional on purpose: each step commits on its own so a failure leaves
 * the stay marked and visible. Check-out never waits on this; a failed invoice is retried from the stay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelStayInvoicingService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final FuelTruckStopStayRepository stays;
    private final FuelCustomerAccountRepository accounts;
    private final FuelStayInvoicingTx tx;
    private final InvoicingFacade invoicing;
    private final CrmFacade crm;

    /** Invoices a checked-out stay. Throws with a plain message when it cannot; the stay then shows FAILED and can be retried. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StayView invoice(TenantId tenantId, UUID stayId) {
        FuelTruckStopStay s = stays.findOwned(tenantId, stayId).orElseThrow(() -> new ResourceNotFoundException("Stay", stayId.toString()));
        if (!FuelTruckStopStay.CHECKED_OUT.equals(s.getStatus())) throw new IllegalStateException("Only a stay that has checked out can be invoiced.");
        if (s.getAccountId() == null) throw new IllegalStateException("This stay is not charged to an account; it is paid at the gate.");
        FuelCustomerAccount account = accounts.findActiveById(tenantId, s.getAccountId())
                .orElseThrow(() -> new IllegalStateException("The customer account for this stay is no longer active."));
        tx.claim(tenantId, stayId);
        DraftInvoiceResult result;
        try {
            LocalDate today = LocalDate.now(SAST);
            var vat = invoicing.vatTreatment(tenantId, today);
            BigDecimal vatRate = vat.registered() && vat.ratePercent() != null ? vat.ratePercent() : BigDecimal.ZERO;
            String description = "Overnight parking - " + s.getVehicleRegistration() + " - " + DAY.format(s.getArrivalDate()) + " to " + DAY.format(s.endDate());
            List<DraftInvoiceLine> lines = List.of(new DraftInvoiceLine(description, "night", BigDecimal.valueOf(s.nights()), s.getRatePerNight(), vatRate));
            String name = crm.findCustomerById(tenantId, account.getCrmCustomerId()).map(CustomerSummary::name).orElse("customer");
            result = invoicing.createDraftInvoice(tenantId, new DraftInvoice(account.getCrmCustomerId(), "Truck stop parking - " + name,
                    "Overnight stay " + s.getVehicleRegistration() + (s.getDriverName() == null ? "" : ", driver " + s.getDriverName()),
                    today.plusDays(account.getPaymentTermsDays()), lines));
        } catch (RuntimeException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Stay invoicing failed for stay={}: {}", stayId, why);
            tx.fail(tenantId, stayId, why);
            throw new IllegalStateException("The invoice could not be created: " + why + " The stay stays on the list to retry.");
        }
        final DraftInvoiceResult created = result;
        try {
            tx.link(tenantId, stayId, created.id(), created.invoiceNumber());
        } catch (RuntimeException e) {
            log.error("Invoice {} was created but could not be linked to stay {}", created.invoiceNumber(), stayId, e);
            try {
                tx.linkFailed(tenantId, stayId, created.id(), created.invoiceNumber(), e.getMessage());
            } catch (RuntimeException again) {
                log.error("Could not record the failed link for stay {} either: {}", stayId, again.getMessage());
            }
            throw new IllegalStateException("Invoice " + created.invoiceNumber() + " was created in Invoicing but could not be linked to this stay. Do not invoice it again.");
        }
        return FuelTruckStopStayService.view(stays.findOwned(tenantId, stayId).orElse(s), LocalDate.now(SAST));
    }
}
