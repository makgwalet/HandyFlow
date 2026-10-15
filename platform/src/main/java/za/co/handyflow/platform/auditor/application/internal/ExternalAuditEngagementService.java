package za.co.handyflow.platform.auditor.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.auditor.domain.model.AuditShare;
import za.co.handyflow.platform.auditor.domain.model.AuditorAccessGrant;
import za.co.handyflow.platform.auditor.domain.model.ExternalAuditEngagement;
import za.co.handyflow.platform.auditor.domain.repository.AuditShareRepository;
import za.co.handyflow.platform.auditor.domain.repository.AuditorAccessGrantRepository;
import za.co.handyflow.platform.auditor.domain.repository.ExternalAuditEngagementRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The life of an external audit: planned, running, finished, archived. Ending it ends the auditors' access. */
@Service
@RequiredArgsConstructor
public class ExternalAuditEngagementService {

    private final ExternalAuditEngagementRepository repo;
    private final AuditorAccessGrantRepository grantRepo;
    private final AuditShareRepository shareRepo;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    public record AuditorLine(UUID grantId, String email, String status, Instant acceptedAt, Instant revokedAt) {}
    public record ShareLine(String itemType, UUID itemId, String reason, String sharedByName, Instant sharedAt, Instant withdrawnAt, String withdrawnReason) {}
    public record Manifest(ExternalAuditEngagement engagement, List<AuditorLine> auditors, List<ShareLine> shares) {}

    @Transactional
    public ExternalAuditEngagement create(TenantId tenantId, String name, String firm, LocalDate from, LocalDate to,
                                          String entities, String scope, UUID by) {
        ExternalAuditEngagement e = guard(() -> ExternalAuditEngagement.create(tenantId.getValue(), name, firm, from, to, entities, scope, by));
        repo.save(e);
        trail.record(tenantId, "ENGAGEMENT_CREATED", "EXTERNAL_AUDIT_ENGAGEMENT", e.getId(), null, Map.of("name", e.getName(), "firm", e.getFirmName()));
        return e;
    }

    @Transactional
    public ExternalAuditEngagement update(TenantId tenantId, UUID id, String name, String firm, LocalDate from, LocalDate to, String entities, String scope) {
        ExternalAuditEngagement e = find(tenantId, id);
        guard(() -> { e.update(name, firm, from, to, entities, scope); return e; });
        trail.record(tenantId, "ENGAGEMENT_UPDATED", "EXTERNAL_AUDIT_ENGAGEMENT", id, null, Map.of("name", e.getName(), "firm", e.getFirmName()));
        return repo.save(e);
    }

    @Transactional
    public ExternalAuditEngagement start(TenantId tenantId, UUID id) {
        ExternalAuditEngagement e = find(tenantId, id);
        guard(() -> { e.start(); return e; });
        trail.record(tenantId, "ENGAGEMENT_STARTED", "EXTERNAL_AUDIT_ENGAGEMENT", id, Map.of("status", "PLANNED"), Map.of("status", "ACTIVE"));
        return repo.save(e);
    }

    /** Auditors attached to it lose access at once, because their access depends on the engagement being active. */
    @Transactional
    public ExternalAuditEngagement complete(TenantId tenantId, UUID id, UUID by) {
        ExternalAuditEngagement e = find(tenantId, id);
        guard(() -> { e.complete(by); return e; });
        trail.record(tenantId, "ENGAGEMENT_COMPLETED", "EXTERNAL_AUDIT_ENGAGEMENT", id, Map.of("status", "ACTIVE"), Map.of("status", "COMPLETED"));
        return repo.save(e);
    }

    /** Archiving also revokes every grant that belonged to the engagement, so none can be revived by re-opening it. */
    @Transactional
    public ExternalAuditEngagement archive(TenantId tenantId, UUID id, UUID by) {
        ExternalAuditEngagement e = find(tenantId, id);
        guard(() -> { e.archive(by); return e; });
        int revoked = 0;
        for (AuditorAccessGrant g : grantRepo.findByTenantIdAndEngagementId(tenantId.getValue(), id)) {
            if (!"REVOKED".equals(g.getStatus())) { g.revoke(by); grantRepo.save(g); revoked++; }
        }
        trail.record(tenantId, "ENGAGEMENT_ARCHIVED", "EXTERNAL_AUDIT_ENGAGEMENT", id, Map.of("status", "COMPLETED"),
                Map.of("status", "ARCHIVED", "grantsRevoked", revoked));
        return repo.save(e);
    }

    @Transactional(readOnly = true)
    public List<ExternalAuditEngagement> list(TenantId tenantId) {
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId.getValue());
    }

    /**
     * What the engagement had access to, for the file: who was let in and what was shared. Sharing is still decided for the business
     * as a whole (not per engagement), so the share list is every share that was live at any point in the engagement's life.
     */
    @Transactional(readOnly = true)
    public Manifest manifest(TenantId tenantId, UUID id) {
        ExternalAuditEngagement e = find(tenantId, id);
        List<AuditorLine> auditors = grantRepo.findByTenantIdAndEngagementId(tenantId.getValue(), id).stream()
                .map(g -> new AuditorLine(g.getId(), g.getInviteEmail(), g.getStatus(), g.getAcceptedAt(), g.getRevokedAt())).toList();
        Instant from = e.getStartedAt() != null ? e.getStartedAt() : e.getCreatedAt();
        Instant to = e.getCompletedAt() != null ? e.getCompletedAt() : Instant.now();
        List<ShareLine> shares = shareRepo.findAllForTenant(tenantId.getValue()).stream()
                .filter(s -> overlaps(s, from, to))
                .map(s -> new ShareLine(s.getItemType(), s.getItemId(), s.getReason(), s.getSharedByName(), s.getSharedAt(), s.getWithdrawnAt(), s.getWithdrawnReason()))
                .toList();
        return new Manifest(e, auditors, shares);
    }

    static boolean overlaps(AuditShare s, Instant from, Instant to) {
        Instant end = s.getWithdrawnAt() != null ? s.getWithdrawnAt() : Instant.MAX;
        return !s.getSharedAt().isAfter(to) && !end.isBefore(from);
    }

    private ExternalAuditEngagement find(TenantId tenantId, UUID id) {
        return repo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new ResourceNotFoundException("ExternalAuditEngagement", id.toString()));
    }

    private <T> T guard(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "ENGAGEMENT_INVALID");
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "ENGAGEMENT_STATE");
        }
    }
}
