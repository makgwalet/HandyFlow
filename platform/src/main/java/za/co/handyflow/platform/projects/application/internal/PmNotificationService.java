package za.co.handyflow.platform.projects.application.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.EmailService;

import java.util.Optional;
import java.util.UUID;

/**
 * Sends transactional emails for Project Management module events.
 *
 * Delegates to the platform's shared {@link EmailService} — consistent with
 * how every other module in HandyFlow sends email (same from-address, same
 * SMTP config, same async/error-handling behaviour).
 *
 * Tenant admin email is looked up via a native SQL query on the tenants table
 * (column: email, key: tenant_id UUID).  This avoids coupling to
 * TenantRepository whose method signatures may differ across modules.
 *
 * All public methods are @Async — a failed notification must never roll back
 * the business transaction that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmNotificationService {

    private final EmailService emailService;

    @PersistenceContext
    private EntityManager em;

    /** Mirrors app.notifications.enabled in application.yaml */
    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    // ─── Change Orders ────────────────────────────────────────────────────────

    @Async
    public void notifyChangeOrderApproved(UUID tenantId, String projectName,
                                          String coNumber, String approvedBy) {
        send(tenantId,
                "Change Order Approved — " + projectName,
                html("Change Order Approved",
                        "<p>A change order has been approved on <strong>" + esc(projectName) + "</strong>.</p>" +
                                kv("Change Order", coNumber) +
                                kv("Approved By",  approvedBy) +
                                "<p>The project budget has been updated. Log in to review the revised cost plan.</p>"));
    }

    // ─── Milestones ───────────────────────────────────────────────────────────

    @Async
    public void notifyMilestoneOverdue(UUID tenantId, String projectName,
                                       String milestoneTitle, String plannedEnd) {
        send(tenantId,
                "Milestone Overdue — " + projectName,
                html("Milestone Overdue",
                        "<p>A milestone on <strong>" + esc(projectName) + "</strong> is past its planned date.</p>" +
                                kv("Milestone", milestoneTitle) +
                                kv("Was Due",   plannedEnd) +
                                "<p>Log in to update the task status or adjust the schedule baseline.</p>"));
    }

    // ─── Risks ────────────────────────────────────────────────────────────────

    @Async
    public void notifyRiskEscalated(UUID tenantId, String projectName,
                                    String riskTitle, String rating) {
        String colour = "RED".equals(rating) ? "#DC2626" : "#D97706";
        send(tenantId,
                "Risk Escalated to " + rating + " — " + projectName,
                html("Risk Escalated",
                        "<p>A risk on <strong>" + esc(projectName) + "</strong> has been rated " +
                                "<strong style='color:" + colour + "'>" + rating + "</strong>.</p>" +
                                kv("Risk",   riskTitle) +
                                kv("Rating", "<span style='color:" + colour + ";font-weight:700'>" + rating + "</span>") +
                                "<p>Log in to review the risk register and update mitigation actions.</p>"));
    }

    // ─── RFIs ─────────────────────────────────────────────────────────────────

    @Async
    public void notifyRfiSubmitted(UUID tenantId, String projectName,
                                   String rfiNumber, String rfiTitle) {
        send(tenantId,
                rfiNumber + " Submitted — " + projectName,
                html("RFI Submitted",
                        "<p>A new Request for Information has been submitted on <strong>" + esc(projectName) + "</strong>.</p>" +
                                kv("RFI Number", rfiNumber) +
                                kv("Title",      rfiTitle) +
                                "<p>Log in to review and respond to the RFI.</p>"));
    }

    @Async
    public void notifyRfiResponded(UUID tenantId, String projectName,
                                   String rfiNumber, String rfiTitle, String respondedBy) {
        send(tenantId,
                rfiNumber + " Responded — " + projectName,
                html("RFI Response Received",
                        "<p>A response has been provided for an RFI on <strong>" + esc(projectName) + "</strong>.</p>" +
                                kv("RFI Number",   rfiNumber) +
                                kv("Title",        rfiTitle) +
                                kv("Responded By", respondedBy) +
                                "<p>Log in to review the response and close the RFI if satisfied.</p>"));
    }

    // ─── Send infrastructure ──────────────────────────────────────────────────

    private void send(UUID tenantId, String subject, String htmlBody) {
        if (!notificationsEnabled) {
            log.debug("[PM] Notifications disabled — skipping: {}", subject);
            return;
        }
        findAdminEmail(tenantId).ifPresentOrElse(
                email -> emailService.send(email, subject, htmlBody),
                ()    -> log.info("[PM] No admin email for tenant={} — skipping: {}", tenantId, subject)
        );
    }

    // ─── Tenant email lookup ──────────────────────────────────────────────────

    /**
     * Looks up the admin email via native SQL.
     * Works regardless of the Tenant entity's primary key strategy —
     * always queries by the tenant_id UUID column used in TenantContext.
     */
    private Optional<String> findAdminEmail(UUID tenantId) {
        try {
            String email = (String) em.createNativeQuery(
                            "SELECT email FROM tenants WHERE tenant_id = :tid AND email IS NOT NULL LIMIT 1")
                    .setParameter("tid", tenantId)
                    .getSingleResult();
            return Optional.ofNullable(email).filter(e -> !e.isBlank());
        } catch (NoResultException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[PM] Could not look up admin email for tenant={}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── HTML helpers ──────────────────────────────────────────────────────────

    private static String html(String heading, String body) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"/></head>
            <body style="font-family:Inter,Arial,sans-serif;background:#F8FAFC;padding:32px;margin:0">
              <div style="max-width:560px;margin:0 auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #E2E8F0">
                <div style="background:#1B3A6B;padding:20px 28px">
                  <div style="color:#fff;font-size:11px;letter-spacing:0.08em;text-transform:uppercase;opacity:.7;margin-bottom:4px">HandyFlow · Project Management</div>
                  <div style="color:#fff;font-size:20px;font-weight:800">""" + heading + """
</div>
                </div>
                <div style="padding:24px 28px;color:#374151;font-size:14px;line-height:1.7">
                  """ + body + """
                  <hr style="border:none;border-top:1px solid #E2E8F0;margin:24px 0 16px"/>
                  <p style="font-size:12px;color:#94A3B8;margin:0">
                    This is an automated message from HandyFlow. Do not reply to this email.
                  </p>
                </div>
              </div>
            </body></html>""";
    }

    private static String kv(String key, String value) {
        return "<table style='width:100%;border-collapse:collapse;margin:10px 0'><tr>" +
                "<td style='font-size:12px;color:#94A3B8;font-weight:600;text-transform:uppercase;" +
                "letter-spacing:0.04em;padding:5px 0;width:130px;vertical-align:top'>" + esc(key) + "</td>" +
                "<td style='font-size:14px;color:#0F172A;font-weight:600;padding:5px 0 5px 12px;" +
                "vertical-align:top'>" + value + "</td>" +
                "</tr></table>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
}