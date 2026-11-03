package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * Emails for the RFI workflow: assigned to you, clarification requested, and overdue escalation.
 * (Submission and response notices stay in PmNotificationService.)
 *
 * Every method is @Async on the bounded "notificationExecutor" pool and swallows its own failures —
 * a failed email must never roll back the RFI change that triggered it. Recipients are de-duplicated
 * by email address, so someone who is both assignee and tenant admin gets one message, not two.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfiNotificationService {

    private final EmailService          emailService;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final UserRecipientResolver userRecipientResolver;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Async("notificationExecutor")
    public void notifyAssigned(UUID tenantId, String projectName, String rfiNumber, String title,
                               UUID assigneeId, String assignedBy) {
        if (assigneeId == null) return;
        send(recipients(tenantId, assigneeId, false),
                rfiNumber + " assigned to you — " + projectName,
                html("RFI Assigned",
                        "<p>You have been asked to respond to an RFI on <strong>" + esc(projectName) + "</strong>.</p>"
                                + kv("RFI", rfiNumber) + kv("Title", title) + kv("Assigned by", assignedBy)
                                + "<p>Log in to review the question and respond.</p>"));
    }

    @Async("notificationExecutor")
    public void notifyClarification(UUID tenantId, String projectName, String rfiNumber, String title,
                                    String comment, UUID responderId) {
        if (responderId == null) return;
        send(recipients(tenantId, responderId, false),
                rfiNumber + " — clarification requested — " + projectName,
                html("Clarification Requested",
                        "<p>More information is needed on an RFI you answered on <strong>" + esc(projectName) + "</strong>.</p>"
                                + kv("RFI", rfiNumber) + kv("Title", title) + kv("Question", comment)
                                + "<p>Log in to respond.</p>"));
    }

    /** Level 2 and 3 are worded as urgent. Goes to the assignee (if any) and the tenant admins. */
    @Async("notificationExecutor")
    public void notifyOverdue(UUID tenantId, String projectName, String rfiNumber, String title,
                              LocalDate dueDate, int daysOverdue, int level, UUID assigneeId) {
        String urgency = level >= 3 ? "URGENT — " : level == 2 ? "Overdue a week — " : "Overdue — ";
        send(recipients(tenantId, assigneeId, true),
                urgency + rfiNumber + " unanswered — " + projectName,
                html("RFI Overdue",
                        "<p>An RFI on <strong>" + esc(projectName) + "</strong> is <strong>" + daysOverdue
                                + " day(s)</strong> past its due date and still unanswered.</p>"
                                + kv("RFI", rfiNumber) + kv("Title", title) + kv("Was due", String.valueOf(dueDate))
                                + "<p>Unanswered RFIs can hold up site work. Log in to respond or escalate.</p>"));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<Recipient> recipients(UUID tenantId, UUID userId, boolean includeAdmins) {
        Map<String, Recipient> byEmail = new LinkedHashMap<>();
        try {
            if (userId != null) {
                userRecipientResolver.resolveUser(TenantId.of(tenantId), userId)
                        .ifPresent(r -> add(byEmail, r));
            }
            if (includeAdmins) {
                tenantAdminRecipients.resolveTenantAdmins(TenantId.of(tenantId)).forEach(r -> add(byEmail, r));
            }
        } catch (Exception e) {
            log.warn("[RFI] Could not resolve notification recipients for tenant={}: {}", tenantId, e.getMessage());
        }
        return List.copyOf(byEmail.values());
    }

    private static void add(Map<String, Recipient> byEmail, Recipient r) {
        if (r.email() != null && !r.email().isBlank()) byEmail.putIfAbsent(r.email().toLowerCase(), r);
    }

    private void send(List<Recipient> to, String subject, String body) {
        if (!notificationsEnabled) {
            log.debug("[RFI] Notifications disabled — skipping: {}", subject);
            return;
        }
        for (Recipient r : to) {
            try {
                emailService.send(r.email(), subject, body);
            } catch (Exception e) {
                log.error("[RFI] Failed to email {}: {}", r.email(), e.getMessage());
            }
        }
    }

    private static String html(String heading, String body) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/></head>"
                + "<body style=\"font-family:Inter,Arial,sans-serif;background:#F8FAFC;padding:32px;margin:0\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #E2E8F0\">"
                + "<div style=\"background:#1B3A6B;padding:20px 28px\">"
                + "<div style=\"color:#fff;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;opacity:.7;margin-bottom:4px\">HandyFlow · Project Management</div>"
                + "<div style=\"color:#fff;font-size:20px;font-weight:800\">" + esc(heading) + "</div></div>"
                + "<div style=\"padding:24px 28px;color:#374151;font-size:14px;line-height:1.7\">" + body
                + "<hr style=\"border:none;border-top:1px solid #E2E8F0;margin:24px 0 16px\"/>"
                + "<p style=\"font-size:12px;color:#94A3B8;margin:0\">This is an automated message from HandyFlow. Do not reply to this email.</p>"
                + "</div></div></body></html>";
    }

    private static String kv(String key, String value) {
        return "<table style='width:100%;border-collapse:collapse;margin:10px 0'><tr>"
                + "<td style='font-size:12px;color:#94A3B8;font-weight:600;text-transform:uppercase;letter-spacing:0.04em;padding:5px 0;width:130px;vertical-align:top'>"
                + esc(key) + "</td><td style='font-size:14px;color:#0F172A;font-weight:600;padding:5px 0 5px 12px;vertical-align:top'>"
                + esc(value) + "</td></tr></table>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}