// security/application/internal/ClientPortalService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.ClientPortalResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ClientPortalService — serves the read-only client portal view.
 *
 * The portal gives the client a real-time view of their site's security
 * operations without requiring a HandyFlow account:
 *   - Site details and contract status
 *   - Shifts for the past 7 days and next 7 days
 *   - Open incidents (OPEN and ACKNOWLEDGED only — resolved are hidden for brevity)
 *   - Checkpoint scan count for the current week (proof-of-patrol metric)
 *
 * WHY JDBC for this service?
 * The portal aggregates data across three tables (shifts, incidents, checkpoint_logs)
 * for one site.  Using JPA would require three separate query calls; JDBC lets us
 * build exactly the three queries we need with precise column selection.
 *
 * WHY show resolved incidents? We don't — only OPEN and ACKNOWLEDGED are shown.
 * The client seeing every resolved incident might cause alarm out of context.
 * The intent is operational transparency ("guards are on-site, no open issues")
 * not a full audit log.  Full history is available to the tenant in the main app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPortalService {

    private final SiteRepository siteRepository;
    private final JdbcTemplate   jdbc;

    @Transactional(readOnly = true)
    public ClientPortalResponse getPortalData(String token) {
        Site site = siteRepository.findByPortalToken(token)
                .orElseThrow(() -> new HandyFlowException(
                        "Portal not found or has been disabled",
                        HttpStatus.NOT_FOUND, "PORTAL_NOT_FOUND"));

        UUID siteId = site.getId();
        LocalDate today = LocalDate.now();

        // ── Shifts: past 7 days + next 7 days ─────────────────────────────────
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

        // ── Open incidents ─────────────────────────────────────────────────────
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

        // ── Proof-of-patrol: checkpoint scan count this week ──────────────────
        Integer weeklyScans = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_checkpoint_logs l
                JOIN security_checkpoints c ON c.id = l.checkpoint_id
                WHERE c.site_id = ?
                  AND l.scanned_at >= date_trunc('week', NOW())
                """, Integer.class, siteId);

        // ── Active guards right now ────────────────────────────────────────────
        Integer activeGuardsNow = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM security_shifts s
                WHERE s.site_id = ?
                  AND s.status = 'ACTIVE'
                  AND s.deleted_at IS NULL
                """, Integer.class, siteId);

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
                incidents
        );
    }

    /**
     * Generates (or regenerates) a portal token for a site.
     * Called by SiteController POST /sites/{id}/portal/generate
     *
     * WHY allow regeneration?
     * If the client shares the URL or it's compromised, the tenant needs a way
     * to invalidate the old token.  Regenerating creates a new UUID and the old
     * URL immediately stops working.
     */
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

    /** Disables the portal for a site by clearing the token. */
    @Transactional
    public void disablePortal(UUID siteId, za.co.handyflow.platform.shared.TenantId tenantId) {
        Site site = siteRepository.findActiveById(tenantId, siteId)
                .orElseThrow(() -> new HandyFlowException(
                        "Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND"));
        site.disablePortal();
        siteRepository.save(site);
        log.info("[Portal] Portal disabled site={} tenant={}", siteId, tenantId);
    }
}
