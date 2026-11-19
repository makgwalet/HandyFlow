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

/**
 * A compliance finding. For now raised only by a partial or failed control test (one finding per test); later chunks add audit findings.
 * OPEN → IN_REMEDIATION → RESOLVED → CLOSED. Closing needs a verifier who did not resolve it and a passing retest. A verifier who is not
 * satisfied sends it back to IN_REMEDIATION; nothing is deleted and the counts of reopenings are kept.
 */
@Entity
@Table(name = "legalcompliance_findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Finding extends AggregateRoot<Finding> {

    public static final int MIN_NOTE = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Column(name = "source_type", nullable = false, length = 20) private String sourceType;
    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(name = "control_id") private UUID controlId;
    @Column(nullable = false, length = 255) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private FindingSeverity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private FindingStatus status = FindingStatus.OPEN;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "remediation_plan", columnDefinition = "TEXT") private String remediationPlan;
    @Column(name = "remediation_started_at") private Instant remediationStartedAt;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_by_name", length = 255) private String resolvedByName;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolution_note", columnDefinition = "TEXT") private String resolutionNote;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_by_name", length = 255) private String verifiedByName;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "verification_note", columnDefinition = "TEXT") private String verificationNote;
    @Column(name = "closing_test_id") private UUID closingTestId;
    @Column(name = "reopened_count", nullable = false) private int reopenedCount;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static Finding fromControlTest(TenantId tenantId, String reference, UUID testId, UUID controlId, String title, String description,
                                          FindingSeverity severity, UUID ownerId, String ownerName, LocalDate dueDate, UUID by, String byName) {
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("A finding needs an owner");
        Finding f = new Finding();
        f.initTenantId(tenantId);
        f.reference = reference;
        f.sourceType = "CONTROL_TEST";
        f.sourceId = testId;
        f.controlId = controlId;
        f.title = title;
        f.description = description;
        f.severity = severity;
        f.ownerId = ownerId;
        f.ownerName = ownerName;
        f.dueDate = dueDate;
        f.createdBy = by;
        f.createdByName = byName;
        return f;
    }

    public void startRemediation(String plan, UUID ownerId, String ownerName, LocalDate dueDate) {
        if (status != FindingStatus.OPEN && status != FindingStatus.IN_REMEDIATION) throw new IllegalStateException("Remediation can only be planned for an open finding — current: " + status);
        if (plan == null || plan.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A remediation plan of at least " + MIN_NOTE + " characters is required");
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (dueDate == null) throw new IllegalArgumentException("A due date is required");
        this.remediationPlan = plan.trim();
        this.ownerId = ownerId;
        this.ownerName = ownerName.trim();
        this.dueDate = dueDate;
        if (status == FindingStatus.OPEN) this.remediationStartedAt = Instant.now();
        this.status = FindingStatus.IN_REMEDIATION;
    }

    public void resolve(UUID by, String byName, String note) {
        if (status != FindingStatus.IN_REMEDIATION) throw new IllegalStateException("Only a finding in remediation can be marked resolved — current: " + status);
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Describe what was done (at least " + MIN_NOTE + " characters)");
        this.resolvedBy = by;
        this.resolvedByName = byName;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note.trim();
        this.status = FindingStatus.RESOLVED;
    }

    /** The verifier is not satisfied: back to remediation. */
    public void rejectResolution(UUID by, String byName, String note) {
        requireResolved();
        requireIndependent(by);
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Say what is still wrong (at least " + MIN_NOTE + " characters)");
        this.verifiedBy = by;
        this.verifiedByName = byName;
        this.verifiedAt = Instant.now();
        this.verificationNote = note.trim();
        this.reopenedCount++;
        this.status = FindingStatus.IN_REMEDIATION;
    }

    public void close(UUID by, String byName, String note, UUID closingTestId) {
        requireResolved();
        requireIndependent(by);
        if (closingTestId == null) throw new IllegalArgumentException("A passing retest is required to close a finding");
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A verification note of at least " + MIN_NOTE + " characters is required");
        this.verifiedBy = by;
        this.verifiedByName = byName;
        this.verifiedAt = Instant.now();
        this.verificationNote = note.trim();
        this.closingTestId = closingTestId;
        this.closedAt = Instant.now();
        this.status = FindingStatus.CLOSED;
    }

    public boolean isOpen() { return status != FindingStatus.CLOSED; }
    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate.isBefore(today); }

    private void requireResolved() { if (status != FindingStatus.RESOLVED) throw new IllegalStateException("The finding has not been marked resolved — current: " + status); }
    private void requireIndependent(UUID by) {
        if (by != null && by.equals(resolvedBy)) throw new IllegalStateException("Segregation of duties: the verifier must not be the person who resolved the finding");
    }
}
