package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only history of the project's approved baseline. Version 1 is the baseline as first
 * locked; each approved change order adds the next version with exactly what moved (dates,
 * budget lines, tasks — old and new values), so the original baseline and every approved
 * revision remain recoverable and nothing is silently overwritten.
 */
@Entity
@Table(name = "project_baseline_revisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectBaselineRevision {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)   private UUID tenantId;
    @Column(name = "project_id", nullable = false)  private UUID projectId;
    @Column(name = "version", nullable = false)     private int version;
    @Column(name = "change_order_id")                private UUID changeOrderId;
    @Column(name = "summary", nullable = false)     private String summary;
    @Column(name = "detail_json", nullable = false) private String detailJson;
    @Column(name = "applied_by")                     private UUID appliedBy;
    @Column(name = "applied_by_name")                private String appliedByName;
    @Column(name = "applied_at", nullable = false)  private Instant appliedAt;

    public static ProjectBaselineRevision record(UUID tenantId, UUID projectId, int version, UUID changeOrderId,
                                                 String summary, String detailJson, UUID by, String byName) {
        ProjectBaselineRevision r = new ProjectBaselineRevision();
        r.id            = UUID.randomUUID();
        r.tenantId      = tenantId;
        r.projectId     = projectId;
        r.version       = version;
        r.changeOrderId = changeOrderId;
        r.summary       = summary;
        r.detailJson    = detailJson;
        r.appliedBy     = by;
        r.appliedByName = byName;
        r.appliedAt     = Instant.now();
        return r;
    }
}