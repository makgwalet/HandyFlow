package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The rules for a company's own inspection checklists. A checklist is a list of questions; each question is a pass/fail check, a number
 * with an allowed range, a note, or a photo reference. The built-in walk-round is the minimum for the pre-use, handover and return
 * types: a company can add to it, never take from it. One UNSAFE answer makes the whole inspection UNSAFE, as before.
 */
public final class ChecklistRules {
    private ChecklistRules() {}

    public static final String PASS_FAIL = "PASS_FAIL";
    public static final String NUMERIC = "NUMERIC";
    public static final String TEXT = "TEXT";
    public static final String PHOTO = "PHOTO";
    public static final Set<String> ANSWER_TYPES = Set.of(PASS_FAIL, NUMERIC, TEXT, PHOTO);

    /** The types that always carry the built-in walk-round. */
    public static final Set<String> MINIMUM_TYPES = Set.of(InspectionRules.PRE_USE, InspectionRules.HANDOVER, InspectionRules.RETURN);
    public static final int MAX_QUESTIONS = 60;
    public static final int MAX_TEXT = 80;

    public record Question(UUID id, String section, String text, String answerType, boolean required, boolean builtIn,
                           BigDecimal min, BigDecimal max, String unit, String failOutcome) {}

    /** The checklist an inspection is answered against. {@code versionId} is null for the built-in list. */
    public record Active(UUID versionId, Integer versionNo, String type, boolean requiresSignoff, LocalDate effectiveFrom, List<Question> questions) {
        public boolean builtInDefault() { return versionId == null; }
    }

    public record Given(String item, String outcome, String comment, BigDecimal number, String text, String evidence) {}

    public record Checked(Question question, String outcome, String comment, BigDecimal number, String text, String evidence) {}

    public static Question builtInQuestion(String text) {
        return new Question(null, "Walk-round", text, PASS_FAIL, true, true, null, null, null, InspectionRules.DEFECT);
    }

    public static List<Question> builtIn() { return InspectionRules.CHECKLIST.stream().map(ChecklistRules::builtInQuestion).toList(); }

    public static Active builtInActive(String type) { return new Active(null, null, type, false, null, builtIn()); }

    public static String answerType(String t) {
        String v = t == null || t.isBlank() ? PASS_FAIL : t.trim().toUpperCase(Locale.ROOT);
        if (!ANSWER_TYPES.contains(v)) throw new IllegalArgumentException("A question is answered with PASS_FAIL, NUMERIC, TEXT or PHOTO.");
        return v;
    }

    private static boolean isBuiltInText(String text) {
        return InspectionRules.CHECKLIST.stream().anyMatch(b -> b.equalsIgnoreCase(text));
    }

    /**
     * Cleans a draft's questions: trimmed text, no duplicates, a sensible range on number questions, and, for the types that carry the
     * built-in walk-round, every built-in item still there as a required pass/fail question.
     */
    public static List<Question> clean(String type, List<Question> given) {
        if (given == null || given.isEmpty()) throw new IllegalArgumentException("A checklist needs at least one question.");
        if (given.size() > MAX_QUESTIONS) throw new IllegalArgumentException("A checklist can have at most " + MAX_QUESTIONS + " questions.");
        List<Question> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Question q : given) {
            String text = q.text() == null ? "" : q.text().trim();
            if (text.isEmpty()) throw new IllegalArgumentException("Every question needs its wording.");
            if (text.length() > MAX_TEXT) throw new IllegalArgumentException("\"" + text.substring(0, 30) + "...\" is too long: keep a question to " + MAX_TEXT + " characters.");
            if (!seen.add(text.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("\"" + text + "\" is on the checklist twice.");
            String at = answerType(q.answerType());
            String fail = q.failOutcome() == null || q.failOutcome().isBlank() ? InspectionRules.DEFECT : q.failOutcome().trim().toUpperCase(Locale.ROOT);
            if (!InspectionRules.DEFECT.equals(fail) && !InspectionRules.UNSAFE.equals(fail))
                throw new IllegalArgumentException("A number outside its range is a DEFECT or UNSAFE.");
            BigDecimal min = null, max = null;
            String unit = null;
            if (NUMERIC.equals(at)) {
                min = q.min();
                max = q.max();
                if (min == null && max == null) throw new IllegalArgumentException("\"" + text + "\": give the lowest or the highest allowed value.");
                if (min != null && max != null && min.compareTo(max) > 0) throw new IllegalArgumentException("\"" + text + "\": the lowest value is above the highest.");
                unit = q.unit() == null || q.unit().isBlank() ? null : q.unit().trim();
                if (unit != null && unit.length() > 12) throw new IllegalArgumentException("\"" + text + "\": keep the unit to 12 characters.");
            }
            boolean builtIn = isBuiltInText(text);
            if (builtIn && !PASS_FAIL.equals(at)) throw new IllegalArgumentException("\"" + text + "\" is part of the built-in walk-round and stays a pass/fail check.");
            if (builtIn && !q.required()) throw new IllegalArgumentException("\"" + text + "\" is part of the built-in walk-round and stays required.");
            String section = q.section() == null || q.section().isBlank() ? null : q.section().trim();
            if (section != null && section.length() > 60) throw new IllegalArgumentException("Keep a section name to 60 characters.");
            out.add(new Question(q.id(), section, builtIn ? InspectionRules.CHECKLIST.stream().filter(b -> b.equalsIgnoreCase(text)).findFirst().orElse(text) : text,
                    at, q.required(), builtIn, min, max, unit, NUMERIC.equals(at) ? fail : InspectionRules.DEFECT));
        }
        if (MINIMUM_TYPES.contains(type)) {
            for (String b : InspectionRules.CHECKLIST)
                if (out.stream().noneMatch(x -> x.text().equalsIgnoreCase(b)))
                    throw new IllegalArgumentException("A " + type.toLowerCase(Locale.ROOT).replace('_', '-') + " checklist keeps the built-in walk-round: \"" + b + "\" is missing.");
        }
        return out;
    }

    /** A version cannot start in the past: inspections already done must not appear to have used it. */
    public static LocalDate effectiveFrom(LocalDate requested, LocalDate today) {
        LocalDate d = requested == null ? today : requested;
        if (d.isBefore(today)) throw new IllegalArgumentException("A checklist cannot start before today.");
        return d;
    }

    public static void checkSignoff(boolean required, String name) {
        if (required && (name == null || name.trim().length() < 3))
            throw new IllegalArgumentException("This checklist needs the inspector's name as a sign-off.");
    }

    /** Matches the answers to the questions and returns them in checklist order, or throws saying what is wrong or missing. */
    public static List<Checked> check(List<Question> questions, List<Given> given) {
        if (given == null) given = List.of();
        for (Given g : given) {
            String name = g.item() == null ? "" : g.item().trim();
            if (questions.stream().noneMatch(q -> q.text().equalsIgnoreCase(name)))
                throw new IllegalArgumentException("\"" + name + "\" is not on this checklist.");
        }
        List<Checked> out = new ArrayList<>();
        for (Question q : questions) {
            Given g = given.stream().filter(x -> x.item() != null && x.item().trim().equalsIgnoreCase(q.text())).findFirst().orElse(null);
            String comment = g == null || g.comment() == null || g.comment().isBlank() ? null : g.comment().trim();
            switch (q.answerType()) {
                case NUMERIC -> {
                    BigDecimal n = g == null ? null : g.number();
                    if (n == null) {
                        if (q.required()) throw new IllegalArgumentException("Enter a value for \"" + q.text() + "\".");
                        continue;
                    }
                    boolean out_ = (q.min() != null && n.compareTo(q.min()) < 0) || (q.max() != null && n.compareTo(q.max()) > 0);
                    String outcome = out_ ? q.failOutcome() : InspectionRules.OK;
                    if (out_ && (comment == null || comment.length() < 5))
                        throw new IllegalArgumentException("\"" + q.text() + "\" is outside " + range(q) + ": say what was seen (at least 5 characters).");
                    out.add(new Checked(q, outcome, comment, n, null, null));
                }
                case TEXT -> {
                    String t = g == null || g.text() == null || g.text().isBlank() ? null : g.text().trim();
                    if (t == null) {
                        if (q.required()) throw new IllegalArgumentException("Write an answer for \"" + q.text() + "\".");
                        continue;
                    }
                    out.add(new Checked(q, InspectionRules.OK, comment, null, t, null));
                }
                case PHOTO -> {
                    String e = g == null || g.evidence() == null || g.evidence().isBlank() ? null : g.evidence().trim();
                    if (e == null) {
                        if (q.required()) throw new IllegalArgumentException("Attach the photo for \"" + q.text() + "\".");
                        continue;
                    }
                    if (e.length() > 200) throw new IllegalArgumentException("Keep the photo reference to 200 characters.");
                    out.add(new Checked(q, InspectionRules.OK, comment, null, null, e));
                }
                default -> {
                    if (g == null || g.outcome() == null || g.outcome().isBlank()) {
                        if (q.required()) throw new IllegalArgumentException("Answer every item on the checklist: \"" + q.text() + "\" is missing.");
                        continue;
                    }
                    String o = InspectionRules.outcome(g.outcome());
                    if (!InspectionRules.OK.equals(o) && (comment == null || comment.length() < 5))
                        throw new IllegalArgumentException("Say what was seen on \"" + q.text() + "\" (at least 5 characters).");
                    out.add(new Checked(q, o, comment, null, null, null));
                }
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Answer every item on the checklist.");
        return out;
    }

    public static String overall(List<Checked> answers) {
        if (answers.stream().anyMatch(a -> InspectionRules.UNSAFE.equals(a.outcome()))) return InspectionRules.UNSAFE;
        if (answers.stream().anyMatch(a -> InspectionRules.DEFECT.equals(a.outcome()))) return InspectionRules.DEFECTS;
        return InspectionRules.PASS;
    }

    private static String range(Question q) {
        String u = q.unit() == null ? "" : " " + q.unit();
        if (q.min() != null && q.max() != null) return q.min().stripTrailingZeros().toPlainString() + " to " + q.max().stripTrailingZeros().toPlainString() + u;
        if (q.min() != null) return "at least " + q.min().stripTrailingZeros().toPlainString() + u;
        return "at most " + q.max().stripTrailingZeros().toPlainString() + u;
    }
}
