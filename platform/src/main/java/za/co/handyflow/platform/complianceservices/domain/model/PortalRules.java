package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a client may see in the portal, and the checks on inviting someone. Pure rules. The portal shows a client only
 * what was sent to them: a quote that is still being priced or approved, an agreement that has not started, a draft
 * invoice, and anything about discount authority or internal notes stay out of it.
 */
public final class PortalRules {

    private PortalRules() {}

    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String REVOKED = "REVOKED";
    public static final int INVITE_DAYS = 7;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    /** Quotes the client has been sent. A withdrawn quote is not shown, and neither is one still in the practice's hands. */
    public static final Set<String> QUOTE_STATUSES = Set.of(QuoteRules.SENT, QuoteRules.ACCEPTED, QuoteRules.DECLINED);
    public static final Set<String> AGREEMENT_STATUSES = Set.of(AgreementRules.ACTIVE, AgreementRules.SUSPENDED, AgreementRules.ENDED);
    /** A draft is not yet an invoice to the client, and a cancelled one is no longer owed. */
    public static final Set<String> HIDDEN_INVOICE_STATUSES = Set.of("DRAFT", "CANCELLED");

    public static boolean quoteVisible(String status) { return status != null && QUOTE_STATUSES.contains(status); }

    public static boolean agreementVisible(String status) { return status != null && AGREEMENT_STATUSES.contains(status); }

    public static boolean invoiceVisible(String invoiceStatus) { return invoiceStatus != null && !HIDDEN_INVOICE_STATUSES.contains(invoiceStatus); }

    public static String normaliseEmail(String raw) { return raw == null ? "" : raw.trim().toLowerCase(); }

    public static String emailProblem(String raw) {
        String e = normaliseEmail(raw);
        if (e.isEmpty()) return "An email address is required";
        if (e.length() > 255 || !EMAIL.matcher(e).matches()) return "That does not look like an email address";
        return null;
    }

    /** What is still owed on an invoice: the total less what was paid and credited, never below zero. */
    public static BigDecimal outstanding(BigDecimal total, BigDecimal paid, BigDecimal credited) {
        BigDecimal t = total == null ? BigDecimal.ZERO : total;
        BigDecimal left = t.subtract(paid == null ? BigDecimal.ZERO : paid).subtract(credited == null ? BigDecimal.ZERO : credited);
        return left.signum() < 0 ? BigDecimal.ZERO : left.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** The first few billing dates still to come, so a client can see what is expected without a long list. */
    public static boolean upcoming(LocalDate date, LocalDate today) { return date != null && !date.isBefore(today); }
}
