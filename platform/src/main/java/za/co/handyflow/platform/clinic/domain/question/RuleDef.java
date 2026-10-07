package za.co.handyflow.platform.clinic.domain.question;

import java.util.Map;

/** One rule on a question: what it does ({@link RuleKind}), when ({@code expression}), and what to tell the user. */
public record RuleDef(RuleKind kind, Map<String, Object> expression, String message, String targetGroupCode) {
    public static RuleDef of(RuleKind kind, Map<String, Object> expression) {
        return new RuleDef(kind, expression, null, null);
    }
    public static RuleDef warning(Map<String, Object> expression, String message) {
        return new RuleDef(RuleKind.WARNING_WHEN, expression, message, null);
    }
    public static RuleDef trigger(Map<String, Object> expression, String targetGroupCode) {
        return new RuleDef(RuleKind.TRIGGER_GROUP, expression, null, targetGroupCode);
    }
}
