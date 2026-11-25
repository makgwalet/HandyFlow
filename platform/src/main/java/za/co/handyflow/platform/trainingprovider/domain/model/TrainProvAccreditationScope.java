package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One programme at one delivery site that an accreditation covers. Withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvAccreditationScope")
@Table(name = "trainprov_accreditation_scopes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAccreditationScope {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "accreditation_id", nullable = false)
    private UUID accreditationId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "course_id")
    private UUID courseId;
    @Column(name = "programme_title", nullable = false)
    private String programmeTitle;
    @Column(name = "qualification_ref")
    private String qualificationRef;
    @Column(name = "nqf_level")
    private Integer nqfLevel;
    private Integer credits;
    @Column(name = "valid_from")
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    private String notes;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAccreditationScope create(TenantId tenantId, UUID accreditationId, UUID siteId, UUID courseId,
                                                     String programmeTitle, String qualificationRef, Integer nqfLevel,
                                                     Integer credits, LocalDate validFrom, LocalDate validTo, String notes, UUID by) {
        if (programmeTitle == null || programmeTitle.isBlank()) {
            throw new IllegalArgumentException("Name the programme or qualification");
        }
        if (nqfLevel != null && (nqfLevel < 1 || nqfLevel > 10)) {
            throw new IllegalArgumentException("NQF level must be between 1 and 10");
        }
        if (credits != null && credits < 0) {
            throw new IllegalArgumentException("Credits cannot be negative");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("The end date cannot be before the start date");
        }
        TrainProvAccreditationScope s = new TrainProvAccreditationScope();
        s.tenantId = tenantId.getValue();
        s.accreditationId = accreditationId;
        s.siteId = siteId;
        s.courseId = courseId;
        s.programmeTitle = programmeTitle.trim();
        s.qualificationRef = qualificationRef == null || qualificationRef.isBlank() ? null : qualificationRef.trim();
        s.nqfLevel = nqfLevel;
        s.credits = credits;
        s.validFrom = validFrom;
        s.validTo = validTo;
        s.notes = notes == null || notes.isBlank() ? null : notes.trim();
        s.createdBy = by;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void withdraw(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This scope is already withdrawn");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Say why, in at least 10 characters");
        }
        this.status = "WITHDRAWN";
        this.withdrawnReason = reason.trim();
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
        this.updatedAt = this.withdrawnAt;
    }
}
