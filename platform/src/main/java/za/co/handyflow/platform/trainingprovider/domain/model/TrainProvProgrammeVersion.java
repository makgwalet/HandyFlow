package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A dated definition of a programme. Editable while a draft; once approved it is frozen so a learner keeps the version they studied under. */
@Entity(name = "TrainProvProgrammeVersion")
@Table(name = "trainprov_programme_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProgrammeVersion {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;
    @Column(nullable = false)
    private String status = "DRAFT";
    @Column(name = "qualification_version_id")
    private UUID qualificationVersionId;
    @Column(name = "learning_hours")
    private BigDecimal learningHours;
    private Integer credits;
    private String summary;
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "approval_override_reason")
    private String approvalOverrideReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    public static TrainProvProgrammeVersion draft(TenantId t, UUID programmeId, int versionNo, UUID by) {
        TrainProvProgrammeVersion v = new TrainProvProgrammeVersion();
        v.tenantId = t.getValue();
        v.programmeId = programmeId;
        v.versionNo = versionNo;
        v.createdBy = by;
        v.createdAt = Instant.now();
        v.updatedAt = v.createdAt;
        return v;
    }

    public void edit(UUID qualificationVersionId, BigDecimal learningHours, Integer credits, String summary, LocalDate effectiveFrom) {
        requireDraft();
        if (learningHours != null && learningHours.signum() < 0) {
            throw new IllegalArgumentException("Learning hours cannot be negative");
        }
        if (credits != null && credits < 0) {
            throw new IllegalArgumentException("Credits cannot be negative");
        }
        this.qualificationVersionId = qualificationVersionId;
        this.learningHours = learningHours;
        this.credits = credits;
        this.summary = summary == null || summary.isBlank() ? null : summary.trim();
        this.effectiveFrom = effectiveFrom;
        this.updatedAt = Instant.now();
    }

    public void requireDraft() {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("An approved version is frozen. Start a new version to change it");
        }
    }

    public void approve(UUID by, String overrideReason) {
        requireDraft();
        this.status = "APPROVED";
        this.approvedBy = by;
        this.approvedAt = Instant.now();
        this.approvalOverrideReason = by.equals(createdBy) && overrideReason != null ? overrideReason.trim() : null;
        this.updatedAt = this.approvedAt;
    }

    public void supersede() {
        this.status = "SUPERSEDED";
        this.updatedAt = Instant.now();
    }
}
