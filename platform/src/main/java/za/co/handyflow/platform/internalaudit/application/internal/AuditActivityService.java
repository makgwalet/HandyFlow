package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade.Activity;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditEngagementRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.internalaudit.domain.repository.AuditWorkpaperFileRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The recent happenings in internal audit, read from the audit trail and given the name of the thing they happened to. */
@Service
@RequiredArgsConstructor
public class AuditActivityService {
    /** The codes internal audit writes. Matched by pattern so that new steps of an existing kind show up without a change here. */
    static final List<String> PATTERNS = List.of(
            "FINDING\\_%", "WORKPAPER\\_%", "REPORT\\_SUBMITTED", "REPORT\\_REOPENED", "AUDIT\\_REPORT\\_%", "AUDIT\\_FOLLOWUP\\_%",
            "AUDIT\\_PROGRAMME\\_%", "AUDIT\\_MONITOR\\_REVIEWED", "AUDIT\\_QR\\_%", "REVIEW\\_NOTE\\_%", "ACTION\\_PLAN\\_%");
    static final int DEFAULT_LIMIT = 8;

    public record Item(String actor, String action, String itemType, UUID itemId, String subject, Instant at) {}

    private final AuditTrailFacade trail;
    private final AuditFindingRepository findings;
    private final AuditEngagementRepository engagements;
    private final AuditWorkpaperFileRepository files;

    @Transactional(readOnly = true)
    public List<Item> recent(TenantId tenant, int limit) {
        UUID t = tenant.getValue();
        return trail.recent(tenant, PATTERNS, limit <= 0 ? DEFAULT_LIMIT : limit).stream()
                .map(a -> new Item(a.actorName(), a.action(), a.itemType(), a.itemId(), subject(t, a), a.at())).toList();
    }

    /** The finding's title, the engagement's name or the workpaper's file name, when the event is about one and it still exists. */
    private String subject(UUID t, Activity a) {
        if (a.itemId() == null || a.itemType() == null) return null;
        return switch (a.itemType()) {
            case "FINDING", "AUDIT_FINDING" -> findings.findByTenantAndId(t, a.itemId()).map(f -> f.getTitle()).orElse(null);
            case "ENGAGEMENT", "AUDIT_ENGAGEMENT" -> engagements.findByTenantAndId(t, a.itemId()).map(e -> e.getName()).orElse(null);
            case "WORKPAPER" -> files.findByTenantAndId(t, a.itemId()).map(f -> f.getFileName()).orElse(null);
            default -> null;
        };
    }
}
