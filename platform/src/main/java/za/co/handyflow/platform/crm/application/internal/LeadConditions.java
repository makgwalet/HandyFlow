package za.co.handyflow.platform.crm.application.internal;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import za.co.handyflow.platform.crm.domain.model.ConditionField;
import za.co.handyflow.platform.crm.domain.model.ConditionOp;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

/** One yes/no test on a lead, shared by scoring, routing and workflows. Pure: no Spring, no database. */
final class LeadConditions {
    private LeadConditions() {}

    /** What is known about a lead when a rule looks at it. Tags are lower-case. */
    record Facts(LeadSource source, LeadStage stage, BigDecimal dealValue, boolean hasEmail, boolean hasPhone, boolean hasOwner, Set<String> tags) {}

    record Condition(ConditionField field, ConditionOp op, String value) {}

    /** Throws IllegalArgumentException with a message for the person filling in the rule. */
    static void check(Condition c) {
        if (c == null || c.field() == null) throw new IllegalArgumentException("Choose what to check");
        if (c.op() == null) throw new IllegalArgumentException("Choose how to compare");
        String v = c.value() == null ? "" : c.value().strip();
        if (v.isEmpty()) throw new IllegalArgumentException("Give the value to compare with");
        if (v.length() > 100) throw new IllegalArgumentException("The value can be at most 100 characters");
        switch (c.field()) {
            case SOURCE -> { needEq(c); parse(LeadSource.class, v, "a lead source"); }
            case STAGE -> { needEq(c); parse(LeadStage.class, v, "a pipeline stage"); }
            case HAS_EMAIL, HAS_PHONE, HAS_OWNER -> {
                needEq(c);
                if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) throw new IllegalArgumentException("Choose yes or no");
            }
            case TAG -> needEq(c);
            case DEAL_VALUE -> {
                if (c.op() == ConditionOp.EQ) throw new IllegalArgumentException("A deal value is compared as at least or at most");
                BigDecimal n;
                try { n = new BigDecimal(v); } catch (NumberFormatException e) { throw new IllegalArgumentException("The deal value must be a number"); }
                if (n.signum() < 0) throw new IllegalArgumentException("The deal value cannot be negative");
            }
        }
    }

    private static void needEq(Condition c) {
        if (c.op() != ConditionOp.EQ) throw new IllegalArgumentException(label(c.field()) + " can only be compared with \"is\"");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String v, String what) {
        try { return Enum.valueOf(type, v.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Choose " + what); }
    }

    /** True when the condition holds. A null condition holds for every lead. Assumes the condition passed check(). */
    static boolean matches(Condition c, Facts f) {
        if (c == null) return true;
        String v = c.value().strip();
        return switch (c.field()) {
            case SOURCE -> f.source() != null && f.source().name().equalsIgnoreCase(v);
            case STAGE -> f.stage() != null && f.stage().name().equalsIgnoreCase(v);
            case HAS_EMAIL -> f.hasEmail() == Boolean.parseBoolean(v);
            case HAS_PHONE -> f.hasPhone() == Boolean.parseBoolean(v);
            case HAS_OWNER -> f.hasOwner() == Boolean.parseBoolean(v);
            case TAG -> f.tags() != null && f.tags().contains(v.toLowerCase(Locale.ROOT));
            case DEAL_VALUE -> {
                if (f.dealValue() == null) yield false;
                int cmp = f.dealValue().compareTo(new BigDecimal(v));
                yield c.op() == ConditionOp.GTE ? cmp >= 0 : cmp <= 0;
            }
        };
    }

    static String label(ConditionField f) {
        return switch (f) {
            case SOURCE -> "Source"; case STAGE -> "Stage"; case DEAL_VALUE -> "Deal value"; case HAS_EMAIL -> "Has an email";
            case HAS_PHONE -> "Has a phone number"; case HAS_OWNER -> "Has an owner"; case TAG -> "Tag";
        };
    }

    /** Plain-language form, for score reasons and rule lists. */
    static String describe(Condition c) {
        if (c == null) return "Every lead";
        String v = c.value().strip();
        return switch (c.field()) {
            case SOURCE, STAGE -> label(c.field()) + " is " + pretty(v);
            case HAS_EMAIL, HAS_PHONE, HAS_OWNER -> label(c.field()) + ": " + (Boolean.parseBoolean(v) ? "yes" : "no");
            case TAG -> "Tagged " + v;
            case DEAL_VALUE -> "Deal value is " + (c.op() == ConditionOp.GTE ? "at least" : "at most") + " R " + v;
        };
    }

    private static String pretty(String v) {
        String s = v.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
