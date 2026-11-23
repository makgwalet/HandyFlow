package za.co.handyflow.platform.agriculture.domain.model;

import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A record that keeps who added and last changed it. */
public interface AgAudited {
    UUID getId();
    TenantId getTenantId();
    AgAuditStamp getAudit();
    void setAudit(AgAuditStamp audit);
    Instant getCreatedAt();
    /** Null for records that are only ever added, never edited. */
    default Instant getUpdatedAt() { return null; }
}
