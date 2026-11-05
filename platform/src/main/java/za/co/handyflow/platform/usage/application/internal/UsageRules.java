package za.co.handyflow.platform.usage.application.internal;

/** How close a count is to its limit. Pure, so it runs without a database. */
public final class UsageRules {

    public static final String NONE = "NONE", NEAR = "NEAR_LIMIT", CLOSE = "CLOSE_TO_LIMIT", AT_LIMIT = "AT_LIMIT", NO_LIMIT = "NO_LIMIT";

    private UsageRules() {}

    /** 80% and 90% are the warning points the owner chose; at or over the limit a hard cap would block. */
    public static String level(long used, Integer limit) {
        if (limit == null || limit <= 0) return NO_LIMIT;
        if (used >= limit) return AT_LIMIT;
        if (used * 100 >= limit * 90L) return CLOSE;
        if (used * 100 >= limit * 80L) return NEAR;
        return NONE;
    }

    /** Whole percent of the limit used, or null when there is no limit. */
    public static Integer percent(long used, Integer limit) {
        return limit == null || limit <= 0 ? null : (int) Math.min(Integer.MAX_VALUE, used * 100 / limit);
    }
}
