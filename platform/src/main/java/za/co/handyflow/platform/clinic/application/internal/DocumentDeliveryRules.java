package za.co.handyflow.platform.clinic.application.internal;

/** What a document emailed to a patient says (patch 0203). Pure. The body never carries clinical content: that is in the attached PDF. */
public final class DocumentDeliveryRules {

    private DocumentDeliveryRules() {}

    public record Email(String subject, String html, String fileName) {}

    /** A usable address, or null. Only the patient's own address on file is used, so a mistyped recipient cannot receive the record. */
    public static String address(String raw) {
        if (raw == null) return null;
        String a = raw.trim();
        return a.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? a : null;
    }

    public static Email email(String type, String number, String firstName, String practiceName) {
        String label = DocumentNumberRules.label(type);
        String who = firstName == null || firstName.isBlank() ? "there" : esc(firstName.trim());
        String from = practiceName == null || practiceName.isBlank() ? "the practice" : esc(practiceName.trim());
        String line = isReferral(type)
                ? "A copy of your referral letter is attached for your records. Please bring it to your appointment."
                : "Your " + esc(label.toLowerCase(java.util.Locale.ROOT)) + " from " + from + " is attached.";
        String html = "<p>Dear " + who + ",</p><p>" + line + "</p><p>Document number " + esc(number)
                + ". You can check it is genuine on the practice's document verification page with the code printed at the foot of the document.</p>"
                + "<p>If you have any questions, please contact the practice.</p>";
        String subject = (isReferral(type) ? "Your referral letter" : "Your " + label.toLowerCase(java.util.Locale.ROOT)) + " (" + number + ")";
        return new Email(subject, html, number + ".pdf");
    }

    private static boolean isReferral(String type) { return "REFERRAL".equals(type); }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
