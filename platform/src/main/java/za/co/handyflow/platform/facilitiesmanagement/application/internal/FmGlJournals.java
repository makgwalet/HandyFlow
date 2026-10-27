package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest.JournalLineRequest;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoicePayment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The journal lines for each facilities-management document, kept separate from the posting mechanics so the
 * double-entry rules can be tested on their own. Every set of lines balances (debits equal credits) by construction:
 * <ul>
 *   <li><b>Invoice</b>: DR receivables (total); CR revenue (subtotal); CR output VAT (VAT, when there is any).</li>
 *   <li><b>Credit note</b>: DR revenue (subtotal); DR output VAT (VAT, when there is any); CR receivables (total).</li>
 *   <li><b>Payment</b>: DR bank (amount); CR receivables (amount).</li>
 * </ul>
 * {@code vatAccount} may be null only when the document carries no VAT.
 */
final class FmGlJournals {

    private FmGlJournals() {}

    static List<JournalLineRequest> invoiceLines(FmInvoice invoice, String clientName,
                                                 UUID receivables, UUID revenue, UUID vatAccount) {
        List<JournalLineRequest> lines = new ArrayList<>();
        lines.add(new JournalLineRequest(receivables,
                "FM invoice " + invoice.getInvoiceNumber() + " — " + clientName, invoice.getTotal(), null));
        lines.add(new JournalLineRequest(revenue,
                "Facilities management revenue — " + clientName, null, invoice.getSubtotal()));
        if (invoice.getVatAmount().signum() > 0) {
            lines.add(new JournalLineRequest(requireVat(vatAccount),
                    "Output VAT " + invoice.getVatRate() + "% — " + invoice.getInvoiceNumber(), null, invoice.getVatAmount()));
        }
        return lines;
    }

    static List<JournalLineRequest> creditNoteLines(FmCreditNote note, FmInvoice invoice, String clientName,
                                                    UUID receivables, UUID revenue, UUID vatAccount) {
        List<JournalLineRequest> lines = new ArrayList<>();
        lines.add(new JournalLineRequest(revenue,
                "Credit note " + note.getCreditNoteNumber() + " against " + invoice.getInvoiceNumber() + " — " + clientName,
                note.getSubtotal(), null));
        if (note.getVatAmount().signum() > 0) {
            lines.add(new JournalLineRequest(requireVat(vatAccount),
                    "Output VAT reversed " + note.getVatRate() + "% — " + note.getCreditNoteNumber(), note.getVatAmount(), null));
        }
        lines.add(new JournalLineRequest(receivables,
                "Credit note " + note.getCreditNoteNumber() + " — " + clientName, null, note.getTotal()));
        return lines;
    }

    static List<JournalLineRequest> paymentLines(FmInvoicePayment payment, FmInvoice invoice, String clientName,
                                                 UUID bank, UUID receivables) {
        String ref = payment.getReference() != null ? " ref " + payment.getReference() : "";
        return List.of(
                new JournalLineRequest(bank,
                        "Payment on " + invoice.getInvoiceNumber() + " — " + clientName + ref, payment.getAmount(), null),
                new JournalLineRequest(receivables,
                        "Payment on " + invoice.getInvoiceNumber() + " — " + clientName, null, payment.getAmount()));
    }

    private static UUID requireVat(UUID vatAccount) {
        if (vatAccount == null) throw new IllegalStateException("A VAT ledger account is required to post a document that carries VAT");
        return vatAccount;
    }
}
