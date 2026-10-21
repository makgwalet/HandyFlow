package za.co.handyflow.platform.hr.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for HR letters: which merge fields exist, how a template is filled in, and when a letter may be issued. Pure functions so they
 * can be tested without a database. Nothing here decides what a letter should say; that is the template's wording, which HR owns.
 */
public final class LetterRules {

    private LetterRules() {}

    public static final String DRAFT = "DRAFT", ISSUED = "ISSUED", VOID = "VOID";

    public static final List<String> CATEGORIES = List.of("EMPLOYMENT", "DISCIPLINARY", "GRIEVANCE", "INCAPACITY", "GENERAL");

    /** A merge field a template may use. Fields in the group "To type in" are never filled automatically; HR types them into the draft. */
    public record Field(String key, String label, String group) {}

    public static final String TYPE_IN = "To type in";

    public static final List<Field> FIELDS = List.of(
            new Field("company.name", "Company name", "Company"),
            new Field("today", "Today's date", "Company"),
            new Field("employee.name", "Employee's full name", "Employee"),
            new Field("employee.firstName", "Employee's first name", "Employee"),
            new Field("employee.lastName", "Employee's surname", "Employee"),
            new Field("employee.number", "Employee number", "Employee"),
            new Field("employee.jobTitle", "Job title", "Employee"),
            new Field("employee.department", "Department", "Employee"),
            new Field("employee.startDate", "Start date", "Employee"),
            new Field("employee.endDate", "Last day of employment", "Employee"),
            new Field("case.number", "Case number", "Case"),
            new Field("case.title", "Case title", "Case"),
            new Field("case.category", "Case category", "Case"),
            new Field("case.incidentDate", "Incident date", "Case"),
            new Field("case.description", "What happened", "Case"),
            new Field("charges", "Charges (numbered list)", "Case"),
            new Field("hearing.date", "Hearing or meeting date", "Hearing"),
            new Field("hearing.time", "Hearing or meeting time", "Hearing"),
            new Field("hearing.venue", "Venue", "Hearing"),
            new Field("hearing.chair", "Chair", "Hearing"),
            new Field("hearing.initiator", "Initiator", "Hearing"),
            new Field("hearing.noticeHours", "Notice period in hours (company policy)", "Hearing"),
            new Field("outcome", "Outcome", "Outcome"),
            new Field("outcome.date", "Date of the outcome", "Outcome"),
            new Field("outcome.reasons", "Reasons for the outcome", "Outcome"),
            new Field("warning.months", "Months the warning stays valid", "Outcome"),
            new Field("warning.validUntil", "Warning valid until", "Outcome"),
            new Field("appeal.days", "Days allowed to appeal (company policy)", "Outcome"),
            new Field("appeal.deadline", "Appeal deadline", "Outcome"),
            new Field("appeal.outcome", "Appeal result", "Outcome"),
            new Field("appeal.reasons", "Reasons for the appeal decision", "Outcome"),
            new Field("ccma.deadline", "Last day to refer a dismissal to the CCMA", "Outcome"),
            new Field("suspension.from", "Suspension from", "Suspension"),
            new Field("suspension.to", "Suspension until", "Suspension"),
            new Field("suspension.reason", "Reason for suspension", "Suspension"),
            new Field("manual.remuneration", "Remuneration (type in)", TYPE_IN),
            new Field("manual.reason", "Reason (type in)", TYPE_IN),
            new Field("manual.details", "Details (type in)", TYPE_IN),
            new Field("manual.address", "Employer's address (type in)", TYPE_IN),
            new Field("manual.sectoral", "Council or sectoral standard that covers the business (type in, or write none)", TYPE_IN));

    private static final Map<String, Field> BY_KEY = new LinkedHashMap<>();
    static { for (Field f : FIELDS) BY_KEY.put(f.key(), f); }

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");
    private static final Pattern MARKER = Pattern.compile("\\[\\[([^\\[\\]]{1,120})]]");

    public static boolean isKnown(String key) { return BY_KEY.containsKey(key); }

    public static boolean isTypeIn(String key) { return key != null && key.startsWith("manual."); }

    public static String labelOf(String key) { Field f = BY_KEY.get(key); return f == null ? key : f.label(); }

    /** Every merge field named in the text, in order of first use. */
    public static Set<String> fieldsIn(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    public static List<String> unknownFields(String text) {
        List<String> out = new ArrayList<>();
        for (String k : fieldsIn(text)) if (!isKnown(k)) out.add(k);
        return out;
    }

    /** Null when the template can be saved. */
    public static String templateProblem(String name, String category, String subject, String body) {
        if (name == null || name.isBlank()) return "Give the template a name.";
        if (name.trim().length() > 120) return "The template name is too long (120 characters at most).";
        if (category == null || !CATEGORIES.contains(category)) return "Choose a category.";
        if (subject == null || subject.isBlank()) return "Give the template a subject line.";
        if (subject.length() > 200) return "The subject is too long (200 characters at most).";
        if (body == null || body.isBlank()) return "The template has no wording.";
        if (body.length() > 20000) return "The wording is too long (20 000 characters at most).";
        if (subject.contains("[[") || subject.contains("]]") || body.contains("[[") || body.contains("]]"))
            return "Square double brackets [[ ]] are used to mark gaps in a draft. Use {{field}} for merge fields.";
        List<String> unknown = new ArrayList<>(unknownFields(subject));
        unknown.addAll(unknownFields(body));
        if (!unknown.isEmpty()) return "These merge fields do not exist: " + String.join(", ", new LinkedHashSet<>(unknown)) + ".";
        String open = leftoverBraces(subject);
        if (open == null) open = leftoverBraces(body);
        if (open != null) return open;
        return null;
    }

    private static String leftoverBraces(String text) {
        String stripped = TOKEN.matcher(text).replaceAll("");
        if (stripped.contains("{{") || stripped.contains("}}")) return "A merge field is not written correctly. Write it as {{employee.name}}.";
        return null;
    }

    /** The text with merge fields filled in, and the fields that had no value. A gap is marked [[Label]] so it cannot be missed. */
    public record Rendered(String text, List<String> missing) {}

    public static Rendered render(String text, Map<String, String> values) {
        List<String> missing = new ArrayList<>();
        if (text == null) return new Rendered("", missing);
        Matcher m = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String v = values.get(key);
            String piece;
            if (v == null || v.isBlank()) {
                piece = "[[" + labelOf(key) + "]]";
                if (!missing.contains(key)) missing.add(key);
            } else {
                piece = v.replace("[[", "[ [").replace("]]", "] ]");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(piece));
        }
        m.appendTail(sb);
        return new Rendered(sb.toString(), missing);
    }

    /** Gaps still marked in the text, as the label inside each marker. */
    public static List<String> gaps(String... texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : texts) {
            if (t == null) continue;
            Matcher m = MARKER.matcher(t);
            while (m.find()) out.add(m.group(1));
        }
        return new ArrayList<>(out);
    }

    /** Null when a draft's wording can be saved. A draft is final text, so it must not still contain merge fields. */
    public static String draftProblem(String subject, String body) {
        if (subject == null || subject.isBlank()) return "Give the letter a subject.";
        if (subject.length() > 200) return "The subject is too long (200 characters at most).";
        if (body == null || body.isBlank()) return "The letter has no wording.";
        if (body.length() > 20000) return "The letter is too long (20 000 characters at most).";
        if (TOKEN.matcher(subject).find() || TOKEN.matcher(body).find()) return "The letter still contains {{merge fields}}. A draft is the final wording.";
        return null;
    }

    /** Null when a draft may be issued. */
    public static String issueProblem(String status, String subject, String body) {
        if (!DRAFT.equals(status)) return "Only a draft can be issued.";
        String p = draftProblem(subject, body);
        if (p != null) return p;
        List<String> gaps = gaps(subject, body);
        if (!gaps.isEmpty()) return "Fill in the gaps before issuing: " + String.join(", ", gaps) + ".";
        return null;
    }

    public static String voidProblem(String status, String reason) {
        if (VOID.equals(status)) return "This letter is already void.";
        if (reason == null || reason.trim().length() < 3) return "Give the reason for voiding the letter.";
        if (reason.trim().length() > 500) return "The reason is too long (500 characters at most).";
        return null;
    }

    public static boolean canRead(Set<String> held) { return held != null && held.contains("HR_LETTER_READ"); }

    public static boolean canManage(Set<String> held) { return held != null && held.contains("HR_LETTER_MANAGE"); }

    public static boolean canIssue(Set<String> held) { return held != null && held.contains("HR_LETTER_ISSUE"); }

    public static boolean mayEdit(String status) { return DRAFT.equals(status); }

    /** Whether the employee can still confirm receipt. */
    public static boolean mayAcknowledge(String status, boolean requiresAck, Object acknowledgedAt) {
        return ISSUED.equals(status) && requiresAck && acknowledgedAt == null;
    }

    /** The buttons HR is offered for a letter in this state. */
    public static List<String> actions(String status) {
        return switch (status) {
            case DRAFT -> List.of("EDIT", "ISSUE", "VOID");
            case ISSUED -> List.of("DOWNLOAD", "VOID");
            default -> List.of();
        };
    }

    // ── Wording helpers shared by the context builder ─────────────────────────────────────────────

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
            "November", "December"};

    /** "5 October 2026". Does not depend on the server's locale. */
    public static String longDate(LocalDate d) {
        return d == null ? null : d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
    }

    /** "Final written warning" from FINAL_WRITTEN_WARNING. */
    public static String words(String code) {
        if (code == null || code.isBlank()) return null;
        String t = code.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** A lines-of-text numbered list: "1. First\n2. Second". */
    public static String numbered(List<String> items) {
        if (items == null || items.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(i + 1).append(". ").append(items.get(i));
        }
        return sb.toString();
    }

    /** A short readable code from a name, for a template HR writes themselves. */
    public static String codeFrom(String name, Set<String> taken) {
        String base = name == null ? "" : name.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "LETTER";
        if (base.length() > 30) base = base.substring(0, 30).replaceAll("_+$", "");
        String code = base;
        int n = 2;
        while (taken.contains(code)) code = base + "_" + n++;
        return code;
    }
}
