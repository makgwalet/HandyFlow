package za.co.handyflow.platform.recruiter.domain.model;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Checks on what a member of the public sends to the careers page, and on what a candidate may do with their own application. Pure.
 * The careers endpoints take no login, so every value is limited in length and shape here before it is stored or shown to staff.
 */
public final class RecruiterPublicRules {
    private RecruiterPublicRules() {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9 +()\\-.]{5,30}$");
    private static final Pattern SOURCE = Pattern.compile("^[A-Za-z0-9_]{1,30}$");

    public static String normaliseEmail(String email) { return email == null ? null : email.trim().toLowerCase(); }

    /** Why this application form cannot be accepted, or null when it can. */
    public static String applicationProblem(String firstName, String lastName, String email, String phone, String location,
                                            String linkedinUrl, String portfolioUrl, String source, String referrerName) {
        if (blank(firstName) || firstName.trim().length() > 100) return "Enter your first name (100 characters at most).";
        if (blank(lastName) || lastName.trim().length() > 100) return "Enter your last name (100 characters at most).";
        String e = normaliseEmail(email);
        if (e == null || e.length() > 254 || !EMAIL.matcher(e).matches()) return "Enter a valid email address.";
        String profile = profileProblem(phone, location, linkedinUrl, portfolioUrl);
        if (profile != null) return profile;
        if (!blank(source) && !SOURCE.matcher(source.trim()).matches()) return "The source is not valid.";
        if (referrerName != null && referrerName.trim().length() > 100) return "The referrer's name is too long (100 characters at most).";
        return null;
    }

    /** Why these contact details cannot be kept, or null when they can. Any of them may be missing. */
    public static String profileProblem(String phone, String location, String linkedinUrl, String portfolioUrl) {
        if (!blank(phone) && !PHONE.matcher(phone.trim()).matches()) return "Enter a valid phone number (digits, spaces, + ( ) - only).";
        if (location != null && location.trim().length() > 150) return "The location is too long (150 characters at most).";
        if (!blank(linkedinUrl)) { String p = urlProblem(linkedinUrl); if (p != null) return "LinkedIn link: " + p; }
        if (!blank(portfolioUrl)) { String p = urlProblem(portfolioUrl); if (p != null) return "Portfolio link: " + p; }
        return null;
    }

    private static String urlProblem(String url) {
        String u = url.trim();
        if (u.length() > 500) return "too long (500 characters at most).";
        String lower = u.toLowerCase();
        if (!(lower.startsWith("https://") || lower.startsWith("http://"))) return "must start with http:// or https://.";
        if (u.chars().anyMatch(c -> c < 33 || c == 127 || c == '<' || c == '>' || c == '"')) return "contains characters that are not allowed.";
        return null;
    }

    /** Why applications are closed for a job with this closing date, or null while it is open. The closing day itself is still open. */
    public static String closedProblem(LocalDate closesAt, LocalDate today) {
        return closesAt != null && today.isAfter(closesAt) ? "The closing date for this position has passed." : null;
    }

    /** Why a candidate cannot withdraw an application in this stage, or null when they can. */
    public static String withdrawProblem(String stage) {
        if ("HIRED".equals(stage)) return "This application led to a hire and cannot be withdrawn here. Please contact the company.";
        if ("REJECTED".equals(stage) || "WITHDRAWN".equals(stage)) return "This application is already closed.";
        return null;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
