package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A question from a client contact. The practice answers in the same thread and closes it; the client cannot. */
@Entity(name = "CompliancePortalQuery")
@Table(name = "compliance_portal_queries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompliancePortalQuery {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(name = "raised_by", nullable = false)
    private UUID raisedBy;
    @Column(name = "raised_by_name")
    private String raisedByName;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;
    @Column(name = "message_count", nullable = false)
    private int messageCount;
    @Column(name = "closed_by")
    private UUID closedBy;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Version
    private Long version;

    /** Raised with its first message, which the caller saves with {@link CompliancePortalMessage#of}. */
    public static CompliancePortalQuery raise(TenantId tenantId, UUID clientId, String subject, UUID raisedBy, String raisedByName, int openForPerson) {
        ContactRules.checkCanRaise(openForPerson);
        CompliancePortalQuery q = new CompliancePortalQuery();
        q.tenantId = tenantId;
        q.clientId = clientId;
        q.subject = ContactRules.subject(subject);
        q.raisedBy = raisedBy;
        q.raisedByName = raisedByName;
        q.createdAt = Instant.now();
        q.lastMessageAt = q.createdAt;
        return q;
    }

    /** Records that a message was added and moves the status. */
    public void messageAdded(String authorType) {
        ContactRules.checkThreadLength(messageCount);
        this.status = ContactRules.statusAfter(status, authorType);
        this.messageCount++;
        this.lastMessageAt = Instant.now();
    }

    public void close(UUID by) {
        if ("CLOSED".equals(status)) throw new IllegalStateException("This query is already closed");
        if (by == null) throw new IllegalArgumentException("Someone must close it");
        this.status = "CLOSED";
        this.closedBy = by;
        this.closedAt = Instant.now();
    }
}
