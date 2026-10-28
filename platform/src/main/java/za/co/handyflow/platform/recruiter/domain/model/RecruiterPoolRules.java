package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Talent pools, tags and when a past candidate may be considered for a new vacancy. Pure. A candidate agreed to be considered "for this and similar
 * roles" for the company's retention period, so they can be reused only while that agreement stands.
 */
public final class RecruiterPoolRules {
    private RecruiterPoolRules() {}

    public static final int MAX_NAME = 80, MAX_DESCRIPTION = 300, MAX_NOTE = 300, MAX_TAG = 30, MAX_TAGS = 20;
    private static final Pattern TAG = Pattern.compile("[a-z0-9][a-z0-9 +#.\\-]*");

    public static String poolProblem(String name, String description) {
        if (name == null || name.trim().length() < 2) return "Give the pool a name (at least 2 characters).";
        if (name.trim().length() > MAX_NAME) return "The pool name is too long (" + MAX_NAME + " characters at most).";
        if (description != null && description.trim().length() > MAX_DESCRIPTION) return "The description is too long (" + MAX_DESCRIPTION + " characters at most).";
        return null;
    }

    public static String noteProblem(String note) {
        return note != null && note.trim().length() > MAX_NOTE ? "The note is too long (" + MAX_NOTE + " characters at most)." : null;
    }

    /** Lower case, single spaces; null when nothing is left. */
    public static String normaliseTag(String tag) {
        if (tag == null) return null;
        String t = tag.trim().toLowerCase().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /** Why this set of tags cannot be saved, or null. */
    public static String tagsProblem(List<String> tags) {
        if (tags == null) return null;
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : tags) {
            String t = normaliseTag(raw);
            if (t == null) continue;
            if (t.length() > MAX_TAG) return "A tag can be " + MAX_TAG + " characters at most.";
            if (!TAG.matcher(t).matches()) return "Tags can use letters, numbers, spaces and + # . - only.";
            seen.add(t);
        }
        return seen.size() > MAX_TAGS ? "A candidate can have " + MAX_TAGS + " tags at most." : null;
    }

    public static List<String> cleanTags(List<String> tags) {
        Set<String> seen = new LinkedHashSet<>();
        if (tags != null) for (String raw : tags) { String t = normaliseTag(raw); if (t != null) seen.add(t); }
        return List.copyOf(seen);
    }

    /** Why this past candidate cannot be considered now, or null. */
    public static String reuseProblem(boolean anonymised, Instant consentGivenAt, Instant lastActivity, int retentionMonths, Instant now) {
        if (anonymised) return "This candidate's details have been removed.";
        if (consentGivenAt == null) return "There is no record that this candidate agreed to be considered for other roles.";
        if (lastActivity != null && !RecruiterRetentionRules.dueOn(lastActivity, retentionMonths).isAfter(now))
            return "The candidate's details are past the " + retentionMonths + "-month retention period. Ask them to apply again.";
        return null;
    }
}
