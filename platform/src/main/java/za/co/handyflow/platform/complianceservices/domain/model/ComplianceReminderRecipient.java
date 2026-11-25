package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Someone the digest goes to. {@code adminItems} says whether it includes the items only administrators act on, such as approvals. */
@Entity(name = "ComplianceReminderRecipient")
@Table(name = "compliance_reminder_recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReminderRecipient {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "admin_items", nullable = false)
    private boolean adminItems;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public void setAdminItems(boolean adminItems) { this.adminItems = adminItems; }

    public static ComplianceReminderRecipient of(TenantId tenantId, UUID userId, boolean adminItems, UUID by) {
        if (userId == null) throw new IllegalArgumentException("Choose a person");
        ComplianceReminderRecipient r = new ComplianceReminderRecipient();
        r.tenantId = tenantId;
        r.userId = userId;
        r.adminItems = adminItems;
        r.createdBy = by;
        r.createdAt = Instant.now();
        return r;
    }
}
