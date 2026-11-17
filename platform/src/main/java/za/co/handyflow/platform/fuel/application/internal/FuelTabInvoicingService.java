package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelTruckStopTab;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabLineRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTruckStopTabRepository;
import za.co.handyflow.platform.fuel.dto.TabDtos.TabView;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.DraftInvoice;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.DraftInvoiceLine;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.DraftInvoiceResult;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Charges a settled tab to its customer account as a DRAFT invoice in Invoicing; nothing is issued here. Same safeguards as stays: not transactional,
 * each step commits on its own, one caller wins the claim, and an invoice that was made but could not be linked is never made again.
 * A failed invoice never undoes the tab; it shows on the tab and can be retried.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelTabInvoicingService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelTruckStopTabRepository tabs;
    private final FuelTruckStopTabLineRepository lines;
    private final FuelCustomerAccountRepository accounts;
    private final FuelTabInvoicingTx tx;
    private final InvoicingFacade invoicing;
    private final CrmFacade crm;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TabView invoice(TenantId tenantId, UUID tabId) {
        FuelTruckStopTab t = tabs.findOwned(tenantId, tabId).orElseThrow(() -> new ResourceNotFoundException("Tab", tabId.toString()));
        if (!FuelTruckStopTab.SETTLED.equals(t.getStatus())) throw new IllegalStateException("Only a tab that has been charged can be invoiced.");
        if (t.getAccountId() == null) throw new IllegalStateException("This tab is not charged to an account; it was paid at the till.");
        FuelCustomerAccount account = accounts.findActiveById(tenantId, t.getAccountId())
                .orElseThrow(() -> new IllegalStateException("The customer account for this tab is no longer active."));
        var tabLines = lines.findForTab(tenantId, tabId);
        if (tabLines.isEmpty()) throw new IllegalStateException("This tab has no lines to invoice.");
        tx.claim(tenantId, tabId);
        DraftInvoiceResult result;
        try {
            LocalDate today = LocalDate.now(SAST);
            var vat = invoicing.vatTreatment(tenantId, today);
            BigDecimal vatRate = vat.registered() && vat.ratePercent() != null ? vat.ratePercent() : BigDecimal.ZERO;
            List<DraftInvoiceLine> invoiceLines = tabLines.stream()
                    .map(l -> new DraftInvoiceLine(l.getDescription() + " - " + t.getVehicleRegistration(), "each", l.getQuantity(), l.getUnitPrice(), vatRate)).toList();
            String name = crm.findCustomerById(tenantId, account.getCrmCustomerId()).map(CustomerSummary::name).orElse("customer");
            result = invoicing.createDraftInvoice(tenantId, new DraftInvoice(account.getCrmCustomerId(), "Truck stop services - " + name,
                    "Truck stop tab " + t.getVehicleRegistration() + (t.getDriverName() == null ? "" : ", driver " + t.getDriverName()),
                    today.plusDays(account.getPaymentTermsDays()), invoiceLines));
        } catch (RuntimeException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Tab invoicing failed for tab={}: {}", tabId, why);
            tx.fail(tenantId, tabId, why);
            throw new IllegalStateException("The invoice could not be created: " + why + " The tab stays on the list to retry.");
        }
        final DraftInvoiceResult created = result;
        try {
            tx.link(tenantId, tabId, created.id(), created.invoiceNumber());
        } catch (RuntimeException e) {
            log.error("Invoice {} was created but could not be linked to tab {}", created.invoiceNumber(), tabId, e);
            try {
                tx.linkFailed(tenantId, tabId, created.id(), created.invoiceNumber(), e.getMessage());
            } catch (RuntimeException again) {
                log.error("Could not record the failed link for tab {} either: {}", tabId, again.getMessage());
            }
            throw new IllegalStateException("Invoice " + created.invoiceNumber() + " was created in Invoicing but could not be linked to this tab. Do not invoice it again.");
        }
        return FuelTabService.view(tabs.findOwned(tenantId, tabId).orElse(t), tabLines);
    }
}
