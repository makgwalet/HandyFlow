package za.co.handyflow.platform.approvals.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * ApprovalStep — one required approval within an ApprovalRequest. All
 * steps for a request are materialized up front at submission time
 * (every step row exists as PENDING immediately, not lazily created as
 * earlier steps complete) — SEQUENTIAL ordering is enforced as a
 * business rule at action time (ApprovalEngineService rejects acting on
 * a step while an earlier-ordered step in the same request isn't yet
 * APPROVED), not by delaying when rows get created. Simpler to reason
 * about and query ("show me every step of this request, whatever its
 * state") than a lazy-creation model would be.
 * <p>
 * approverType + approverValue together describe WHO is required,
 * resolved differently per type:
 *   USER               — approverValue is a UUID string, a specific person
 *   ROLE                — approverValue is an authority/permission name
 *                         (e.g. "AP_MANAGE"); resolved at ACTION time against
 *                         the acting user's own JWT authorities, passed in by
 *                         the calling controller — this module deliberately
 *                         never queries identity directly (see package-info)
 *   MANAGER_OF_SUBMITTER — resolution requires module-specific data (e.g.
 *                         HrEmployee.managerId) this module has no access to.
 *                         NOT resolved by this module — a calling module
 *                         that needs this must pre-resolve the manager
 *                         itself and submit a USER-type step instead.
 *   EXTERNAL_CONTACT     — approverValue is an email address, approverName
 *                         is a display name; authenticated via publicToken,
 *                         not a logged-in user. FIX: backlog 1.1 (Creative
 *                         migration) — this is now a real, exercised type,
 *                         not just schema-ready. See
 *                         ApprovalEngineService.submitAdHoc()/
 *                         actOnPublicStep() for the actual public flow.
 * <p>
 * Delegation (ApprovalDelegation) is only ever checked for USER-type
 * steps — a ROLE-type step has no single intended person to delegate
 * away from, and EXTERNAL_CONTACT steps have no platform user identity
 * to delegate at all.
 */
@Entity
@Table(name = "approval_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalStep {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "approval_request_id", nullable = false) private UUID approvalRequestId;

    @Column(name = "step_order", nullable = false) private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 30)
    private ApproverType approverType;

    @Column(name = "approver_value", length = 255) private String approverValue;
    @Column(name = "approver_name", length = 255) private String approverName; // display only, mainly for EXTERNAL_CONTACT

    /** true = acting user must differ from whoever acted on the immediately preceding step (AP's "different person" rule). */
    @Column(name = "exclude_actor_of_previous_step", nullable = false)
    private boolean excludeActorOfPreviousStep = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "acted_by") private UUID actedBy;
    @Column(name = "acted_at") private Instant actedAt;
    @Column(name = "action_comment", columnDefinition = "text") private String comment;
    @Column(name = "actor_ip", length = 64) private String actorIp;

    @Column(name = "public_token", unique = true) private String publicToken;
    @Column(name = "token_expires_at") private Instant tokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public enum ApproverType { USER, ROLE, MANAGER_OF_SUBMITTER, EXTERNAL_CONTACT }
    public enum Status { PENDING, APPROVED, REJECTED, SKIPPED, DELEGATED }

    private static final SecureRandom RANDOM = new SecureRandom();

    public static ApprovalStep create(UUID approvalRequestId, int stepOrder, ApproverType type,
                                      String approverValue, String approverName,
                                      boolean excludeActorOfPreviousStep) {
        ApprovalStep s = new ApprovalStep();
        s.approvalRequestId = approvalRequestId;
        s.stepOrder = stepOrder;
        s.approverType = type;
        s.approverValue = approverValue;
        s.approverName = approverName;
        s.excludeActorOfPreviousStep = excludeActorOfPreviousStep;
        s.status = Status.PENDING;
        s.createdAt = Instant.now();
        // FIX: backlog 1.1 — always generate a token/expiry regardless of
        // approver type. Harmless and unused for USER/ROLE steps; needed
        // for EXTERNAL_CONTACT. Simpler than conditionally generating it,
        // and a token existing on a USER/ROLE step is never reachable —
        // actOnPublicStep() only ever gets called with a token an
        // EXTERNAL_CONTACT step's own creator (Creative) actually sent out.
        s.publicToken = generateToken();
        s.tokenExpiresAt = Instant.now().plusSeconds(72 * 3600); // 72 hours, matches Creative's own prior convention
        return s;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean isTokenValid() {
        return status == Status.PENDING && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt);
    }

    public void approve(UUID actedBy, String comment, String actorIp) {
        if (status != Status.PENDING) throw new IllegalStateException("Only a PENDING step can be approved");
        this.status = Status.APPROVED;
        this.actedBy = actedBy;
        this.actedAt = Instant.now();
        this.comment = comment;
        this.actorIp = actorIp;
    }

    public void reject(UUID actedBy, String comment, String actorIp) {
        if (status != Status.PENDING) throw new IllegalStateException("Only a PENDING step can be rejected");
        this.status = Status.REJECTED;
        this.actedBy = actedBy;
        this.actedAt = Instant.now();
        this.comment = comment;
        this.actorIp = actorIp;
    }

    public void skip() {
        if (status == Status.PENDING) {
            this.status = Status.SKIPPED;
            this.actedAt = Instant.now();
        }
    }
}