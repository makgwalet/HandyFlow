package za.co.handyflow.platform.warehousing.application.internal;

/** Package limit rules. Pure: no database, so the wording and the decision are testable on their own. */
public final class WhsePackageRules {
    private WhsePackageRules() {}

    public enum Enforcement { OFF, MONITOR, ENFORCE }

    public enum Limit {
        WAREHOUSES("warehouses"), CLIENT_ACCOUNTS("3PL client accounts"), MONTHLY_TRANSACTIONS("transactions this billing month"), WEBHOOKS("webhooks");
        public final String label;
        Limit(String label) { this.label = label; }
    }

    public static Enforcement parseEnforcement(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Choose OFF, MONITOR or ENFORCE.");
        try { return Enforcement.valueOf(s.strip().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enforcement must be OFF, MONITOR or ENFORCE."); }
    }

    /** True when adding one more would go past the limit. A null limit means unlimited. */
    public static boolean wouldExceed(long current, Integer max) {
        return max != null && current + 1 > max;
    }

    /** True when usage already exceeds the limit (for display). */
    public static boolean isOver(long current, Integer max) {
        return max != null && current > max;
    }

    /** Refuses only when enforcement is ENFORCE. */
    public static void checkAdd(Limit limit, long current, Integer max, Enforcement mode, String packageName) {
        if (mode != Enforcement.ENFORCE || !wouldExceed(current, max)) return;
        throw new IllegalStateException("Your " + packageName + " package allows " + max + " " + limit.label
                + " and you already have " + current + ". Ask your administrator to move to a larger package.");
    }
}
