package za.co.handyflow.platform.creative.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// ── CreProofComment ────────────────────────────────────────────────────────────
@Entity
@Table(name = "cre_proof_comments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CreProofComment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "proof_id",   nullable = false) private UUID   proofId;
    @Column(name = "tenant_id",  nullable = false) private UUID   tenantId;
    @Column(name = "author_name",nullable = false) private String authorName;
    @Column(name = "author_type",nullable = false) private String authorType; // TEAM | CLIENT
    @Column(nullable = false)                      private String comment;
    @Column(name = "created_at") private Instant createdAt;

    public static CreProofComment create(UUID proofId, UUID tenantId,
                                          String authorName, String authorType,
                                          String comment) {
        CreProofComment c = new CreProofComment();
        c.proofId    = proofId;
        c.tenantId   = tenantId;
        c.authorName = authorName;
        c.authorType = authorType;
        c.comment    = comment;
        c.createdAt  = Instant.now();
        return c;
    }
}
