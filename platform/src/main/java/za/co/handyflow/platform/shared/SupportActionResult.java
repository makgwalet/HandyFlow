package za.co.handyflow.platform.shared;

/**
 * The outcome of running one {@link SupportAction}. Both outcomes get
 * audited by {@code SupportActionService} — this isn't a success-only
 * type, {@code message} should explain a failure clearly enough that
 * whoever reads the audit log later understands what happened without
 * needing to dig into application logs.
 */
public record SupportActionResult(boolean success, String message) {

    public static SupportActionResult success(String message) {
        return new SupportActionResult(true, message);
    }

    public static SupportActionResult failure(String message) {
        return new SupportActionResult(false, message);
    }
}
