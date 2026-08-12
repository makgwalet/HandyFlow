package za.co.handyflow.platform.invoicing.application;

import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public entry point for other modules that need invoice data — matches
 * the same pattern AccountingFacade/CrmFacade already established (DTOs
 * only, never the Invoice entity or InvoiceRepository itself).
 * <p>
 * WHY THIS WAS EMPTY UNTIL NOW: this interface existed as a placeholder
 * with no methods. Meanwhile, accounting.application.internal.AccountingService
 * reached directly into invoicing.domain.repository.InvoiceRepository and
 * invoicing.domain.model.Invoice for getVat201() and getArAging() — a
 * real boundary violation (see HandyFlow BOS Discovery doc, Section 15/
 * 26.4/31.5), the same shape as the Expenses->Accounting JDBC violation
 * that was fixed earlier in this engagement, just via a Java import
 * instead of raw SQL. This facade is the fix: exactly the two read
 * operations AccountingService actually calls, nothing more.
 * <p>
 * DESIGN NOTE: getVat201() only ever aggregates invoice totals (sum of
 * VAT, sum of subtotal, count) — it never needed individual invoices
 * past that, so getVatSummary() returns the aggregate directly rather
 * than a List<Invoice> for the caller to sum itself. getArAging(), by
 * contrast, genuinely needs per-invoice detail (for bucketing and the
 * customer-name lookup accounting already does via CrmFacade), so
 * findOutstandingInvoices() returns one summary DTO per invoice — still
 * never the entity itself.
 */
public interface InvoicingFacade {

    /**
     * Aggregated VAT figures for all VAT-relevant invoices issued/created
     * in the given date range (same status/date filtering
     * InvoiceRepository.findAllForVat() already applies — see that
     * query's own comments for exactly which invoice statuses and which
     * date field, issued_at vs. created_at, count).
     */
    VatSummary getVatSummary(TenantId tenantId, LocalDate from, LocalDate to);

    /**
     * One summary per currently-outstanding invoice (ISSUED,
     * PARTIALLY_PAID, or OVERDUE — same filter
     * InvoiceRepository.findOutstandingForTenant() already applies),
     * ordered by due date ascending. Callers needing AR aging buckets or
     * per-invoice detail use this; callers only needing totals should
     * prefer a narrower method if one is added later rather than summing
     * this list themselves.
     */
    List<OutstandingInvoiceSummary> findOutstandingInvoices(TenantId tenantId);

    /**
     * @param invoiceCount     number of invoices included in the range
     * @param totalSubtotal    sum of subtotal (ex-VAT) across those invoices
     * @param totalOutputVat   sum of VAT total across those invoices
     */
    record VatSummary(int invoiceCount, BigDecimal totalSubtotal, BigDecimal totalOutputVat) {}

    /**
     * @param id               invoice id
     * @param invoiceNumber    e.g. "INV-00042"
     * @param customerId       null for walk-in invoices — see walkinClientName in that case
     * @param walkinClientName only populated when customerId is null
     * @param dueDate          may be null (some invoices have no due date set)
     * @param total            invoice total including VAT
     * @param amountPaid       may be null — treat as zero, matching AccountingService's
     *                         existing null-handling for this exact field
     */
    record OutstandingInvoiceSummary(
            UUID id,
            String invoiceNumber,
            UUID customerId,
            String walkinClientName,
            LocalDate dueDate,
            BigDecimal total,
            BigDecimal amountPaid
    ) {}
}