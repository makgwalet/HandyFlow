package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.VisitMappingDtos.Entry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** What may be saved as the list of question groups for a visit type. Pure, so it is tested without a database. */
final class VisitMappingRules {
    static final int MAX_GROUPS = 30;
    private static final Pattern VISIT_TYPE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,49}$");
    private static final Pattern GROUP_CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,59}$");

    private VisitMappingRules() {}

    static String visitType(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase();
        if (!VISIT_TYPE.matcher(v).matches()) throw new IllegalArgumentException("That is not a valid visit type.");
        return v;
    }

    /** The entries with codes trimmed, in the order given. Throws with a plain-language message on the first problem. */
    static List<Entry> check(List<Entry> entries) {
        if (entries == null) throw new IllegalArgumentException("Send the list of groups (it may be empty).");
        if (entries.size() > MAX_GROUPS) throw new IllegalArgumentException("A visit type can open at most " + MAX_GROUPS + " groups.");
        Set<String> seen = new HashSet<>();
        return entries.stream().map(e -> {
            String code = e == null || e.groupCode() == null ? "" : e.groupCode().trim();
            if (!GROUP_CODE.matcher(code).matches()) throw new IllegalArgumentException("'" + code + "' is not a valid group code.");
            if (!seen.add(code)) throw new IllegalArgumentException("Group " + code + " is listed twice.");
            return new Entry(code, e.required());
        }).toList();
    }
}
