package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Thin re-wrap of InvoicingFacade.OutstandingInvoiceSummary — kept as this module's own response type rather than exposing another module's facade DTO directly on this module's API surface. */
public record OutstandingInvoiceResponse(
        UUID id,
        String invoiceNumber,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal outstanding
) {
    public static OutstandingInvoiceResponse of(OutstandingInvoiceSummary s) {
        BigDecimal paid = s.amountPaid() != null ? s.amountPaid() : BigDecimal.ZERO;
        return new OutstandingInvoiceResponse(s.id(), s.invoiceNumber(), s.dueDate(), s.total(), paid,
                s.total().subtract(paid));
    }
}
