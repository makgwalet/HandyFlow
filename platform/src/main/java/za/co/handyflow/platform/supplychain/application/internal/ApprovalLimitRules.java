package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for monetary approval limits.
 * <ol>
 *   <li>The person's own limit for the document type wins, then their own "*" (any document) limit.</li>
 *   <li>Otherwise each role they hold gives its type-specific limit, else its "*" limit; the most generous role counts
 *       (unlimited beats any figure).</li>
 *   <li>No limit at all = no authority.</li>
 * </ol>
 * A null max is unlimited. The amount is compared with "at or below": a document equal to the limit is approvable.
 */
public final class ApprovalLimitRules {

    private ApprovalLimitRules() {}

    public static final String ANY = "*";
    public static final String REQUISITION = "REQUISITION", PURCHASE_ORDER = "PURCHASE_ORDER", RFQ_AWARD = "RFQ_AWARD",
            PO_AMENDMENT = "PO_AMENDMENT", SUPPLIER_INVOICE = "SUPPLIER_INVOICE", PRICE_OVERRIDE = "PRICE_OVERRIDE";
    public static final List<String> DOCUMENT_TYPES = List.of(REQUISITION, PURCHASE_ORDER, RFQ_AWARD, PO_AMENDMENT, SUPPLIER_INVOICE, PRICE_OVERRIDE);

    public record Limit(String subjectType, UUID subjectId, String documentType, BigDecimal max) {}

    /** granted=false means no limit applies (no authority). max null with granted=true means unlimited. */
    public record Authority(boolean granted, BigDecimal max, String source) {
        public boolean unlimited() { return granted && max == null; }
    }

    public static boolean validDocumentType(String t) { return ANY.equals(t) || DOCUMENT_TYPES.contains(t); }

    public static Authority resolve(UUID userId, Set<UUID> roleIds, List<Limit> all, String documentType) {
        Limit own = pick(all, "USER", userId, documentType);
        if (own != null) return new Authority(true, own.max(), "a personal limit for " + label(documentType));
        Limit ownAny = pick(all, "USER", userId, ANY);
        if (ownAny != null) return new Authority(true, ownAny.max(), "a personal limit for all documents");
        boolean found = false;
        BigDecimal best = BigDecimal.ZERO;
        for (UUID role : roleIds) {
            Limit l = pick(all, "ROLE", role, documentType);
            if (l == null) l = pick(all, "ROLE", role, ANY);
            if (l == null) continue;
            found = true;
            if (l.max() == null) return new Authority(true, null, "a role limit (unlimited)");
            if (l.max().compareTo(best) > 0) best = l.max();
        }
        return found ? new Authority(true, best, "a role limit") : new Authority(false, null, "no limit set");
    }

    private static Limit pick(List<Limit> all, String subjectType, UUID subjectId, String documentType) {
        for (Limit l : all)
            if (l.subjectType().equals(subjectType) && l.subjectId().equals(subjectId) && l.documentType().equals(documentType)) return l;
        return null;
    }

    public static boolean within(Authority a, BigDecimal amount) {
        return a.granted() && (a.max() == null || amount.compareTo(a.max()) <= 0);
    }

    /** The refusal text, or null when the amount is within the person's authority. {@code others} are people who could approve it instead. */
    public static String problem(Authority a, BigDecimal amount, String documentType, List<String> others) {
        if (within(a, amount)) return null;
        String who = others == null || others.isEmpty() ? "Ask an administrator to raise your limit or have someone with a higher limit approve it."
                : "Someone with a higher limit must approve it, for example: " + String.join(", ", others) + ".";
        if (!a.granted())
            return "You have no approval limit set for " + label(documentType) + ", so you cannot approve it. " + who;
        return "This " + singular(documentType) + " is R " + money(amount) + ", above your approval limit of R " + money(a.max())
                + " for " + label(documentType) + ". " + who;
    }

    public static String singular(String documentType) {
        return switch (documentType) {
            case REQUISITION -> "requisition";
            case PURCHASE_ORDER -> "purchase order";
            case RFQ_AWARD -> "RFQ award";
            case PO_AMENDMENT -> "purchase order amendment";
            case SUPPLIER_INVOICE -> "supplier invoice";
            case PRICE_OVERRIDE -> "price increase";
            default -> "document";
        };
    }

    public static String label(String documentType) {
        return switch (documentType) {
            case REQUISITION -> "requisitions";
            case PURCHASE_ORDER -> "purchase orders";
            case RFQ_AWARD -> "RFQ awards";
            case PO_AMENDMENT -> "purchase order amendments";
            case SUPPLIER_INVOICE -> "supplier invoices";
            case PRICE_OVERRIDE -> "price increases";
            case ANY -> "all documents";
            default -> documentType.toLowerCase(Locale.ROOT);
        };
    }

    /** Switching enforcement on is only safe when somebody holds unlimited authority over everything. */
    public static boolean hasUnlimitedForEverything(List<Limit> limitsOfActiveHolders) {
        return limitsOfActiveHolders.stream().anyMatch(l -> ANY.equals(l.documentType()) && l.max() == null);
    }

    private static String money(BigDecimal v) { return String.format(Locale.ROOT, "%,.2f", v); }
}
