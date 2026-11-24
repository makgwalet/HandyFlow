package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a service in the practice's catalogue may say. Pure, so it is easy to test. A service names the pricing model and
 * billing trigger it uses but carries no price: pricing is a separate, effective-dated concern.
 */
public final class ServiceCatalogueRules {

    private ServiceCatalogueRules() {}

    public static final Set<String> CATEGORIES = Set.of("ONBOARDING", "CIPC", "SARS", "POPIA", "PAIA", "BBBEE", "OHS", "EMPLOYMENT", "LICENCE",
            "TENDER_READINESS", "MONITORING", "AUDIT_PREPARATION", "OTHER");
    public static final Set<String> PRICING_MODELS = Set.of("FIXED", "HOURLY", "PER_DOCUMENT", "PER_REGISTRATION", "PER_SITE", "PER_EMPLOYEE",
            "RETAINER", "PROJECT", "HYBRID");
    public static final Set<String> BILLING_TRIGGERS = Set.of("ON_ACCEPTANCE", "ON_DELIVERY", "MONTHLY", "ON_MILESTONE");
    public static final Set<String> ITEM_KINDS = Set.of("DELIVERABLE", "CHECKLIST", "DOCUMENT");
    public static final BigDecimal MAX_HOURS = BigDecimal.valueOf(2000);

    public record Item(String kind, String text, boolean mandatory) {}

    public static Item item(String kind, String text, boolean mandatory) {
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if (!ITEM_KINDS.contains(k)) throw new IllegalArgumentException("An item must be one of " + ITEM_KINDS.stream().sorted().toList());
        String t = text == null ? "" : text.trim();
        if (t.length() < 3) throw new IllegalArgumentException("Describe the item in at least 3 characters");
        if (t.length() > 300) throw new IllegalArgumentException("An item can be at most 300 characters");
        return new Item(k, t, mandatory);
    }

    /** Two of the same kind with the same wording are a mistake. */
    public static void noDuplicates(List<Item> items) {
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (Item i : items) {
            if (!seen.add(i.kind() + "|" + i.text().toLowerCase())) throw new IllegalArgumentException("\"" + i.text() + "\" is listed twice");
        }
    }

    public static BigDecimal hours(BigDecimal raw) {
        if (raw == null) return null;
        BigDecimal h = raw.setScale(2, RoundingMode.HALF_UP);
        if (h.signum() <= 0 || h.compareTo(MAX_HOURS) > 0) throw new IllegalArgumentException("Estimated hours must be more than 0 and at most 2000");
        return h;
    }

    public static Integer slaDays(Integer raw) {
        if (raw != null && (raw < 1 || raw > 365)) throw new IllegalArgumentException("The turnaround must be 1 to 365 days");
        return raw;
    }

    /** What a version must say before anyone is asked to review it. */
    public static List<String> missingForReview(String name, String category, String pricingModel, String billingTrigger, String responsibleRole,
                                                BigDecimal hours, long deliverables, int versionNumber, String changeSummary) {
        List<String> m = new ArrayList<>();
        if (name == null) m.add("name");
        if (category == null) m.add("category");
        if (pricingModel == null) m.add("pricing model");
        if (billingTrigger == null) m.add("billing trigger");
        if (responsibleRole == null) m.add("responsible role");
        if (hours == null) m.add("estimated hours");
        if (deliverables < 1) m.add("at least one deliverable");
        if (versionNumber > 1 && changeSummary == null) m.add("what changed");
        return m;
    }
}
