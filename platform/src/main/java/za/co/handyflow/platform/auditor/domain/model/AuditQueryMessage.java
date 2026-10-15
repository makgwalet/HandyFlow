package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One line in the thread of a query: the question, an answer, a follow-up, or the closing note. */
@Entity(name = "AuditQueryMessage")
@Table(name = "audit_query_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditQueryMessage {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "query_id", nullable = false) private UUID queryId;
    @Column(name = "author_kind", nullable = false) private String authorKind;
    @Column(name = "author_id", nullable = false) private UUID authorId;
    @Column(name = "author_label") private String authorLabel;
    @Column(name = "message_kind", nullable = false) private String messageKind;
    @Column(name = "body", nullable = false, columnDefinition = "TEXT") private String body;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    static AuditQueryMessage of(UUID tenantId, UUID queryId, String authorKind, UUID authorId, String label, String messageKind, String body) {
        AuditQueryMessage m = new AuditQueryMessage();
        m.tenantId = tenantId; m.queryId = queryId; m.authorKind = authorKind; m.authorId = authorId; m.authorLabel = label;
        m.messageKind = messageKind; m.body = body;
        return m;
    }
}
