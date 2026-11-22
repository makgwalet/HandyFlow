package za.co.handyflow.platform.security.application.internal;

/** Rules for showing a guard's full ID number or bank account: which field, which permission, what reason. */
final class PiiReveal {

    static final int MIN_REASON = 10;
    static final int MAX_REASON = 500;

    enum Field { ID_NUMBER, BANK_ACCOUNT }

    private PiiReveal() {}

    static Field parse(String text) {
        if (text == null) return null;
        try { return Field.valueOf(text.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Permission needed: ID numbers need SECURITY_MANAGE; bank accounts need the banking permission. */
    static String authorityFor(Field f) {
        return f == Field.BANK_ACCOUNT ? "SECURITY_GUARD_BANKING_MANAGE" : "SECURITY_MANAGE";
    }

    /** Error code when the request is not acceptable, else null. */
    static String problem(String field, String reason) {
        if (parse(field) == null) return "INVALID_PII_FIELD";
        if (reason == null || reason.trim().length() < MIN_REASON) return "REASON_REQUIRED";
        if (reason.trim().length() > MAX_REASON) return "REASON_TOO_LONG";
        return null;
    }

    /** Audit metadata. Records which field and why, never the value itself. */
    static String auditJson(Field f, String reason) {
        return "{\"field\":" + AuditJson.quote(f.name()) + ",\"reason\":" + AuditJson.quote(reason.trim()) + "}";
    }
}
