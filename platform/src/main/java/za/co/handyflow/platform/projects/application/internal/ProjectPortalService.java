package za.co.handyflow.platform.projects.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.PortalStatusResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.RateLimiter;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The ONLY way to turn a client-portal token into a Project.
 *
 * resolve() — public, unauthenticated path:
 *   1. rate-limits per client IP (shared DB-backed RateLimiter, safe across instances)
 *   2. rejects anything that isn't a 32-char lowercase hex token before touching the DB
 *   3. rejects unknown, disabled and expired tokens
 *   Steps 2–3 all return the SAME 404 — a caller can't tell "never existed" from
 *   "revoked" or "expired".
 *
 * resolve() is deliberately NOT @Transactional: RateLimiter.tryConsume() writes, and
 * joining a read-only transaction would silently skip the flush, so the counter would
 * never persist and the limit would never trigger.
 *
 * Admin operations (rotate / revoke / enable / status) are tenant-scoped.
 */
@Slf4j
@Service
public class ProjectPortalService {

    private static final Pattern TOKEN_FORMAT = Pattern.compile("^[0-9a-f]{32}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectRepository projectRepo;
    private final RateLimiter       rateLimiter;
    private final int               defaultTtlDays;
    private final int               rateMaxRequests;
    private final long              rateWindowMs;

    public ProjectPortalService(
            ProjectRepository projectRepo,
            RateLimiter rateLimiter,
            @Value("${app.projects.portal.token-ttl-days:365}") int defaultTtlDays,
            @Value("${app.projects.portal.rate-limit.max-requests:60}") int rateMaxRequests,
            @Value("${app.projects.portal.rate-limit.window-ms:60000}") long rateWindowMs) {
        this.projectRepo     = projectRepo;
        this.rateLimiter     = rateLimiter;
        this.defaultTtlDays  = defaultTtlDays;
        this.rateMaxRequests = rateMaxRequests;
        this.rateWindowMs    = rateWindowMs;
    }

    // ── Public resolution ─────────────────────────────────────────────────────

    public Project resolve(String token, String clientIp) {
        String ip = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp;
        if (!rateLimiter.tryConsume("projects:portal:" + ip, rateMaxRequests, rateWindowMs)) {
            throw new HandyFlowException(
                    "Too many requests — please try again shortly",
                    HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        }

        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw notFound();
        }
        Project p = projectRepo.findByPortalToken(token).orElseThrow(ProjectPortalService::notFound);
        if (!p.isPortalAccessible(Instant.now())) {
            log.info("Portal access refused (disabled/expired) project={}", p.getId());
            throw notFound();
        }
        return p;
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    /** Issues a new token (old link dies immediately) and re-enables access. ttlDays null → default; 0 → never expires. */
    @Transactional
    public PortalStatusResponse rotateToken(TenantId tenantId, UUID projectId, Integer ttlDays) {
        Project p = load(tenantId, projectId);
        int ttl = ttlDays != null ? ttlDays : defaultTtlDays;
        Instant expiresAt = ttl > 0 ? Instant.now().plus(Duration.ofDays(ttl)) : null;
        p.rotatePortalToken(newToken(), expiresAt);
        projectRepo.save(p);
        log.info("Portal token rotated project={} expiresAt={}", projectId, expiresAt);
        return status(p, true);
    }

    @Transactional
    public PortalStatusResponse revoke(TenantId tenantId, UUID projectId) {
        Project p = load(tenantId, projectId);
        p.disablePortal();
        projectRepo.save(p);
        log.info("Portal access revoked project={}", projectId);
        return status(p, false);
    }

    /** Re-enables the current token. If it has expired, rotate instead — enabling alone won't revive it. */
    @Transactional
    public PortalStatusResponse enable(TenantId tenantId, UUID projectId) {
        Project p = load(tenantId, projectId);
        p.enablePortal();
        projectRepo.save(p);
        log.info("Portal access enabled project={}", projectId);
        return status(p, false);
    }

    @Transactional(readOnly = true)
    public PortalStatusResponse getStatus(TenantId tenantId, UUID projectId) {
        return status(load(tenantId, projectId), false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Project load(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private static PortalStatusResponse status(Project p, boolean includeToken) {
        return new PortalStatusResponse(
                p.isPortalEnabled(),
                p.isPortalAccessible(Instant.now()),
                p.getPortalTokenExpiresAt(),
                includeToken ? p.getClientPortalToken() : null);
    }

    /** 128 bits from SecureRandom, lowercase hex — matches TOKEN_FORMAT. */
    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static HandyFlowException notFound() {
        return new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}