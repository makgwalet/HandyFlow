package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** What an obligation in the catalogue may say, and how a version moves from draft to approved. Pure, so it is easy to test. */
public final class ObligationRules {

    private ObligationRules() {}

    public static final Set<String> FREQUENCIES = Set.of("ONCE", "MONTHLY", "QUARTERLY", "BIANNUAL", "ANNUAL", "ON_EXPIRY", "ON_EVENT");
    public static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final Set<String> RESPONSIBLE_ROLES = Set.of("CONSULTANT", "REVIEWER", "CLIENT", "EXTERNAL_PROVIDER");
    public static final Set<String> DUE_RULES = Set.of("NONE", "ANNUAL_FIXED", "ANNUAL_AFTER_FYE", "MONTHLY_DAY", "QUARTER_END_PLUS",
            "ANNIVERSARY", "AFTER_EVENT", "BEFORE_EXPIRY");
    public static final Set<String> CONDITION_FIELDS = Set.of("ENTITY_TYPE", "INDUSTRY", "EMPLOYEE_COUNT", "TURNOVER_BAND", "VAT_REGISTERED");
    public static final int MIN_REVIEW_REASON = 10;
    public static final int MIN_EXCEPTION_REASON = 15;

    public record Condition(String field, String operator, String value) {}
    public record Evidence(String documentType, boolean mandatory) {}

    public static String code(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("code is required");
        String c = raw.trim().toUpperCase().replaceAll("\\s+", "-");
        if (!c.matches("[A-Z0-9][A-Z0-9._-]{1,59}")) throw new IllegalArgumentException("code may use letters, digits, . _ and - (2 to 60 characters)");
        return c;
    }

    /** What the due-date rule needs, or an error naming what is missing. */
    public static void dueRule(String type, Integer month, Integer day, Integer offsetDays, Integer offsetMonths) {
        if (type == null || !DUE_RULES.contains(type)) throw new IllegalArgumentException("dueRuleType must be one of " + DUE_RULES.stream().sorted().toList());
        switch (type) {
            case "NONE" -> { }
            case "ANNUAL_FIXED" -> {
                if (month == null || month < 1 || month > 12) throw new IllegalArgumentException("A fixed yearly date needs a month from 1 to 12");
                if (day == null || day < 1 || day > YearMonth.of(2024, month).lengthOfMonth()) throw new IllegalArgumentException("That day does not exist in that month");
            }
            case "MONTHLY_DAY" -> {
                if (day == null || day < 1 || day > 31) throw new IllegalArgumentException("A monthly due date needs a day from 1 to 31");
            }
            case "ANNUAL_AFTER_FYE", "ANNIVERSARY" -> {
                if (offsetMonths == null || offsetMonths < 0 || offsetMonths > 24) throw new IllegalArgumentException("This rule needs a number of months from 0 to 24");
            }
            case "QUARTER_END_PLUS", "AFTER_EVENT", "BEFORE_EXPIRY" -> {
                if (offsetDays == null || offsetDays < 0 || offsetDays > 730) throw new IllegalArgumentException("This rule needs a number of days from 0 to 730");
            }
            default -> throw new IllegalArgumentException("Unknown due rule " + type);
        }
    }

    /** Checks one applicability condition and returns it in its stored form. */
    public static Condition condition(String field, String operator, String value) {
        if (field == null || !CONDITION_FIELDS.contains(field)) throw new IllegalArgumentException("Unknown condition field " + field);
        String op = operator == null ? "" : operator.trim().toUpperCase();
        String v = value == null ? "" : value.trim();
        switch (field) {
            case "ENTITY_TYPE", "TURNOVER_BAND" -> {
                if (!op.equals("IN")) throw new IllegalArgumentException(field + " uses IN");
                Set<String> allowed = field.equals("ENTITY_TYPE") ? ClientProfileRules.ENTITY_TYPES : ClientProfileRules.TURNOVER_BANDS;
                List<String> items = list(v);
                if (items.isEmpty()) throw new IllegalArgumentException(field + " needs at least one value");
                for (String i : items) if (!allowed.contains(i)) throw new IllegalArgumentException(i + " is not a valid " + field);
                return new Condition(field, op, String.join(",", items));
            }
            case "INDUSTRY" -> {
                if (!op.equals("IN")) throw new IllegalArgumentException("INDUSTRY uses IN");
                List<String> items = Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (items.isEmpty()) throw new IllegalArgumentException("INDUSTRY needs at least one value");
                return new Condition(field, op, String.join(",", items));
            }
            case "EMPLOYEE_COUNT" -> {
                if (!op.equals("GTE") && !op.equals("LTE")) throw new IllegalArgumentException("EMPLOYEE_COUNT uses GTE or LTE");
                try {
                    int n = Integer.parseInt(v);
                    if (n < 0) throw new NumberFormatException();
                    return new Condition(field, op, String.valueOf(n));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("EMPLOYEE_COUNT needs a whole number, 0 or more");
                }
            }
            default -> {   // VAT_REGISTERED
                if (!op.equals("IS_TRUE") && !op.equals("IS_FALSE")) throw new IllegalArgumentException("VAT_REGISTERED uses IS_TRUE or IS_FALSE");
                return new Condition(field, op, null);
            }
        }
    }

    private static List<String> list(String v) {
        List<String> out = new ArrayList<>();
        for (String s : v.split(",")) { String t = s.trim().toUpperCase(); if (!t.isEmpty() && !out.contains(t)) out.add(t); }
        return out;
    }

    /** Two conditions on the same field that cannot both be met are a mistake. */
    public static void consistent(List<Condition> conditions) {
        Integer min = null, max = null;
        for (Condition c : conditions) {
            if (!c.field().equals("EMPLOYEE_COUNT")) continue;
            int n = Integer.parseInt(c.value());
            if (c.operator().equals("GTE")) min = min == null ? n : Math.max(min, n); else max = max == null ? n : Math.min(max, n);
        }
        if (min != null && max != null && min > max) throw new IllegalArgumentException("The employee limits cannot both be met");
        long vat = conditions.stream().filter(c -> c.field().equals("VAT_REGISTERED")).map(Condition::operator).distinct().count();
        if (vat > 1) throw new IllegalArgumentException("VAT_REGISTERED cannot be both true and false");
    }

    public static Evidence evidence(String documentType, boolean mandatory) {
        if (documentType == null || documentType.isBlank()) throw new IllegalArgumentException("An evidence type needs a name");
        String t = documentType.trim();
        return new Evidence(t.length() > 120 ? t.substring(0, 120) : t, mandatory);
    }

    /** The version in force on a date: approved, started, and not yet ended. */
    public record Span(int number, boolean approved, LocalDate from, LocalDate to) {}

    public static Integer effectiveOn(List<Span> versions, LocalDate date) {
        Integer best = null;
        for (Span s : versions) {
            if (!s.approved() || s.from() == null || s.from().isAfter(date)) continue;
            if (s.to() != null && s.to().isBefore(date)) continue;
            if (best == null || s.number() > best) best = s.number();
        }
        return best;
    }

    /**
     * An approved version starting on {@code from} ends the one before it the day before. It is refused if the earlier
     * version started on or after that day, because the earlier one would then never be in force.
     */
    public static LocalDate endOfPrevious(LocalDate previousFrom, LocalDate newFrom) {
        if (previousFrom != null && !previousFrom.isBefore(newFrom)) {
            throw new IllegalArgumentException("The new version must start after the one before it, which started on " + previousFrom);
        }
        return newFrom.minusDays(1);
    }
}
