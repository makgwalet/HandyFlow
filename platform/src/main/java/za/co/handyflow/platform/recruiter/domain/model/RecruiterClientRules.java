package za.co.handyflow.platform.recruiter.domain.model;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** What a client, a contact and a placement fee must look like. Pure. Every method returns a sentence for the person, or null when it is fine. */
public final class RecruiterClientRules {
    private RecruiterClientRules() {}

    public static final int MAX_NAME = 150, MAX_INDUSTRY = 80, MAX_LOCATION = 120, MAX_NOTES = 2000, MAX_CONTACT_NAME = 120, MAX_EMAIL = 200, MAX_PHONE = 30, MAX_ROLE = 80;
    public static final BigDecimal MAX_FEE = new BigDecimal("10000000");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{5,30}$");

    public static String clientProblem(String name, String industry, String location, String notes) {
        if (name == null || name.trim().length() < 2) return "Give the client a name (at least 2 characters).";
        if (name.trim().length() > MAX_NAME) return "The name is too long (" + MAX_NAME + " characters at most).";
        if (industry != null && industry.trim().length() > MAX_INDUSTRY) return "The industry is too long (" + MAX_INDUSTRY + " characters at most).";
        if (location != null && location.trim().length() > MAX_LOCATION) return "The location is too long (" + MAX_LOCATION + " characters at most).";
        if (notes != null && notes.trim().length() > MAX_NOTES) return "The notes are too long (" + MAX_NOTES + " characters at most).";
        return null;
    }

    public static String statusProblem(String status) {
        return status == null || status.equals("ACTIVE") || status.equals("INACTIVE") ? null : "A client is either active or inactive.";
    }

    public static String contactProblem(String name, String email, String phone, String roleTitle) {
        if (name == null || name.trim().isEmpty()) return "Give the contact a name.";
        if (name.trim().length() > MAX_CONTACT_NAME) return "The name is too long (" + MAX_CONTACT_NAME + " characters at most).";
        boolean hasEmail = email != null && !email.trim().isEmpty(), hasPhone = phone != null && !phone.trim().isEmpty();
        if (!hasEmail && !hasPhone) return "Give an email address or a phone number so the contact can be reached.";
        if (hasEmail && (email.trim().length() > MAX_EMAIL || !EMAIL.matcher(email.trim()).matches())) return "That email address does not look right.";
        if (hasPhone && !PHONE.matcher(phone.trim()).matches()) return "That phone number does not look right.";
        if (roleTitle != null && roleTitle.trim().length() > MAX_ROLE) return "The job title is too long (" + MAX_ROLE + " characters at most).";
        return null;
    }

    /** A fee of null clears it. Otherwise it is rand and cents, not negative, and not absurd. */
    public static String feeProblem(BigDecimal fee) {
        if (fee == null) return null;
        if (fee.signum() < 0) return "A fee cannot be negative.";
        if (fee.scale() > 2 && fee.stripTrailingZeros().scale() > 2) return "A fee has at most two decimal places.";
        if (fee.compareTo(MAX_FEE) > 0) return "That fee is too large. Check the amount.";
        return null;
    }

    /** Blank becomes null so the database holds either something or nothing. */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
