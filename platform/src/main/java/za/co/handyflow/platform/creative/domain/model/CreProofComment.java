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

    // NEW: pin position for point annotation on images — stored as
    // fractions (0.0-1.0) of the image's width/height, not pixel
    // coordinates, so a pin placed at "roughly the top-right corner" stays
    // correct regardless of what size the image is actually rendered at
    // (thumbnail, full preview, different screen). Both null or both set —
    // never just one.
    @Column(name = "anchor_x") private Double anchorX;
    @Column(name = "anchor_y") private Double anchorY;

    // Same rationale as timecodeSeconds — see its own comment above.
    @Column(name = "timecode_seconds") private Double timecodeSeconds;

    @Column(name = "created_at") private Instant createdAt;

    public static CreProofComment create(UUID proofId, UUID tenantId,
                                         String authorName, String authorType,
                                         String comment, Double timecodeSeconds,
                                         Double anchorX, Double anchorY) {
        CreProofComment c = new CreProofComment();
        c.proofId    = proofId;
        c.tenantId   = tenantId;
        c.authorName = authorName;
        c.authorType = authorType;
        c.comment    = comment;
        c.timecodeSeconds = timecodeSeconds;
        c.anchorX    = anchorX;
        c.anchorY    = anchorY;
        c.createdAt  = Instant.now();
        return c;
    }
}
