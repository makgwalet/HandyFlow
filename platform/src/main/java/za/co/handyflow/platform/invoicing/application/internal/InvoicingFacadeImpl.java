package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Thin implementation — delegates entirely to InvoiceRepository's existing
 * findAllForVat()/findOutstandingForTenant() queries (unchanged, same SQL,
 * same status/date filtering) and just maps the results into the facade's
 * DTOs. No new query logic, no new business rules — see InvoicingFacade's
 * own Javadoc for why each method is shaped the way it is.
 */
@Service
@RequiredArgsConstructor
class InvoicingFacadeImpl implements InvoicingFacade {

    private final InvoiceRepository invoiceRepo;

    @Override
    public VatSummary getVatSummary(TenantId tenantId, LocalDate from, LocalDate to) {
        List<Invoice> invoices = invoiceRepo.findAllForVat(tenantId.getValue().toString(), from, to);

        BigDecimal outputVat = invoices.stream()
                .map(Invoice::getVatTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSubtotal = invoices.stream()
                .map(Invoice::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VatSummary(invoices.size(), totalSubtotal, outputVat);
    }

    @Override
    public List<OutstandingInvoiceSummary> findOutstandingInvoices(TenantId tenantId) {
        return invoiceRepo.findOutstandingForTenant(tenantId.getValue().toString()).stream()
                .map(inv -> new OutstandingInvoiceSummary(
                        inv.getId(),
                        inv.getInvoiceNumber(),
                        inv.getCustomerId(),
                        inv.getWalkinClientName(),
                        inv.getDueDate(),
                        inv.getTotal(),
                        inv.getAmountPaid()))
                .toList();
    }
}