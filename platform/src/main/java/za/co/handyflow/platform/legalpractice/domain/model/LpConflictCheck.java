package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/**
 * A conflict-of-interest search for one client before a matter is opened. Never deleted: it is the firm's record that
 * the search was done, what it found, and who decided what to do about it.
 * <p>
 * CLEAR (nothing found, automatic)  |  PENDING_REVIEW (something found, or the search could not be complete)
 * → CLEARED_AFTER_REVIEW (a person looked and it is not a real conflict) | PROCEED_WITH_CONSENT (a real conflict, with
 * informed consent on file) | DECLINED (the firm will not act). The first three outcomes let one matter be opened within
 * {@link #VALID_DAYS} days; a check opens at most one matter.
 */
@Entity
@Table(name = "lp_conflict_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpConflictCheck {

    public static final int VALID_DAYS = 30;
    public static final int MIN_REASON = 10;
    public static final Set<String> REVIEW_OUTCOMES = Set.of("CLEARED_AFTER_REVIEW", "PROCEED_WITH_CONSENT", "DECLINED");
    public static final Set<String> USABLE = Set.of("CLEAR", "CLEARED_AFTER_REVIEW", "PROCEED_WITH_CONSENT");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    /** The client's name as it was when the search ran. */
    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(nullable = false, length = 24)
    private String status;

    /** True when a search term matched so many records that not all could be compared: a person must review. */
    @Column(nullable = false)
    private boolean truncated;

    @Column(name = "run_by", nullable = false)
    private UUID runBy;

    @Column(name = "run_by_name", length = 255)
    private String runByName;

    @Column(name = "run_at", nullable = false, updatable = false)
    private Instant runAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    /** The person who ran the search also reviewed it (only possible when the firm allows it). */
    @Column(name = "self_reviewed", nullable = false)
    private boolean selfReviewed = false;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "matter_id")
    private UUID matterId;

    @Version
    private Long version;

    public static LpConflictCheck run(UUID id, TenantId tenantId, UUID clientId, String clientName, int hitCount, boolean truncated,
                                      UUID runBy, String runByName) {
        if (runBy == null) throw new IllegalArgumentException("A conflict check must say who ran it");
        LpConflictCheck c = new LpConflictCheck();
        c.id = id == null ? c.id : id;
        c.tenantId = tenantId;
        c.clientId = clientId;
        c.clientName = clientName;
        c.truncated = truncated;
        c.runBy = runBy;
        c.runByName = runByName;
        c.runAt = Instant.now();
        if (hitCount == 0 && !truncated) {
            c.status = "CLEAR";
            c.validUntil = c.runAt.plus(VALID_DAYS, ChronoUnit.DAYS);
        } else {
            c.status = "PENDING_REVIEW";
        }
        return c;
    }

    public void decide(String outcome, String reason, UUID by, String byName, boolean selfReviewAllowed) {
        if (!"PENDING_REVIEW".equals(status)) {
            throw new IllegalStateException("This check has already been decided (" + status.toLowerCase().replace('_', ' ') + ")");
        }
        if (outcome == null || !REVIEW_OUTCOMES.contains(outcome)) {
            throw new IllegalArgumentException("The outcome must be one of " + REVIEW_OUTCOMES);
        }
        if (reason == null || reason.trim().length() < MIN_REASON) {
            throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters");
        }
        if (by == null) throw new IllegalArgumentException("A decision must say who made it");
        boolean self = by.equals(runBy);
        if (self && !selfReviewAllowed) {
            throw new IllegalStateException("A conflict check must be reviewed by someone other than the person who ran it");
        }
        this.status = outcome;
        this.decidedBy = by;
        this.decidedByName = byName;
        this.decidedAt = Instant.now();
        this.decisionReason = reason.trim();
        this.selfReviewed = self;
        this.validUntil = USABLE.contains(outcome) ? decidedAt.plus(VALID_DAYS, ChronoUnit.DAYS) : null;
    }

    /** Can this check open a matter right now? */
    public boolean isUsableAt(Instant now) {
        return USABLE.contains(status) && matterId == null && validUntil != null && now.isBefore(validUntil);
    }

    /** Why this check cannot open a matter, or null when it can. */
    public String problemFor(UUID forClient, Instant now) {
        if (!clientId.equals(forClient)) return "This conflict check was run for a different client";
        if (matterId != null) return "This conflict check has already been used to open a matter";
        if ("PENDING_REVIEW".equals(status)) return "This conflict check is waiting for review";
        if ("DECLINED".equals(status)) return "The firm declined to act after this conflict check";
        if (!USABLE.contains(status)) return "This conflict check cannot be used";
        if (validUntil == null || !now.isBefore(validUntil)) return "This conflict check has expired. Run it again";
        return null;
    }

    public void consume(UUID forMatter, Instant now) {
        String problem = problemFor(clientId, now);
        if (problem != null) throw new IllegalStateException(problem);
        this.matterId = forMatter;
    }
}
