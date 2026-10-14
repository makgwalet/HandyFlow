package za.co.handyflow.platform.invoicing.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a South African tax invoice and credit note must show (finance patch F14, 2026-10-14). Pure, so it is tested without
 * Spring.
 * <p>
 * Based on sections 20 and 21 of the VAT Act as set out in SARS's draft interpretation note on those sections (2014/26) and
 * summaries by tax practitioners; it has not been checked by a tax practitioner and the current SARS guide (VAT 404) should
 * be read before documents are called compliant. In short:
 * <ul>
 *   <li>Full tax invoice: "tax invoice" (or "VAT invoice" or "invoice") prominently; supplier name, address and VAT number;
 *       recipient name and address, and VAT number when the recipient is a registered vendor; serial number; date;
 *       description and quantity; value, VAT and total. Required above R 5,000; below that an abridged invoice may omit the
 *       recipient and quantity.</li>
 *   <li>Credit note: "credit note" prominently; supplier name, address and VAT number; recipient name and address, and VAT
 *       number when a vendor; date; the reduced value and the VAT; a short explanation; enough to identify the transaction
 *       (the original invoice).</li>
 * </ul>
 */
public final class TaxDocumentRules {

    private TaxDocumentRules() {}

    public static final BigDecimal FULL_INVOICE_ABOVE = new BigDecimal("5000");

    /** One VAT rate's share of a document. */
    public record RateTotal(BigDecimal rate, BigDecimal base, BigDecimal vat) {}
    public record LineAmount(BigDecimal rate, BigDecimal base, BigDecimal vat) {}
    public record Issue(String severity, String message) {}

    /** The heading of an invoice: only a VAT vendor issues a tax invoice, and a draft is not yet a document. */
    public static String invoiceHeading(boolean issued, boolean hasVatNumber) {
        if (!issued) return "DRAFT INVOICE";
        return hasVatNumber ? "TAX INVOICE" : "INVOICE";
    }

    public static String creditNoteHeading(boolean issued) {
        return issued ? "CREDIT NOTE" : "CREDIT NOTE (NOT YET ISSUED)";
    }

    /** SARS VAT numbers are 10 digits starting with 4. */
    public static boolean looksLikeVatNumber(String v) {
        return v != null && v.replace(" ", "").matches("4\\d{9}");
    }

    /** Totals per VAT rate, lowest rate first, so mixed 15% and 0% lines are not shown as one rate. */
    public static List<RateTotal> byRate(List<LineAmount> lines) {
        Map<BigDecimal, BigDecimal[]> sums = new TreeMap<>();
        for (LineAmount l : lines) {
            BigDecimal rate = (l.rate() == null ? BigDecimal.ZERO : l.rate()).stripTrailingZeros();
            if (rate.scale() < 0) rate = rate.setScale(0);
            BigDecimal[] s = sums.computeIfAbsent(rate, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].add(l.base() == null ? BigDecimal.ZERO : l.base());
            s[1] = s[1].add(l.vat() == null ? BigDecimal.ZERO : l.vat());
        }
        List<RateTotal> out = new ArrayList<>();
        sums.forEach((r, s) -> out.add(new RateTotal(r, s[0].setScale(2, RoundingMode.HALF_UP), s[1].setScale(2, RoundingMode.HALF_UP))));
        return out;
    }

    /** One line of address text from the usual street, suburb, city, postal code parts; empty when there is none. */
    public static String addressText(Map<String, String> a) {
        if (a == null) return "";
        List<String> parts = new ArrayList<>();
        for (String k : new String[]{"street", "suburb", "city", "postalCode"}) {
            String v = a.get(k);
            if (v != null && !v.isBlank()) parts.add(v.trim());
        }
        return String.join(", ", parts);
    }

    /** What is wrong with the business's own details for issuing tax documents. */
    public static List<Issue> supplierIssues(String vatNumber, Map<String, String> address, String companyName) {
        List<Issue> out = new ArrayList<>();
        if (companyName == null || companyName.isBlank())
            out.add(new Issue("ERROR", "The business name is not set. Every tax document must show it."));
        if (addressText(address).isEmpty())
            out.add(new Issue("ERROR", "The business address is not set. Tax invoices and credit notes must show it."));
        if (vatNumber == null || vatNumber.isBlank())
            out.add(new Issue("WARNING", "No VAT registration number is set, so documents are headed Invoice, not Tax Invoice. "
                    + "VAT is still added to invoices at the standard rate: add your VAT number if you are a VAT vendor, "
                    + "or set the VAT rate on your invoices to 0% if you are not."));
        else if (!looksLikeVatNumber(vatNumber))
            out.add(new Issue("WARNING", "The VAT number does not look like a SARS VAT number (10 digits starting with 4). Check it."));
        return out;
    }

    /**
     * What a customer's details need for a full tax invoice. {@code total} is the invoice total including VAT.
     * Above R 5,000 the recipient's name and address are required, and the VAT number when the customer is a VAT vendor.
     */
    public static List<Issue> recipientIssues(BigDecimal total, String name, Map<String, String> address, String vatNumber) {
        List<Issue> out = new ArrayList<>();
        if (total == null || total.compareTo(FULL_INVOICE_ABOVE) <= 0) return out;
        if (name == null || name.isBlank())
            out.add(new Issue("ERROR", "An invoice above R 5,000 must name the customer."));
        if (addressText(address).isEmpty())
            out.add(new Issue("ERROR", "An invoice above R 5,000 must show the customer's address."));
        if (vatNumber == null || vatNumber.isBlank())
            out.add(new Issue("INFO", "If the customer is a VAT vendor, their VAT number must be on the invoice."));
        return out;
    }

    /** Null when a credit note carries what the law asks for, otherwise what is missing. */
    public static String creditNoteProblem(String reason) {
        if (reason == null || reason.trim().length() < 3)
            return "A credit note must say briefly why it was issued";
        return null;
    }
}
