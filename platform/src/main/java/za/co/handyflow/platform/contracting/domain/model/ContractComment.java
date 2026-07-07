package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment or amendment request posted by any party (internal or external).
 * Amendment requests are flagged in the HandyFlow admin UI for the owner's attention.
 *
 * File: contracting/domain/model/ContractComment.java
 */
@Getter
@Setter
@Entity
@Table(name = "contract_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "party_id")
    private UUID partyId;   // null = posted by internal HandyFlow user
    
    @Column(name = "posted_by_user_id")
    private UUID postedByUserId;

    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Column(name = "clause_ref")
    private String clauseRef;    // optional: e.g. "Clause 3.2"

    @Column(name = "is_amendment_request", nullable = false)
    private boolean amendmentRequest;

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Populated at query time from ContractParty — avoids an extra join in every list call
    @Transient
    private String authorName;

    @Transient
    private String authorRole;

    public static ContractComment create(UUID tenantId, UUID contractId,
                                         UUID partyId, String comment,
                                         String clauseRef, boolean isAmendment) {
        ContractComment c = new ContractComment();
        c.tenantId         = tenantId;
        c.contractId       = contractId;
        c.partyId          = partyId;
        c.comment          = comment;
        c.clauseRef        = clauseRef;
        c.amendmentRequest = isAmendment;
        c.createdAt        = Instant.now();
        c.updatedAt        = Instant.now();
        return c;
    }
}
