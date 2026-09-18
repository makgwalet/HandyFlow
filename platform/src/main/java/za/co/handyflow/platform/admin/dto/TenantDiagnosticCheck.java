package za.co.handyflow.platform.admin.dto;

/**
 * One check in a tenant's diagnostic report (see AdminTenantDiagnosticService).
 * {@code severity} distinguishes a real problem from an informational,
 * opt-in item that simply hasn't been configured yet — deliberately NOT
 * collapsed into a single pass/fail score, matching the platform brief's
 * explicit instruction: "Not as a simplistic 'score' for the customer, but
 * as a technical diagnostic status."
 */
public record TenantDiagnosticCheck(
        String key,          // e.g. "TENANT_STATUS", "HAS_USERS"
        String label,        // e.g. "Tenant active"
        Severity severity,
        boolean pass,
        String detail        // human-readable explanation of the result
) {
    public enum Severity {
        /** A real problem worth support attention. */
        CRITICAL,
        /** Worth noting but not a fault — e.g. an opt-in feature not yet configured. */
        INFORMATIONAL
    }

    public static TenantDiagnosticCheck critical(String key, String label, boolean pass, String detail) {
        return new TenantDiagnosticCheck(key, label, Severity.CRITICAL, pass, detail);
    }

    public static TenantDiagnosticCheck informational(String key, String label, boolean pass, String detail) {
        return new TenantDiagnosticCheck(key, label, Severity.INFORMATIONAL, pass, detail);
    }
}
