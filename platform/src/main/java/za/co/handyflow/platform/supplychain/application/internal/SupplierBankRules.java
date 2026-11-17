package za.co.handyflow.platform.supplychain.application.internal;

/**
 * Pure validation/normalisation for supplier bank details. Throws IllegalArgumentException,
 * which GlobalExceptionHandler already maps to HTTP 400.
 * Account numbers: digits only after stripping spaces/hyphens, 5-17 digits (SA banks issue
 * roughly 7-11; the wider range avoids rejecting legitimate foreign-bank suppliers).
 * Branch code: exactly 6 digits when supplied (SA universal branch codes).
 */
public final class SupplierBankRules {

    private SupplierBankRules() {}

    public record Bank(String account, String branch) {}

    /** For supplier creation: both fields optional, but if present they must be valid. */
    public static Bank validateOptional(String account, String branch) {
        String a = account == null || account.isBlank() ? null : normaliseAccount(account);
        String b = branch == null || branch.isBlank() ? null : normaliseBranch(branch);
        return new Bank(a, b);
    }

    /** For a bank-change request: account number is mandatory. */
    public static Bank validateRequired(String account, String branch) {
        if (account == null || account.isBlank())
            throw new IllegalArgumentException("Bank account number is required");
        return validateOptional(account, branch);
    }

    public static String normaliseAccount(String raw) {
        String digits = raw.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{5,17}"))
            throw new IllegalArgumentException("Bank account number must be 5 to 17 digits");
        return digits;
    }

    public static String normaliseBranch(String raw) {
        String digits = raw.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{6}"))
            throw new IllegalArgumentException("Branch code must be 6 digits");
        return digits;
    }

    /** "****4321" — never reveals more than the last four digits. */
    public static String mask(String account) {
        if (account == null || account.isBlank()) return null;
        String a = account.trim();
        return a.length() <= 4 ? "****" : "****" + a.substring(a.length() - 4);
    }
}
