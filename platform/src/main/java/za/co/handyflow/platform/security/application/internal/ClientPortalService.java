// security/application/internal/ClientPortalService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.ClientPortalResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ClientPortalService — serves the read-only client portal view, plus
 * an on-demand "send the portal link" action.
 * <p>
 * FIX: Gate Access & Registry, Step 5. getPortalData() now also returns
 * currentlyOnSiteCount/currentlyOnSite — same raw-JDBC, Map&lt;String,
 * Object&gt; convention as shifts/incidents above, for internal
 * consistency with how this whole class already works. The underlying
 * query deliberately never selects id_number or phone at all (not just
 * omits them at mapping time) — this endpoint is unauthenticated and
 * external-facing, the token in the URL is the only credential.
 * <p>
 * WHY recipientEmail supplied at send time, not stored on Site?
 * Site has no contactEmail field (confirmed against the actual entity --
 * only contactName/contactPhone exist). Rather than add one speculatively,
 * this keeps the schema unchanged and takes the recipient as part of the
 * send request -- matches "a trigger to send when requested" more directly
 * than assuming a stored default recipient is what's wanted. A contactEmail
 * field + "send to default contact" convenience is a natural follow-up if
 * repeatedly retyping the same address becomes annoying in practice.
 * <p>
 * WHY EmailService.send() directly, not NotificationService?
 * NotificationService/TenantAdminRecipients resolve INTERNAL tenant admins
 * as recipients -- there's no path from that pipeline to an arbitrary
 * external client email address. EmailService.send(to, subject, html) is
 * the lower-level primitive documented elsewhere in this codebase as the
 * established workaround for exactly this case (external recipient, no
 * NotificationType needed).
 * <p>
 * NOTE: I have not seen EmailService's actual interface/package directly --
 * za.co.handyflow.platform.notifications.application.internal.EmailService
 * is inferred by co-location with NotificationService, which lives in the
 * same package. Verify the import path compiles; if EmailService lives
 * elsewhere, that's a one-line fix.
 * <p>
 * NOTE: portalBaseUrl is read from a new config property
 * (app.frontend.base-url) that may not exist in your application.yml yet --
 * without it, the emailed link falls back to a relative path, which won't
 * work outside the app itself. Add the property (e.g.
 * https://app.handyflow.co.za) before relying on this in production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPortalService {

    private final SiteRepository siteRepository;
    private final JdbcTemplate   jdbc;
    private final EmailService emailService;

    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public ClientPortalResponse getPortalData(String token) {
        Site site = siteRepository.findByPortalToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Portal not found or has been disabled",
                        HttpStatus.NOT_FOUND, "PORTAL_NOT_FOUND"));

        UUID siteId = site.getId();
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> shifts = jdbc.queryForList("""
                SELECT
                    s.id, s.status, s.start_at, s.end_at,
                    (g.first_name || ' ' || g.last_name) AS guard_name,
                    g.grade
                FROM security_shifts s
                LEFT JOIN security_guards g ON g.id = s.guard_id
                WHERE s.site_id = ?
                  AND s.deleted_at IS NULL
                  AND s.status NOT IN ('CANCELLED')
                  AND s.start_at >= (NOW() - INTERVAL '7 days')
                  AND s.start_at <= (NOW() + INTERVAL '7 days')
                ORDER BY s.start_at DESC
                LIMIT 50
                """, siteId);

        List<Map<String, Object>> incidents = jdbc.queryForList("""
                SELECT
                    i.id, i.title, i.severity, i.status, i.type,
                    i.created_at, i.acknowledged_at
                FROM security_incidents i
                WHERE i.site_id = ?
                  AND i.status IN ('OPEN', 'ACKNOWLEDGED')
                ORDER BY i.created_at DESC
                LIMIT 20
                """, siteId);

        Integer weeklyScans = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_checkpoint_logs l
                JOIN security_checkpoints c ON c.id = l.checkpoint_id
                WHERE c.site_id = ?
                  AND l.scanned_at >= date_trunc('week', NOW())
                """, Integer.class, siteId);

        Integer activeGuardsNow = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_shifts s
                WHERE s.site_id = ?
                  AND s.status = 'ACTIVE'
                  AND s.deleted_at IS NULL
                """, Integer.class, siteId);

        // FIX: Gate Access & Registry, Step 5. Deliberately never selects
        // id_number or phone — this endpoint is unauthenticated and
        // external-facing (the token in the URL is the only credential),
        // same POPIA posture as every other choice in this class about
        // what an outside client is allowed to see.
        Integer onSiteCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_gate_register_entries e
                WHERE e.site_id = ?
                  AND e.logged_out_at IS NULL
                """, Integer.class, siteId);

        List<Map<String, Object>> onSite = jdbc.queryForList("""
                SELECT
                    e.id, e.entry_type, e.person_name, e.company,
                    e.vehicle_registration, e.host_name, e.logged_in_at, e.status
                FROM security_gate_register_entries e
                WHERE e.site_id = ?
                  AND e.logged_out_at IS NULL
                ORDER BY e.logged_in_at DESC
                LIMIT 50
                """, siteId);

        log.info("[Portal] View site={} token={}...{}", site.getName(),
                token.substring(0, 8), token.substring(token.length() - 4));

        return new ClientPortalResponse(
                site.getId(),
                site.getPortalLabel() != null ? site.getPortalLabel() : site.getName(),
                site.getContractStatus(),
                site.getContractStart(),
                site.getContractEnd(),
                activeGuardsNow != null ? activeGuardsNow : 0,
                weeklyScans    != null ? weeklyScans    : 0,
                shifts,
                incidents,
                onSiteCount != null ? onSiteCount : 0,
                onSite
        );
    }

    @Transactional
    public String generatePortalToken(UUID siteId, String label,
                                      za.co.handyflow.platform.shared.TenantId tenantId) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new HandyFlowException(
                        "Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND"));
        String token = site.generatePortalToken(label);
        siteRepository.save(site);
        log.info("[Portal] Token generated site={} tenant={}", siteId, tenantId);
        return token;
    }

    @Transactional
    public void disablePortal(UUID siteId, za.co.handyflow.platform.shared.TenantId tenantId) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new HandyFlowException(
                        "Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND"));
        site.disablePortal();
        siteRepository.save(site);
        log.info("[Portal] Portal disabled site={} tenant={}", siteId, tenantId);
    }

    // ── Send portal link ──────────────────────────────────────────────────────

    /**
     * Emails the client portal link to an arbitrary recipient. Requires the
     * portal to already be enabled (generate it first via
     * POST /sites/{id}/portal/generate) -- this method does not implicitly
     * generate one, since silently creating a new token as a side effect of
     * "send" would be surprising if the caller expected to resend an
     * existing, already-shared link.
     */
    @Transactional(readOnly = true)
    public void sendPortalLink(UUID siteId, za.co.handyflow.platform.shared.TenantId tenantId,
                               String recipientEmail, String customMessage) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new HandyFlowException(
                        "Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND"));

        if (!site.isPortalEnabled() || site.getPortalToken() == null) {
            throw new HandyFlowException(
                    "This site has no active portal link yet — generate one first",
                    HttpStatus.CONFLICT, "PORTAL_NOT_ENABLED");
        }

        String portalPath = "/portal/" + site.getPortalToken();
        String portalUrl  = (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                ? frontendBaseUrl.replaceAll("/$", "") + portalPath
                : portalPath;

        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            log.warn("[Portal] app.frontend.base-url is not configured — emailed link will be a "
                    + "relative path ({}), which will not work outside the app. Set the property "
                    + "before relying on this in production.", portalPath);
        }

        String subject = "Your security portal link — " + site.getName();
        String html = buildEmailHtml(site, portalUrl, customMessage);

        emailService.send(recipientEmail, subject, html);

        log.info("[Portal] Link sent site={} tenant={} to={}",
                siteId, tenantId.getValue(), recipientEmail);
    }

    private String buildEmailHtml(Site site, String portalUrl, String customMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Hello,</p>");
        if (customMessage != null && !customMessage.isBlank()) {
            sb.append("<p>").append(escapeHtml(customMessage)).append("</p>");
        }
        sb.append("<p>Here is your live security portal link for <strong>")
                .append(escapeHtml(site.getName())).append("</strong>:</p>")
                .append("<p><a href=\"").append(portalUrl).append("\">").append(portalUrl).append("</a></p>")
                .append("<p>This link shows current guards on duty, recent shifts, open incidents, "
                        + "and checkpoint scan activity for your site in real time.</p>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}