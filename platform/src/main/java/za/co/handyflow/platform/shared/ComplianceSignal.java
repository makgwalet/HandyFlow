package za.co.handyflow.platform.shared;

/**
 * One headline count a module reports about its own compliance position, for Legal Compliance to show alongside its own registers.
 * Counts only: no names, registrations, amounts or personal information. A module that has nothing to report returns no signals (never a zero).
 *
 * @param module    the reporting module's name, for example "fleet"
 * @param key       stable key within the module, for example "documentsExpired"
 * @param label     plain-language text that reads after the count, for example "vehicle document(s) expired"
 * @param count     always above zero
 * @param severity  how urgent it is, in the reporting module's own judgement
 * @param actionUrl where in the app the person goes to deal with it (a relative path), or null
 */
public record ComplianceSignal(String module, String key, String label, long count, Severity severity, String actionUrl) {

    public enum Severity { CRITICAL, WARNING, INFO }

    public ComplianceSignal {
        if (module == null || module.isBlank() || key == null || key.isBlank() || label == null || label.isBlank()) throw new IllegalArgumentException("module, key and label are required");
        if (count <= 0) throw new IllegalArgumentException("A signal reports a count above zero");
        if (severity == null) throw new IllegalArgumentException("severity is required");
        if (actionUrl != null && !actionUrl.startsWith("/")) throw new IllegalArgumentException("actionUrl must be a relative path");
    }
}
