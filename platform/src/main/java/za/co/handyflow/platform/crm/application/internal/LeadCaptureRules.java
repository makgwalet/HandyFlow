package za.co.handyflow.platform.crm.application.internal;

import java.util.regex.Pattern;
import za.co.handyflow.platform.crm.domain.model.LeadSource;

/** What a lead sent from outside must look like, and how it becomes a lead record. Pure, no database. */
final class LeadCaptureRules {
    static final int MAX_KEYS = 10, NAME_MAX = 255, MESSAGE_MAX = 2000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^(\\+|0)[\\d\\s\\-]{7,19}$");

    /** A submission after checking. record is the name of the lead record; notes carries the contact and message. */
    record Clean(String recordName, String email, String phone, String notes, LeadSource source) {}

    private LeadCaptureRules() {}

    /** True when the robot trap is filled in. */
    static boolean isSpam(String website) { return website != null && !website.isBlank(); }

    static void checkKeyName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the key");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
    }

    static Clean clean(String name, String company, String email, String phone, String message, String source, LeadSource keyDefault) {
        String n = trim(name), c = trim(company), e = trim(email), p = trim(phone), m = trim(message);
        if (n == null && c == null) throw new IllegalArgumentException("Send a name");
        if (n != null && n.length() > NAME_MAX || c != null && c.length() > NAME_MAX) throw new IllegalArgumentException("The name can be at most " + NAME_MAX + " characters");
        if (e != null && (e.length() > 255 || !EMAIL.matcher(e).matches())) throw new IllegalArgumentException("That email address is not valid");
        if (p != null && !PHONE.matcher(p).matches()) throw new IllegalArgumentException("Phone must start with + or 0 and have 7 to 20 digits, spaces or hyphens");
        if (m != null && m.length() > MESSAGE_MAX) throw new IllegalArgumentException("The message can be at most " + MESSAGE_MAX + " characters");
        StringBuilder notes = new StringBuilder("Received through the web form or API.");
        if (c != null && n != null) notes.append("\nContact: ").append(n);
        if (m != null) notes.append("\nMessage: ").append(m);
        return new Clean(c != null ? c : n, e == null ? null : e.toLowerCase(), p, notes.toString(), sourceOf(source, keyDefault));
    }

    /** The sender's source if it is a real one, else the key's default. */
    static LeadSource sourceOf(String given, LeadSource keyDefault) {
        String g = trim(given);
        if (g != null) {
            try { return LeadSource.valueOf(g.toUpperCase().replace(' ', '_').replace('-', '_')); } catch (IllegalArgumentException ignored) { /* fall through */ }
        }
        return keyDefault == null ? LeadSource.WEBSITE : keyDefault;
    }

    private static String trim(String s) { return s == null || s.isBlank() ? null : s.strip(); }
}
