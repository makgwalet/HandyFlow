package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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

    public record Subject(CustomerType type, CustomerStatus status, LeadSource source, LeadStage stage, UUID ownerId, Set<String> tags,
                          BigDecimal dealValue, Instant createdAt, Instant updatedAt, boolean hasEmail, boolean hasPhone) {}

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

    /** Why the conditions cannot be saved, or null. */
    public static String problem(List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) return "Add at least one condition.";
        if (conditions.size() > MAX_CONDITIONS) return "A segment can have at most " + MAX_CONDITIONS + " conditions.";
        for (Condition c : conditions) {
            String p = conditionProblem(c);
            if (p != null) return p;
        }
        return null;
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
            boolean noValue = "EMAIL".equals(c.field()) || "PHONE".equals(c.field());
            out.add(new Condition(c.field(), c.op(), noValue ? "" : v));
        }
        return out;
    }

    public static boolean matches(List<Condition> conditions, Subject s, Instant now) {
        return conditions.stream().allMatch(c -> matches(c, s, now));
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
