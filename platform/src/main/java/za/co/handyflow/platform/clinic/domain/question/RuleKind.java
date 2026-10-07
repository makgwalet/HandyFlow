package za.co.handyflow.platform.clinic.domain.question;

/** What a rule does when its expression is true. Rules reveal, require, enable, warn or open another group; they never diagnose. */
public enum RuleKind {
    SHOW_WHEN, REQUIRED_WHEN, ENABLE_WHEN, WARNING_WHEN, TRIGGER_GROUP
}
