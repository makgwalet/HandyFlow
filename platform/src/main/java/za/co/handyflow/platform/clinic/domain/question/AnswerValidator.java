package za.co.handyflow.platform.clinic.domain.question;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks that a stored answer has the right shape for its question's type. Value shapes:
 * <ul>
 *   <li>YES_NO, TOGGLE: boolean</li>
 *   <li>SINGLE_SELECT, RADIO_GROUP: one option value (string)</li>
 *   <li>MULTI_SELECT, CHECKLIST: list of option values</li>
 *   <li>NUMBER: whole number; DECIMAL: any number; both within min/max when set</li>
 *   <li>TEXT: string up to 500 characters; LONG_TEXT: up to 5000</li>
 *   <li>DATE: ISO date (2026-10-07); DATE_TIME: ISO local or offset date-time</li>
 *   <li>DURATION: {"value": number, "unit": MINUTES|HOURS|DAYS|WEEKS|MONTHS|YEARS}</li>
 *   <li>MEASUREMENT: {"value": number, "unit": string} (unit must be the question's unit if it has options)</li>
 *   <li>SCALE: whole number within min/max (default 0 to 10)</li>
 *   <li>BODY: list of body-region codes (strings)</li>
 * </ul>
 * An empty answer (null, blank, empty list) is always shape-valid; "required" is the engine's concern.
 */
public final class AnswerValidator {

    private static final Set<String> DURATION_UNITS = Set.of("MINUTES", "HOURS", "DAYS", "WEEKS", "MONTHS", "YEARS");

    private AnswerValidator() {}

    /** Returns a readable problem, or null when the answer is acceptable. */
    public static String problem(QuestionDef q, Object a) {
        if (QuestionRuleEngine.isEmpty(a)) return null;
        return switch (q.type()) {
            case YES_NO, TOGGLE -> a instanceof Boolean ? null : "Answer with yes or no.";
            case SINGLE_SELECT, RADIO_GROUP -> single(q, a);
            case MULTI_SELECT, CHECKLIST -> multi(q, a);
            case NUMBER  -> numeric(q, a, true);
            case DECIMAL -> numeric(q, a, false);
            case TEXT      -> text(a, 500);
            case LONG_TEXT -> text(a, 5000);
            case DATE      -> date(a);
            case DATE_TIME -> dateTime(a);
            case DURATION  -> duration(a);
            case MEASUREMENT -> measurement(q, a);
            case SCALE -> scale(q, a);
            case BODY  -> body(a);
        };
    }

    private static String single(QuestionDef q, Object a) {
        if (!(a instanceof String s)) return "Choose one option.";
        return optionValues(q).contains(s) ? null : "'" + s + "' is not one of the options.";
    }

    private static String multi(QuestionDef q, Object a) {
        if (!(a instanceof List<?> l)) return "Choose one or more options.";
        Set<String> allowed = optionValues(q);
        for (Object o : l) if (!(o instanceof String s) || !allowed.contains(s)) return "'" + o + "' is not one of the options.";
        return null;
    }

    private static String numeric(QuestionDef q, Object a, boolean whole) {
        BigDecimal n = toNumber(a);
        if (n == null) return "Enter a number.";
        if (whole && n.stripTrailingZeros().scale() > 0) return "Enter a whole number.";
        return range(q.min(), q.max(), n);
    }

    private static String text(Object a, int max) {
        if (!(a instanceof String s)) return "Enter text.";
        return s.length() <= max ? null : "Text is limited to " + max + " characters.";
    }

    private static String date(Object a) {
        try { LocalDate.parse(String.valueOf(a)); return null; }
        catch (DateTimeParseException e) { return "Enter a date as YYYY-MM-DD."; }
    }

    private static String dateTime(Object a) {
        String s = String.valueOf(a);
        try { OffsetDateTime.parse(s); return null; } catch (DateTimeParseException ignored) { /* try local */ }
        try { LocalDateTime.parse(s); return null; } catch (DateTimeParseException e) { return "Enter a date and time (ISO 8601)."; }
    }

    private static String duration(Object a) {
        if (!(a instanceof Map<?, ?> m)) return "Enter a duration with a value and unit.";
        BigDecimal v = toNumber(m.get("value"));
        if (v == null || v.signum() < 0) return "A duration needs a value of zero or more.";
        return DURATION_UNITS.contains(String.valueOf(m.get("unit"))) ? null : "Unit must be one of " + DURATION_UNITS + ".";
    }

    private static String measurement(QuestionDef q, Object a) {
        if (!(a instanceof Map<?, ?> m)) return "Enter a measurement with a value and unit.";
        BigDecimal v = toNumber(m.get("value"));
        if (v == null) return "A measurement needs a numeric value.";
        Object unit = m.get("unit");
        if (!(unit instanceof String u) || u.isBlank()) return "A measurement needs a unit.";
        Set<String> units = optionValues(q);
        if (!units.isEmpty() && !units.contains(u)) return "Unit '" + u + "' is not allowed here.";
        return range(q.min(), q.max(), v);
    }

    private static String scale(QuestionDef q, Object a) {
        BigDecimal n = toNumber(a);
        if (n == null || n.stripTrailingZeros().scale() > 0) return "Choose a whole number on the scale.";
        BigDecimal lo = q.min() != null ? q.min() : BigDecimal.ZERO;
        BigDecimal hi = q.max() != null ? q.max() : BigDecimal.TEN;
        return range(lo, hi, n);
    }

    private static String body(Object a) {
        if (!(a instanceof List<?> l)) return "Choose one or more body regions.";
        for (Object o : l) if (!(o instanceof String s) || s.isBlank()) return "Body regions must be codes.";
        return null;
    }

    private static String range(BigDecimal min, BigDecimal max, BigDecimal n) {
        if (min != null && n.compareTo(min) < 0) return "Must be at least " + min.stripTrailingZeros().toPlainString() + ".";
        if (max != null && n.compareTo(max) > 0) return "Must be at most " + max.stripTrailingZeros().toPlainString() + ".";
        return null;
    }

    private static Set<String> optionValues(QuestionDef q) {
        java.util.HashSet<String> s = new java.util.HashSet<>();
        for (Map<String, Object> o : q.options()) s.add(String.valueOf(o.get("value")));
        return s;
    }

    private static BigDecimal toNumber(Object v) {
        if (v instanceof Boolean || v == null) return null;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        if (v instanceof String s) { try { return new BigDecimal(s.trim()); } catch (NumberFormatException e) { return null; } }
        return null;
    }
}
