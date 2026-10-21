package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.CustomFieldType;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a segment's conditions mean. A customer is in a segment when it meets every condition.
 * Pure: it works on a small snapshot of a customer, so it can be tested without a database.
 */
public final class SegmentRules {
    private SegmentRules() {}

    public static final int MAX_CONDITIONS = 10;
    public static final int MAX_SEGMENTS = 50;
    public static final int MAX_NAME = 60;
    public static final int MAX_DAYS = 3650;
    public static final String UNASSIGNED = "UNASSIGNED";

    public record Condition(String field, String op, String value) {}

    /** custom holds the values stored in the customer's custom fields, by field key. */
    public record Subject(CustomerType type, CustomerStatus status, LeadSource source, LeadStage stage, UUID ownerId, Set<String> tags,
                          BigDecimal dealValue, Instant createdAt, Instant updatedAt, boolean hasEmail, boolean hasPhone, Map<String, String> custom) {
        public Subject(CustomerType type, CustomerStatus status, LeadSource source, LeadStage stage, UUID ownerId, Set<String> tags,
                       BigDecimal dealValue, Instant createdAt, Instant updatedAt, boolean hasEmail, boolean hasPhone) {
            this(type, status, source, stage, ownerId, tags, dealValue, createdAt, updatedAt, hasEmail, hasPhone, Map.of());
        }
    }

    /** A condition on a custom field is written with this before the field's key: CF:fleet_size. */
    public static final String CUSTOM_PREFIX = "CF:";
    public static final int MAX_CUSTOM_VALUE = 500;

    private static final Set<String> SET = Set.of("IS_SET", "IS_NOT_SET");

    /** The comparisons that fit each kind of custom field. Every kind can also be tested for filled in / empty. */
    public static Set<String> customOperators(CustomFieldType type) {
        Set<String> out = new java.util.LinkedHashSet<>(SET);
        switch (type) {
            case TEXT, EMAIL, URL -> out.addAll(Set.of("CONTAINS", "IS"));
            case NUMBER -> out.addAll(Set.of("GREATER_THAN", "LESS_THAN", "EQUALS"));
            case DATE -> out.addAll(Set.of("BEFORE", "AFTER"));
            case CHOICE -> out.addAll(Set.of("IS", "IS_NOT"));
            case MULTI_CHOICE -> out.addAll(Set.of("HAS", "NOT_HAS"));
            case YES_NO -> out.add("IS");
        }
        return out;
    }

    private static final Set<String> IS = Set.of("IS", "IS_NOT");
    private static final Map<String, Set<String>> OPS = Map.of(
            "TYPE", IS, "STATUS", IS, "SOURCE", IS, "STAGE", IS, "OWNER", IS,
            "TAG", Set.of("HAS", "NOT_HAS"),
            "DEAL_VALUE", Set.of("GREATER_THAN", "LESS_THAN"),
            "CREATED", Set.of("WITHIN_DAYS", "OLDER_THAN_DAYS"),
            "UPDATED", Set.of("WITHIN_DAYS", "OLDER_THAN_DAYS"),
            "EMAIL", Set.of("IS_SET", "IS_NOT_SET"));

    private static Set<String> operators(String field) { return "PHONE".equals(field) ? OPS.get("EMAIL") : OPS.get(field); }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> out = new HashSet<>();
        for (Enum<?> e : values) out.add(e.name());
        return out;
    }

    /** Why the conditions cannot be saved, or null. Conditions on custom fields are refused here; use the version that knows the fields. */
    public static String problem(List<Condition> conditions) { return problem(conditions, Map.of(), Map.of()); }

    /**
     * Why the conditions cannot be saved, or null. types gives the kind of each active custom field by key, and options the choices of the
     * list fields, so a condition on a custom field can be checked.
     */
    public static String problem(List<Condition> conditions, Map<String, CustomFieldType> types, Map<String, List<String>> options) {
        if (conditions == null || conditions.isEmpty()) return "Add at least one condition.";
        if (conditions.size() > MAX_CONDITIONS) return "A segment can have at most " + MAX_CONDITIONS + " conditions.";
        for (Condition c : conditions) {
            String p = c != null && c.field() != null && c.field().startsWith(CUSTOM_PREFIX) ? customProblem(c, types, options) : conditionProblem(c);
            if (p != null) return p;
        }
        return null;
    }

    static String customProblem(Condition c, Map<String, CustomFieldType> types, Map<String, List<String>> options) {
        String key = c.field().substring(CUSTOM_PREFIX.length());
        CustomFieldType type = types.get(key);
        if (type == null) return "That custom field no longer exists.";
        if (c.op() == null || !customOperators(type).contains(c.op())) return "That comparison does not fit this field.";
        if (SET.contains(c.op())) return null;
        String v = c.value() == null ? "" : c.value().strip();
        if (v.isEmpty()) return "Give a value to compare with.";
        if (v.length() > MAX_CUSTOM_VALUE) return "The value can be at most " + MAX_CUSTOM_VALUE + " characters.";
        switch (type) {
            case NUMBER:
                try { new BigDecimal(v.replace(" ", "")); return null; } catch (NumberFormatException e) { return "The value must be a number."; }
            case DATE:
                try { LocalDate.parse(v); return null; } catch (DateTimeParseException e) { return "The value must be a date like 2026-11-30."; }
            case CHOICE:
            case MULTI_CHOICE:
                return options.getOrDefault(key, List.of()).contains(v) ? null : "Choose one of the field's options.";
            case YES_NO:
                return "yes".equals(v) || "no".equals(v) ? null : "Choose yes or no.";
            default:
                return null;
        }
    }

    static String conditionProblem(Condition c) {
        if (c == null || c.field() == null || operators(c.field()) == null) return "Choose what to filter on.";
        if (c.op() == null || !operators(c.field()).contains(c.op())) return "That comparison does not fit " + c.field().toLowerCase(Locale.ROOT).replace('_', ' ') + ".";
        String v = c.value() == null ? "" : c.value().strip();
        switch (c.field()) {
            case "TYPE": return names(CustomerType.values()).contains(v) ? null : "Choose a valid type.";
            case "STATUS": return names(CustomerStatus.values()).contains(v) ? null : "Choose a valid status.";
            case "SOURCE": return names(LeadSource.values()).contains(v) ? null : "Choose a valid lead source.";
            case "STAGE": return names(LeadStage.values()).contains(v) ? null : "Choose a valid stage.";
            case "OWNER":
                if (UNASSIGNED.equals(v)) return null;
                try { UUID.fromString(v); return null; } catch (IllegalArgumentException e) { return "Choose a valid owner."; }
            case "TAG": return v.isEmpty() || v.length() > 40 ? "A tag must be 1 to 40 characters." : null;
            case "DEAL_VALUE":
                try {
                    BigDecimal n = new BigDecimal(v);
                    return n.signum() < 0 ? "A deal value cannot be negative." : null;
                } catch (NumberFormatException e) { return "Deal value must be a number."; }
            case "CREATED":
            case "UPDATED":
                try {
                    int d = Integer.parseInt(v);
                    return d < 1 || d > MAX_DAYS ? "Days must be between 1 and " + MAX_DAYS + "." : null;
                } catch (NumberFormatException e) { return "Days must be a whole number."; }
            default: return null;   // EMAIL, PHONE: no value
        }
    }

    /** Conditions with their values trimmed, as they are stored. */
    public static List<Condition> clean(List<Condition> conditions) {
        List<Condition> out = new ArrayList<>();
        for (Condition c : conditions) {
            String v = c.value() == null ? "" : c.value().strip();
            boolean noValue = "EMAIL".equals(c.field()) || "PHONE".equals(c.field()) || (c.field() != null && c.field().startsWith(CUSTOM_PREFIX) && SET.contains(c.op()));
            out.add(new Condition(c.field(), c.op(), noValue ? "" : v));
        }
        return out;
    }

    public static boolean matches(List<Condition> conditions, Subject s, Instant now) { return matches(conditions, s, now, Map.of()); }

    /** types gives the kind of each active custom field by key. A condition on a field that is no longer active matches nobody. */
    public static boolean matches(List<Condition> conditions, Subject s, Instant now, Map<String, CustomFieldType> types) {
        return conditions.stream().allMatch(c -> c.field() != null && c.field().startsWith(CUSTOM_PREFIX) ? matchesCustom(c, s, types) : matches(c, s, now));
    }

    private static boolean matchesCustom(Condition c, Subject s, Map<String, CustomFieldType> types) {
        String key = c.field().substring(CUSTOM_PREFIX.length());
        CustomFieldType type = types.get(key);
        if (type == null) return false;
        String stored = s.custom() == null ? null : s.custom().get(key);
        boolean has = stored != null && !stored.isBlank();
        if ("IS_SET".equals(c.op())) return has;
        if ("IS_NOT_SET".equals(c.op())) return !has;
        String v = c.value() == null ? "" : c.value().strip();
        boolean negative = "IS_NOT".equals(c.op()) || "NOT_HAS".equals(c.op());
        if (!has) return negative;   // an empty field is "not" anything
        try {
            switch (type) {
                case NUMBER: {
                    int cmp = new BigDecimal(stored).compareTo(new BigDecimal(v.replace(" ", "")));
                    return switch (c.op()) { case "GREATER_THAN" -> cmp > 0; case "LESS_THAN" -> cmp < 0; default -> cmp == 0; };
                }
                case DATE: {
                    int cmp = LocalDate.parse(stored).compareTo(LocalDate.parse(v));
                    return "BEFORE".equals(c.op()) ? cmp < 0 : cmp > 0;
                }
                case MULTI_CHOICE: {
                    boolean picked = java.util.Arrays.asList(stored.split("\\|")).contains(v);
                    return negative != picked;
                }
                case TEXT: case EMAIL: case URL:
                    return "CONTAINS".equals(c.op()) ? stored.toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT)) : stored.equalsIgnoreCase(v);
                default: {   // CHOICE, YES_NO
                    boolean same = stored.equals(v);
                    return negative != same;
                }
            }
        } catch (RuntimeException e) {
            return false;   // a stored value that no longer reads as its kind matches nothing
        }
    }

    private static boolean matches(Condition c, Subject s, Instant now) {
        String v = c.value() == null ? "" : c.value().strip();
        boolean is = "IS".equals(c.op());
        switch (c.field()) {
            case "TYPE": return (s.type() != null && s.type().name().equals(v)) == is;
            case "STATUS": return (s.status() != null && s.status().name().equals(v)) == is;
            case "SOURCE": return (s.source() != null && s.source().name().equals(v)) == is;
            case "STAGE": return (s.stage() != null && s.stage().name().equals(v)) == is;
            case "OWNER": {
                boolean same = UNASSIGNED.equals(v) ? s.ownerId() == null : s.ownerId() != null && s.ownerId().toString().equalsIgnoreCase(v);
                return same == is;
            }
            case "TAG": {
                boolean has = s.tags() != null && s.tags().stream().anyMatch(t -> t.equalsIgnoreCase(v));
                return has == "HAS".equals(c.op());
            }
            case "DEAL_VALUE": {
                if (s.dealValue() == null) return false;
                int cmp = s.dealValue().compareTo(new BigDecimal(v));
                return "GREATER_THAN".equals(c.op()) ? cmp > 0 : cmp < 0;
            }
            case "CREATED": return withinOrOlder(s.createdAt(), c.op(), Integer.parseInt(v), now);
            case "UPDATED": return withinOrOlder(s.updatedAt(), c.op(), Integer.parseInt(v), now);
            case "EMAIL": return s.hasEmail() == "IS_SET".equals(c.op());
            case "PHONE": return s.hasPhone() == "IS_SET".equals(c.op());
            default: return false;
        }
    }

    private static boolean withinOrOlder(Instant when, String op, int days, Instant now) {
        if (when == null) return false;
        boolean within = !when.isBefore(now.minus(Duration.ofDays(days)));
        return "WITHIN_DAYS".equals(op) ? within : !within;
    }
}
