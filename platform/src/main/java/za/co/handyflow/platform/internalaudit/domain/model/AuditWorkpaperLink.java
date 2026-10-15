package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "AuditWorkpaperLink")
@Table(name = "audit_workpaper_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditWorkpaperLink {
    public static final java.util.Set<String> TYPES = java.util.Set.of("TEST", "EXCEPTION", "FINDING");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "workpaper_id", nullable = false) private UUID workpaperId;
    @Column(name = "link_type", nullable = false) private String linkType;
    @Column(name = "link_id", nullable = false) private UUID linkId;
    @Column(name = "linked_by", nullable = false) private UUID linkedBy;
    @Column(name = "linked_at", nullable = false) private Instant linkedAt = Instant.now();

    public static AuditWorkpaperLink of(UUID tenantId, UUID workpaperId, String type, UUID linkId, UUID by) {
        if (!TYPES.contains(type)) throw new IllegalArgumentException("A workpaper can be linked to a test, an exception or a finding");
        AuditWorkpaperLink l = new AuditWorkpaperLink();
        l.tenantId = tenantId; l.workpaperId = workpaperId; l.linkType = type; l.linkId = linkId; l.linkedBy = by;
        return l;
    }
}
