package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "EvidenceRequestComment")
@Table(name = "audit_evidence_request_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceRequestComment {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "author_kind", nullable = false) private String authorKind;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    @Column(name = "author_label") private String authorLabel;
    @Column(name = "body", nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public static EvidenceRequestComment of(UUID tenantId, UUID requestId, String authorKind, UUID authorId, String label, String body) {
        if (body == null || body.trim().isEmpty()) throw new IllegalArgumentException("A comment cannot be empty");
        EvidenceRequestComment c = new EvidenceRequestComment();
        c.tenantId = tenantId; c.requestId = requestId; c.authorKind = authorKind; c.authorId = authorId; c.authorLabel = label; c.body = body.trim();
        return c;
    }
}
