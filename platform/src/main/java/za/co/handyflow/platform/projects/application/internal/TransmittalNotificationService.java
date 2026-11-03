package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittal;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalItem;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalRecipient;
import za.co.handyflow.platform.shared.EmailService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The two emails of secure transmittal access: the transmittal itself (register of documents plus the
 * recipient's own link) and the one-time code.
 *
 * Documents are NEVER attached. The email carries the register (number, title, revision, status), the
 * purpose, who sent it and when the link expires; the files are fetched through the link after the
 * recipient proves their email address. EmailService.send is itself @Async and swallows delivery
 * failures, so a mail problem can never roll back the transmittal; the sender sees "email queued" in
 * the access log and can resend.
 *
 * Nothing sensitive is logged: not the link, not the code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransmittalNotificationService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    /** The link the recipient opens. Public front-end route; the token is the only thing in it. */
    public String linkFor(String token) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/transmittal/" + token;
    }

    /** Returns false when notifications are switched off, so the caller can say the email was not queued. */
    public boolean sendTransmittal(ProjectTransmittal t, ProjectTransmittalRecipient r, String token,
                                   List<ProjectTransmittalItem> items, String projectName) {
        if (!notificationsEnabled) return false;
        StringBuilder rows = new StringBuilder();
        for (ProjectTransmittalItem i : items) {
            rows.append("<tr><td style=\"padding:4px 10px;border-bottom:1px solid #e5e7eb\">").append(esc(nz(i.getDocumentNumber())))
                .append("</td><td style=\"padding:4px 10px;border-bottom:1px solid #e5e7eb\">").append(esc(i.getTitle()))
                .append("</td><td style=\"padding:4px 10px;border-bottom:1px solid #e5e7eb\">").append(esc(nz(i.getRevision())))
                .append("</td><td style=\"padding:4px 10px;border-bottom:1px solid #e5e7eb\">").append(esc(i.getStatusAtSend()))
                .append("</td></tr>");
        }
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827\">"
                + "<p>Hello " + esc(r.getName()) + ",</p>"
                + "<p><strong>" + esc(t.getSentByName() == null ? "A project team member" : t.getSentByName())
                + "</strong> has issued documents to you on <strong>" + esc(projectName) + "</strong>.</p>"
                + "<p>Transmittal <strong>" + esc(t.getTransmittalNumber()) + "</strong> — " + esc(t.getSubject())
                + "<br>Purpose: <strong>" + esc(t.getPurpose().replace('_', ' ').toLowerCase()) + "</strong></p>"
                + (t.getNotes() != null ? "<p>" + esc(t.getNotes()) + "</p>" : "")
                + "<table style=\"border-collapse:collapse;font-size:13px\"><tr style=\"background:#f3f4f6\">"
                + "<th align=\"left\" style=\"padding:4px 10px\">Number</th><th align=\"left\" style=\"padding:4px 10px\">Title</th>"
                + "<th align=\"left\" style=\"padding:4px 10px\">Rev</th><th align=\"left\" style=\"padding:4px 10px\">Status</th></tr>"
                + rows + "</table>"
                + "<p style=\"margin-top:18px\"><a href=\"" + esc(linkFor(token)) + "\" style=\"background:#1d4ed8;color:#fff;"
                + "padding:10px 18px;border-radius:6px;text-decoration:none\">Open the documents</a></p>"
                + "<p>This link is personal to you and works until <strong>" + WHEN.format(r.getExpiresAt()) + "</strong>. "
                + "We will email a six-digit code to this address to confirm it is you before any document opens. "
                + "Please do not forward the link; it will not open for anyone else.</p>"
                + "<p style=\"color:#6b7280;font-size:12px\">The documents are not attached to this email. "
                + "Each download is recorded against your name.</p></div>";
        emailService.send(r.getEmail(), t.getTransmittalNumber() + " — " + t.getSubject() + " — " + projectName, body);
        log.info("Transmittal email queued transmittal={} recipient={}", t.getId(), r.getId());
        return true;
    }

    public void sendOtp(ProjectTransmittalRecipient r, String code, String transmittalNumber) {
        if (!notificationsEnabled) return;
        String body = "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#111827\">"
                + "<p>Hello " + esc(r.getName()) + ",</p>"
                + "<p>Your code to open transmittal <strong>" + esc(transmittalNumber) + "</strong> is:</p>"
                + "<p style=\"font-size:26px;letter-spacing:6px;font-weight:bold\">" + esc(code) + "</p>"
                + "<p>It is valid for 10 minutes. If you did not ask for it, you can ignore this email; "
                + "nobody can open the documents without it.</p></div>";
        emailService.send(r.getEmail(), "Your code for " + transmittalNumber, body);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
