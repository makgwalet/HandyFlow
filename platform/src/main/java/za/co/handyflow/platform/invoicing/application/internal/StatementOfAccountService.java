package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.domain.model.CreditNote;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.CreditNoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * FIX: "no statement of account PDF" gap — a rolled-up "everything you owe
 * us" document across a customer's invoices, standard in most invoicing
 * tools for large/recurring clients.
 * <p>
 * This is also the first place "net amount actually owed" (invoice.total −
 * amountPaid − credit notes against it) gets computed anywhere in the
 * codebase — CreditNote's own doc comment explicitly left that
 * computation "to callers/reporting" rather than baking it into Invoice
 * itself. This is that caller.
 */
@Service
@RequiredArgsConstructor
public class StatementOfAccountService {

    private final InvoiceRepository invoiceRepo;
    private final CreditNoteRepository creditNoteRepo;
    private final CrmFacade crmFacade;
    private final InvoicePaymentTermsResolver paymentTermsResolver;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    public record InvoiceStatementLine(
            String invoiceNumber, LocalDate issuedDate, LocalDate dueDate, boolean dueDateEstimated,
            BigDecimal total, BigDecimal amountPaid, BigDecimal creditedTotal,
            BigDecimal balance, int daysOverdue
    ) {}

    public record CustomerStatement(
            String customerName, String customerEmail,
            LocalDate periodFrom, LocalDate periodTo,
            List<InvoiceStatementLine> lines,
            BigDecimal totalBilled, BigDecimal totalPaid, BigDecimal totalCredited, BigDecimal totalOutstanding,
            BigDecimal current, BigDecimal days1to30, BigDecimal days31to60, BigDecimal days61to90, BigDecimal days90plus
    ) {}

    @Transactional(readOnly = true)
    public CustomerStatement buildStatement(TenantId tenantId, UUID customerId, LocalDate from, LocalDate to) {
        var customer = crmFacade.findCustomerById(tenantId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId.toString()));

        // Only issued invoices belong on a statement — a DRAFT was never sent, so it's not "owed" yet.
        List<Invoice> invoices = invoiceRepo.findByCustomer(tenantId, customerId).stream()
                .filter(i -> i.getIssuedAt() != null)
                .filter(i -> {
                    LocalDate issuedDate = i.getIssuedAt().atZone(SAST).toLocalDate();
                    return (from == null || !issuedDate.isBefore(from)) && (to == null || !issuedDate.isAfter(to));
                })
                .toList();

        LocalDate today = LocalDate.now(SAST);
        BigDecimal current = BigDecimal.ZERO, d30 = BigDecimal.ZERO, d60 = BigDecimal.ZERO,
                d90 = BigDecimal.ZERO, d90p = BigDecimal.ZERO;
        BigDecimal totalBilled = BigDecimal.ZERO, totalPaid = BigDecimal.ZERO, totalCredited = BigDecimal.ZERO;

        List<InvoiceStatementLine> lines = new ArrayList<>();
        for (Invoice inv : invoices) {
            BigDecimal creditedTotal = creditNoteRepo.findByInvoice(tenantId, inv.getId()).stream()
                    .map(CreditNote::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = inv.getTotal().subtract(inv.getAmountPaid()).subtract(creditedTotal).max(BigDecimal.ZERO);

            LocalDate issuedDate = inv.getIssuedAt().atZone(SAST).toLocalDate();
            // FIX: invoices with no explicit dueDate previously always
            // landed in "Current" regardless of age — confirmed via real
            // testing (several invoices showing "Due: —" on a statement,
            // silently excluded from aging entirely no matter how old or
            // unpaid). Falls back to the tenant's resolved payment terms
            // (same defensive parsing InvoicePaymentTermsResolver already
            // does for issueInvoice()) so an undated invoice ages the same
            // way a dated one would, rather than being treated as
            // permanently safe. dueDateEstimated flags this for the PDF so
            // the statement is honest about which dates are explicit vs
            // derived.
            boolean dueDateEstimated = inv.getDueDate() == null;
            LocalDate dueDate = dueDateEstimated
                    ? paymentTermsResolver.resolveDueDate(tenantId, issuedDate)
                    : inv.getDueDate();
            int daysOverdue = balance.compareTo(BigDecimal.ZERO) > 0
                    ? (int) Math.max(0, ChronoUnit.DAYS.between(dueDate, today))
                    : 0;

            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                if (daysOverdue == 0) current = current.add(balance);
                else if (daysOverdue <= 30) d30 = d30.add(balance);
                else if (daysOverdue <= 60) d60 = d60.add(balance);
                else if (daysOverdue <= 90) d90 = d90.add(balance);
                else d90p = d90p.add(balance);
            }

            totalBilled = totalBilled.add(inv.getTotal());
            totalPaid = totalPaid.add(inv.getAmountPaid());
            totalCredited = totalCredited.add(creditedTotal);

            lines.add(new InvoiceStatementLine(inv.getInvoiceNumber(), issuedDate, dueDate, dueDateEstimated,
                    inv.getTotal(), inv.getAmountPaid(), creditedTotal, balance, daysOverdue));
        }

        BigDecimal totalOutstanding = totalBilled.subtract(totalPaid).subtract(totalCredited).max(BigDecimal.ZERO);

        return new CustomerStatement(
                customer.name(), customer.email(), from, to, lines,
                totalBilled, totalPaid, totalCredited, totalOutstanding,
                current, d30, d60, d90, d90p);
    }
}