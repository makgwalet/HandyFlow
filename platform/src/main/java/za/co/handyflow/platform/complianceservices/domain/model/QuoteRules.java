package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Pure rules for quotes: which discount needs whom, how long a quote stands, and what a total is made of. */
public final class QuoteRules {

    private QuoteRules() {}

    public static final String DRAFT = "DRAFT", PENDING_APPROVAL = "PENDING_APPROVAL", APPROVED = "APPROVED", SENT = "SENT",
            ACCEPTED = "ACCEPTED", DECLINED = "DECLINED", WITHDRAWN = "WITHDRAWN", EXPIRED = "EXPIRED";

    /** Statuses stored on a quote. EXPIRED is never stored: it is a sent quote that has passed its date. */
    public static final Set<String> STATUSES = Set.of(DRAFT, PENDING_APPROVAL, APPROVED, SENT, ACCEPTED, DECLINED, WITHDRAWN);
    public static final Set<String> CAN_WITHDRAW = Set.of(DRAFT, PENDING_APPROVAL, APPROVED, SENT);
    public static final List<String> APPROVER_ORDER = List.of("CONSULTANT", "COMPLIANCE_MANAGER", "PRACTICE_OWNER", "PRACTICE_OWNER_WITH_EXCEPTION");
    public static final int MIN_NOTE = 10;
    public static final int DEFAULT_VALID_DAYS = 30;
    public static final int MAX_VALID_DAYS = 365;

    public record Totals(BigDecimal professional, BigDecimal discount, BigDecimal disbursements, BigDecimal vat, BigDecimal total, BigDecimal vatRatePct) {}

    /** One priced line, as far as a quote total is concerned. */
    public record Priced(BigDecimal professional, BigDecimal discount, BigDecimal disbursements, BigDecimal vat, BigDecimal total, BigDecimal vatRatePct) {}

    public static Totals sum(List<Priced> lines) {
        BigDecimal p = BigDecimal.ZERO, d = BigDecimal.ZERO, x = BigDecimal.ZERO, v = BigDecimal.ZERO, t = BigDecimal.ZERO, rate = BigDecimal.ZERO;
        for (Priced l : lines) {
            p = p.add(l.professional());
            d = d.add(l.discount());
            x = x.add(l.disbursements());
            v = v.add(l.vat());
            t = t.add(l.total());
            if (l.vatRatePct() != null) rate = l.vatRatePct();
        }
        return new Totals(two(p), two(d), two(x), two(v), two(t), rate);
    }

    private static BigDecimal two(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    /** The strictest approver any line's discount calls for. */
    public static String requiredApprover(List<BigDecimal> discountPcts) {
        String top = "CONSULTANT";
        for (BigDecimal p : discountPcts) {
            String a = PricingRules.discountApprover(p);
            if (APPROVER_ORDER.indexOf(a) > APPROVER_ORDER.indexOf(top)) top = a;
        }
        return top;
    }

    public static boolean needsApproval(String requiredApprover) { return !"CONSULTANT".equals(requiredApprover); }

    public static LocalDate defaultValidUntil(LocalDate today) { return today.plusDays(DEFAULT_VALID_DAYS); }

    /** Null when the date is fine. */
    public static String validUntilProblem(LocalDate validUntil, LocalDate today) {
        if (validUntil == null) return "Say how long the quote stands";
        if (validUntil.isBefore(today)) return "A quote cannot stand until a date that has passed";
        if (validUntil.isAfter(today.plusDays(MAX_VALID_DAYS))) return "A quote can stand for at most " + MAX_VALID_DAYS + " days";
        return null;
    }

    /** What a person should be told the quote is today. A sent quote past its date has expired. */
    public static String effectiveStatus(String status, LocalDate validUntil, LocalDate today) {
        return SENT.equals(status) && validUntil != null && validUntil.isBefore(today) ? EXPIRED : status;
    }

    public static String note(String raw, String what, int max) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() < MIN_NOTE) throw new IllegalArgumentException(what + " needs at least " + MIN_NOTE + " characters");
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static String breakdownText(List<PricingRules.Part> parts) {
        StringBuilder sb = new StringBuilder();
        for (PricingRules.Part p : parts) sb.append(p.label().replace('\t', ' ').replace('\n', ' ')).append('\t').append(p.amount().toPlainString()).append('\n');
        return sb.toString();
    }

    /** Reads the stored breakdown back into its labels and amounts. */
    public static List<PricingRules.Part> parseBreakdown(String text) {
        List<PricingRules.Part> out = new java.util.ArrayList<>();
        if (text == null) return out;
        for (String row : text.split("\n")) {
            int i = row.lastIndexOf('\t');
            if (i <= 0) continue;
            try {
                out.add(new PricingRules.Part(row.substring(0, i), new BigDecimal(row.substring(i + 1))));
            } catch (NumberFormatException ignored) {
                // a row we cannot read is left out rather than guessed
            }
        }
        return out;
    }
}
