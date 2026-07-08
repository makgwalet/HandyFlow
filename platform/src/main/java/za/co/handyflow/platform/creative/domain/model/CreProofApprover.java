package za.co.handyflow.platform.creative.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per required approver on a multi-stakeholder proof —
 * "marketing manager approves, then legal approves" (sequential) or "both
 * need to sign off, in either order" (parallel). Deliberately mirrors the
 * same shape as Contracting's ContractParty (approvalOrder ~ signingOrder,
 * status ~ signingStatus, its own token ~ the party's own signing token) —
 * a proven pattern in this exact codebase, reused rather than reinvented.
 * <p>
 * Existing single-approver proofs (CreProof.approvalMode == "SINGLE") never
 * create rows here at all — this table only exists for proofs a staff
 * member has explicitly configured for multi-stakeholder approval. This is
 * fully additive: nothing about the existing single-approver flow changes
 * for anyone who doesn't opt in.
 */
@Entity
@Table(name = "cre_proof_approvers")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CreProofApprover {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "proof_id",   nullable = false) private UUID proofId;
    @Column(name = "tenant_id",  nullable = false) private UUID tenantId;

    @Column(name = "approver_name",  nullable = false) private String approverName;
    @Column(name = "approver_email", nullable = false) private String approverEmail;

    // Position in the chain. Gates ordering for SEQUENTIAL proofs (lower
    // order must approve before a later one is allowed to act, enforced in
    // CreativeService.resolveToken() — not just by "we didn't email them
    // yet"); purely a display/notification order for PARALLEL proofs.
    @Column(name = "approval_order", nullable = false) private int approvalOrder;

    @Column(nullable = false) private String status = "PENDING"; // PENDING | APPROVED | REJECTED

    @Column(name = "approval_token",   nullable = false, unique = true) private String  approvalToken;
    @Column(name = "token_expires_at", nullable = false)                private Instant tokenExpiresAt;

    // Null until this approver's link is actually sent — for SEQUENTIAL,
    // that's only once it's their turn; for PARALLEL, every approver gets
    // sentAt set at once, when the proof is sent.
    @Column(name = "sent_at") private Instant sentAt;

    @Column(name = "approved_at")      private Instant approvedAt;
    @Column(name = "approved_by_ip")   private String  approvedByIp;
    @Column(name = "rejection_reason") private String  rejectionReason;

    @Column(name = "created_at") private Instant createdAt;

    public static CreProofApprover create(UUID proofId, UUID tenantId, String approverName,
                                          String approverEmail, int approvalOrder) {
        CreProofApprover a = new CreProofApprover();
        a.proofId       = proofId;
        a.tenantId      = tenantId;
        a.approverName  = approverName;
        a.approverEmail = approverEmail;
        a.approvalOrder = approvalOrder;
        a.status        = "PENDING";
        // Same token-generation approach as CreProof's own approvalToken —
        // two random UUIDs concatenated for a longer, non-enumerable token.
        a.approvalToken  = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        a.tokenExpiresAt = Instant.now().plusSeconds(72 * 3600); // 72 hours
        a.createdAt      = Instant.now();
        return a;
    }

    public void markSent() {
        this.sentAt = Instant.now();
    }

    public void approve(String ip) {
        this.status       = "APPROVED";
        this.approvedAt   = Instant.now();
        this.approvedByIp = ip;
    }

    public void reject(String reason) {
        this.status          = "REJECTED";
        this.rejectionReason = reason;
    }

    public boolean isPending()  { return "PENDING".equals(status); }
    public boolean isApproved() { return "APPROVED".equals(status); }
    public boolean isRejected() { return "REJECTED".equals(status); }

    public boolean isTokenValid() {
        return isPending() && Instant.now().isBefore(tokenExpiresAt);
    }
}
