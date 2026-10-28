package za.co.handyflow.platform.recruiter.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The voluntary equal-opportunity declaration and how it may be reported. Pure. The categories are those used for employment equity reporting in
 * South Africa. Nothing here is used to judge a candidate; it is only counted, and a group smaller than {@link #MIN_GROUP} is never shown as a
 * number, so a person cannot be picked out of a small group.
 */
public final class RecruiterDiversityRules {
    private RecruiterDiversityRules() {}

    public static final int MIN_GROUP = 5;
    public static final Map<String, String> GENDERS = new LinkedHashMap<>();
    public static final Map<String, String> RACES = new LinkedHashMap<>();
    public static final Map<String, String> DISABILITY = new LinkedHashMap<>();
    static {
        GENDERS.put("FEMALE", "Female"); GENDERS.put("MALE", "Male"); GENDERS.put("OTHER", "Another gender"); GENDERS.put("PREFER_NOT_TO_SAY", "Prefer not to say");
        RACES.put("AFRICAN", "African"); RACES.put("COLOURED", "Coloured"); RACES.put("INDIAN", "Indian"); RACES.put("WHITE", "White");
        RACES.put("OTHER", "Another group"); RACES.put("PREFER_NOT_TO_SAY", "Prefer not to say");
        DISABILITY.put("YES", "Has a disability"); DISABILITY.put("NO", "No disability"); DISABILITY.put("PREFER_NOT_TO_SAY", "Prefer not to say");
    }

    public static String normalise(String v) {
        if (v == null) return null;
        String t = v.trim().toUpperCase();
        return t.isEmpty() ? null : t;
    }

    /** Null when each answer given is one of the choices. Every answer is optional. */
    public static String problem(String gender, String race, String disability) {
        String g = normalise(gender), r = normalise(race), d = normalise(disability);
        if (g != null && !GENDERS.containsKey(g)) return "Choose one of the options given for gender, or leave it blank.";
        if (r != null && !RACES.containsKey(r)) return "Choose one of the options given for race, or leave it blank.";
        if (d != null && !DISABILITY.containsKey(d)) return "Choose one of the options given for disability, or leave it blank.";
        return null;
    }

    public static boolean isEmpty(String gender, String race, String disability) {
        return normalise(gender) == null && normalise(race) == null && normalise(disability) == null;
    }

    /** A count for the report: the number, or "<5" when the group is too small to show. */
    public static Object cell(long n) { return n < MIN_GROUP ? "<" + MIN_GROUP : (Object) n; }

    /** A row of counts for the report. When the group itself is too small, nothing about it is shown. */
    public static Object[] row(long applications, long interviewed, long offered, long hired) {
        if (applications < MIN_GROUP) return new Object[]{cell(applications), cell(applications), cell(applications), cell(applications)};
        return new Object[]{applications, cell(interviewed), cell(offered), cell(hired)};
    }
}
