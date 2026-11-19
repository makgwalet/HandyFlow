package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A request that a group of users read and accept one published policy version. The version's content hash is copied so the proof survives later versions. */
@Entity
@Table(name = "legalcompliance_attestation_campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttestationCampaign extends AggregateRoot<AttestationCampaign> {

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "policy_id", nullable = false) private UUID policyId;
    @Column(name = "policy_version_id", nullable = false) private UUID policyVersionId;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String instructions;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "department_filter", length = 100) private String departmentFilter;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private CampaignStatus status = CampaignStatus.OPEN;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Column(name = "launched_by") private UUID launchedBy;
    @Column(name = "launched_by_name", length = 255) private String launchedByName;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "closed_by_name", length = 255) private String closedByName;
    @Column(name = "close_reason", columnDefinition = "TEXT") private String closeReason;

    public static AttestationCampaign launch(TenantId tenantId, String reference, PolicyVersion version, String title, String instructions, LocalDate due,
                                             String departmentFilter, LocalDate today, UUID by, String byName) {
        if (version.getStatus() != PolicyVersionStatus.PUBLISHED) throw new IllegalStateException("Only a published version can be attested to — current: " + version.getStatus());
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (due == null || due.isBefore(today)) throw new IllegalArgumentException("The due date cannot be in the past");
        AttestationCampaign c = new AttestationCampaign();
        c.initTenantId(tenantId);
        c.reference = reference;
        c.policyId = version.getPolicyId();
        c.policyVersionId = version.getId();
        c.title = title.trim();
        c.instructions = instructions == null || instructions.isBlank() ? null : instructions.trim();
        c.dueDate = due;
        c.departmentFilter = departmentFilter == null || departmentFilter.isBlank() ? null : departmentFilter.trim();
        c.contentHash = version.getContentHash();
        c.launchedBy = by;
        c.launchedByName = byName;
        return c;
    }

    public void close(String byName, String reason) {
        if (status == CampaignStatus.CLOSED) throw new IllegalStateException("The campaign is already closed");
        if (reason == null || reason.trim().length() < 15) throw new IllegalArgumentException("A reason of at least 15 characters is required");
        this.status = CampaignStatus.CLOSED;
        this.closedAt = Instant.now();
        this.closedByName = byName;
        this.closeReason = reason.trim();
    }

    public boolean isOpen() { return status == CampaignStatus.OPEN; }
    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate.isBefore(today); }
}
