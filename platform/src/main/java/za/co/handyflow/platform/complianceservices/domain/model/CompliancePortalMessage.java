package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One message in a query thread. Never edited or deleted. A staff message keeps no name here: the client sees "the practice", not a person. */
@Entity(name = "CompliancePortalMessage")
@Table(name = "compliance_portal_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompliancePortalMessage {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "query_id", nullable = false)
    private UUID queryId;
    @Column(name = "author_type", nullable = false)
    private String authorType;
    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Column(name = "author_name")
    private String authorName;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static CompliancePortalMessage of(TenantId tenantId, UUID queryId, String authorType, UUID authorId, String authorName, String body) {
        if (!"CLIENT".equals(authorType) && !"STAFF".equals(authorType)) throw new IllegalArgumentException("A message is from the client or from the practice");
        if (authorId == null) throw new IllegalArgumentException("A message needs an author");
        CompliancePortalMessage m = new CompliancePortalMessage();
        m.tenantId = tenantId;
        m.queryId = queryId;
        m.authorType = authorType;
        m.authorId = authorId;
        m.authorName = "CLIENT".equals(authorType) ? authorName : null;
        m.body = ContactRules.body(body);
        m.createdAt = Instant.now();
        return m;
    }
}
