package za.co.handyflow.platform.clinic.application.internal;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Merge fields in letter templates, such as {{patient.name}}. Pure. */
final class LetterMerge {
    private LetterMerge() {}

    static final List<String> FIELDS = List.of("patient.name", "patient.firstName", "patient.dob", "visit.date", "visit.reason",
            "doctor.name", "practice.name", "today");

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z.]+)\\s*}}");

    /** The merge fields used in the text that this system does not know, in order of first use. */
    static List<String> unknown(String text) {
        Set<String> bad = new LinkedHashSet<>();
        if (text == null) return List.of();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) if (!FIELDS.contains(m.group(1))) bad.add(m.group(1));
        return List.copyOf(bad);
    }

    /** Fills each known field; a field with no value becomes a dash so the gap is visible, never an empty hole. */
    static String render(String text, Map<String, String> values) {
        if (text == null) return null;
        Matcher m = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v == null || v.isBlank() ? "—" : v));
        }
        m.appendTail(out);
        return out.toString();
    }
}
