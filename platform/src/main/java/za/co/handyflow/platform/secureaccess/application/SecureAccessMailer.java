package za.co.handyflow.platform.secureaccess.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.ShareHeader;
import za.co.handyflow.platform.secureaccess.domain.model.SecureCollaborator;
import za.co.handyflow.platform.secureaccess.domain.model.SecureRecipient;
import za.co.handyflow.platform.secureaccess.domain.model.SecureShareItem;
import za.co.handyflow.platform.shared.EmailService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The emails of secure access: the share itself (register of documents plus the recipient's own link),
 * the one-time code, and a collaborator's standing workspace link.
 *
 * By default documents are NOT attached. The email carries the register (number, title, revision, status),
 * who sent it and when the link expires; the files are fetched through the link after the recipient proves
 * their email address. EmailService.send is itself @Async and swallows delivery failures, so a mail problem
 * can never roll back the business record; the sender sees "email queued" in the access log.
 *
 * Nothing sensitive is logged: not the link, not the code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class SecureAccessMailer {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    private String base() { return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl; }

    String shareLink(String token) { return base() + "/secure/share/" + token; }

    String workspaceLink(String token) { return base() + "/secure/workspace/" + token; }

    /** Returns false when notifications are switched off, so the caller can record that no email was queued. */
    boolean sendShare(ShareHeader h, SecureRecipient r, String token, List<SecureShareItem> items,
                      List<EmailService.Attachment> attachments) {
        if (!notificationsEnabled) return false;
        String cell = "<td style=\"padding:4px 10px;border-bottom:1px solid #e5e7eb\">";
        StringBuilder rows = new StringBuilder();
        for (SecureShareItem i : items) {
            rows.append("<tr>").append(cell).append(esc(nz(i.getDisplayNumber()))).append("</td>")
                .append(cell).append(esc(i.getTitle())).append("</td>")
                .append(cell).append(esc(nz(i.getRevision()))).append("</td>")
                .append(cell).append(esc(nz(i.getStatusAtIssue()))).append("</td></tr>");
        }
        String where = h.contextName() == null || h.contextName().isBlank() ? "" : " on <strong>" + esc(h.contextName()) + "</strong>";
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827\">"
                + "<p>Hello " + esc(r.getName()) + ",</p>"
                + "<p><strong>" + esc(h.sentBy() == null ? "A team member" : h.sentBy()) + "</strong> has shared documents with you" + where + ".</p>"
                + "<p><strong>" + esc(nz(h.reference())) + "</strong> — " + esc(nz(h.subject()))
                + (h.purposeLabel() == null ? "" : "<br>Purpose: <strong>" + esc(h.purposeLabel()) + "</strong>") + "</p>"
                + (h.notes() != null ? "<p>" + esc(h.notes()) + "</p>" : "")
                + "<table style=\"border-collapse:collapse;font-size:13px\"><tr style=\"background:#f3f4f6\">"
                + "<th align=\"left\" style=\"padding:4px 10px\">Number</th><th align=\"left\" style=\"padding:4px 10px\">Title</th>"
                + "<th align=\"left\" style=\"padding:4px 10px\">Rev</th><th align=\"left\" style=\"padding:4px 10px\">Status</th></tr>"
                + rows + "</table>"
                + "<p style=\"margin-top:18px\"><a href=\"" + esc(shareLink(token)) + "\" style=\"background:#1d4ed8;color:#fff;"
                + "padding:10px 18px;border-radius:6px;text-decoration:none\">Open the documents</a></p>"
                + "<p>This link is personal to you and works until <strong>" + WHEN.format(r.getExpiresAt()) + "</strong>. "
                + "We will email a six-digit code to this address to confirm it is you before any document opens. "
                + "Please do not forward the link; it will not open for anyone else.</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">"
                + (attachments.isEmpty()
                        ? "The documents are not attached to this email. Each download is recorded against your name."
                        : "The files listed above are attached to this email as well as available through the link. "
                          + "Forwarding this email forwards the attached files; the link is personal to you.")
                + "</p></div>";
        String subject = nz(h.reference()) + " — " + nz(h.subject()) + (h.contextName() == null ? "" : " — " + h.contextName());
        if (attachments.isEmpty()) emailService.send(r.getEmail(), subject, body);
        else emailService.sendWithAttachments(r.getEmail(), subject, body, attachments);
        log.info("Share email queued recipient={}", r.getId());
        return true;
    }

    void sendOtp(SecureRecipient r, String code, String reference) {
        if (!notificationsEnabled) return;
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827\">"
                + "<p>Hello " + esc(r.getName()) + ",</p>"
                + "<p>Your code to open <strong>" + esc(reference) + "</strong> is:</p>"
                + "<p style=\"font-size:26px;letter-spacing:6px;font-weight:bold\">" + esc(code) + "</p>"
                + "<p>It is valid for 10 minutes. If you did not ask for it, you can ignore this email; "
                + "nobody can open the documents without it.</p></div>";
        emailService.send(r.getEmail(), "Your code for " + reference, body);
    }

    boolean sendCollaboratorLink(SecureCollaborator c, String token) {
        if (!notificationsEnabled) return false;
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827\">"
                + "<p>Hello " + esc(c.getName()) + ",</p>"
                + "<p>You have been given a personal workspace where the documents shared with you are kept together.</p>"
                + "<p style=\"margin-top:18px\"><a href=\"" + esc(workspaceLink(token)) + "\" style=\"background:#1d4ed8;color:#fff;"
                + "padding:10px 18px;border-radius:6px;text-decoration:none\">Open my workspace</a></p>"
                + "<p>The link is personal to you and works until <strong>" + WHEN.format(c.getExpiresAt()) + "</strong>. "
                + "Each time you open it we email a six-digit code to this address first. Please do not forward the link.</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">You only see documents that were shared with you; "
                + "each download is recorded against your name.</p></div>";
        emailService.send(c.getEmail(), "Your document workspace", body);
        log.info("Collaborator link email queued collaborator={}", c.getId());
        return true;
    }

    void sendCollaboratorOtp(SecureCollaborator c, String code) {
        if (!notificationsEnabled) return;
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827\">"
                + "<p>Hello " + esc(c.getName()) + ",</p>"
                + "<p>Your code to open your document workspace is:</p>"
                + "<p style=\"font-size:26px;letter-spacing:6px;font-weight:bold\">" + esc(code) + "</p>"
                + "<p>It is valid for 10 minutes. If you did not ask for it, you can ignore this email.</p></div>";
        emailService.send(c.getEmail(), "Your workspace code", body);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
