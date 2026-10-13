package za.co.handyflow.platform.clinic.application.internal;

/**
 * One standing document per source (patch 0203): a visit has one prescription and one visit summary, a consent event one consent record.
 * Downloading again returns the copy already issued, so the same document never circulates under two numbers. Pure.
 */
public final class IssueOnceRules {

    private IssueOnceRules() {}

    /**
     * What to do when the content changed since the standing copy was issued.
     * SUPERSEDE: replace it, withdrawing the old one (a visit summary after an addendum).
     * ASK_FOR_REASON: refuse until someone reissues with a reason (a prescription: two valid scripts must never be in circulation).
     */
    public enum WhenChanged { SUPERSEDE, ASK_FOR_REASON }

    public enum Action {
        /** Nothing issued yet: issue. */
        ISSUE,
        /** The standing copy still matches: hand it back, no new number. */
        REUSE,
        /** Withdraw the standing copy and issue a new one. */
        REPLACE,
        /** Content changed and a reason is needed first. */
        REFUSE
    }

    /**
     * @param standing        a copy of this type for this source is issued and not withdrawn
     * @param sameContent     the data it was made from equals the data now
     * @param storedCopy      its PDF was kept (documents issued before patch 0203 were not)
     * @param reissueReason   a reason was given for a deliberate reissue
     */
    public static Action decide(boolean standing, boolean sameContent, boolean storedCopy, WhenChanged policy, boolean reissueReason) {
        if (!standing) return Action.ISSUE;
        if (reissueReason) return Action.REPLACE;
        if (sameContent) return storedCopy ? Action.REUSE : Action.REPLACE;
        return policy == WhenChanged.SUPERSEDE ? Action.REPLACE : Action.REFUSE;
    }

    /** A reissue reason must say something: 5 to 500 characters once trimmed. Null or blank means "no reissue asked for". */
    public static String reason(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String r = raw.trim();
        if (r.length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters");
        if (r.length() > 500) throw new IllegalArgumentException("Keep the reason under 500 characters");
        return r;
    }

    /** Why the earlier copy was withdrawn, as the public check and the register will show it. */
    public static String replacedBecause(String label, String reason) {
        return reason != null ? "Reissued: " + reason : label + " replaced by an updated copy";
    }
}
