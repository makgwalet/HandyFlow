package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.LmsLinkRules;

import java.time.Instant;
import java.util.UUID;

/** A launch link to an outside learning system for one programme version. No secret is held. Ended with a reason, never deleted. */
@Entity(name = "TrainProvLmsLink")
@Table(name = "trainprov_lms_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLmsLink {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private UUID programmeVersionId;
    @Column(name = "system_name", nullable = false)
    private String systemName;
    @Column(name = "launch_url", nullable = false)
    private String launchUrl;
    private String notes;
    @Column(nullable = false)
    private String status = LmsLinkRules.ACTIVE;
    @Column(name = "ended_reason")
    private String endedReason;
    @Column(name = "ended_by")
    private UUID endedBy;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLmsLink create(TenantId t, UUID versionId, String systemName, String url, String notes, UUID by) {
        TrainProvLmsLink l = new TrainProvLmsLink();
        l.tenantId = t.getValue();
        l.programmeVersionId = versionId;
        l.createdBy = by;
        l.createdAt = Instant.now();
        l.apply(systemName, url, notes);
        return l;
    }

    public void change(String systemName, String url, String notes) {
        String problem = LmsLinkRules.editProblem(status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        apply(systemName, url, notes);
    }

    private void apply(String systemName, String url, String notes) {
        String problem = LmsLinkRules.linkProblem(systemName, url);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.systemName = systemName.trim();
        this.launchUrl = url.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
    }

    public void end(String reason, UUID by) {
        String problem = LmsLinkRules.endProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = LmsLinkRules.ENDED;
        this.endedReason = reason.trim();
        this.endedBy = by;
        this.endedAt = Instant.now();
        this.updatedAt = this.endedAt;
    }
}
