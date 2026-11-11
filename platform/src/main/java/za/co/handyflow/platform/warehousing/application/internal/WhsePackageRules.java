package za.co.handyflow.platform.warehousing.application.internal;

import java.util.List;

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

    /** Whether a tier applies today: from its start date to its expiry, both inclusive; no date means no bound. */
    public enum WindowState { ACTIVE, SCHEDULED, EXPIRED }

    public static WindowState windowState(java.time.LocalDate from, java.time.LocalDate to, java.time.LocalDate today) {
        if (from != null && today.isBefore(from)) return WindowState.SCHEDULED;
        if (to != null && today.isAfter(to)) return WindowState.EXPIRED;
        return WindowState.ACTIVE;
    }

    public static void checkWindow(java.time.LocalDate from, java.time.LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("The tier cannot expire before it starts.");
    }

    /** The limit once overrides in force are added. Unlimited stays unlimited. */
    public static Integer effectiveLimit(Integer base, long extra) {
        return base == null ? null : (int) Math.min(Integer.MAX_VALUE, base + extra);
    }

    public static void checkOverride(int extra, java.time.LocalDate validUntil, String reason, java.time.LocalDate today) {
        if (extra < 1 || extra > 100000) throw new IllegalArgumentException("The extra allowance must be between 1 and 100,000.");
        if (validUntil == null || validUntil.isBefore(today)) throw new IllegalArgumentException("Choose an end date that is today or later.");
        if (validUntil.isAfter(today.plusDays(366))) throw new IllegalArgumentException("An override can last at most a year; renew it if it is still needed.");
        if (reason == null || reason.strip().length() < 10) throw new IllegalArgumentException("Give a reason of at least 10 characters; it is kept in the audit trail.");
        if (reason.strip().length() > 300) throw new IllegalArgumentException("The reason can be at most 300 characters.");
    }

    /** A tier offered for upgrade: its limits in {@link Limit} order (null = unlimited). */
    public record Tier(String code, String name, java.math.BigDecimal price, int sortOrder, Integer[] limits) {}

    /** The tiers above the current one that the tenant's present usage would fit in, cheapest step first. A tier already over on usage is not offered. */
    public static List<Tier> upgradeOptions(int currentSortOrder, List<Tier> all, long[] used) {
        return all.stream().filter(t -> t.sortOrder() > currentSortOrder)
                .filter(t -> {
                    for (int i = 0; i < used.length; i++) if (t.limits()[i] != null && used[i] > t.limits()[i]) return false;
                    return true;
                })
                .sorted(java.util.Comparator.comparingInt(Tier::sortOrder)).toList();
    }
}
