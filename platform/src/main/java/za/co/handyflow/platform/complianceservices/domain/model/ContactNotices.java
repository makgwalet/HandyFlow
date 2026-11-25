package za.co.handyflow.platform.complianceservices.domain.model;

/**
 * The words of the messages sent when a client writes, uploads, or the practice replies. They carry the subject or title only, never what
 * was written or uploaded, and they say "handed over", not "delivered". The client's message names no staff member.
 */
public final class ContactNotices {

    private ContactNotices() {}

    private static String clean(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    public static String clientWroteTitle(String clientName, boolean first) {
        return (first ? "New query from " : "New message from ") + clean(clientName, 80);
    }

    public static String clientWroteMessage(String clientName, String subject, boolean first) {
        return clean(clientName, 80) + (first ? " has asked: " : " has written again about: ") + clean(subject, 120) + ". Open Client queries to read and reply.";
    }

    public static String uploadTitle(String clientName) { return "File received from " + clean(clientName, 80); }

    public static String uploadMessage(String clientName, String requestTitle) {
        return clean(clientName, 80) + " has uploaded a file for: " + clean(requestTitle, 120) + ". It has not been checked. Look at it, then close the request.";
    }

    public static String replyTitle() { return "Your practice has replied"; }

    public static String replyMessage(String subject) {
        return "Your practice has replied to your question \"" + clean(subject, 120) + "\". Sign in to the client portal to read it.";
    }
}
