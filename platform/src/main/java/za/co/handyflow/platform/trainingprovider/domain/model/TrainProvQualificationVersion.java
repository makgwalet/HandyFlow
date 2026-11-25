package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CatalogueRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One dated snapshot of a qualification's registration details. Editable while a draft; once current it is never changed, only superseded. */
@Entity(name = "TrainProvQualificationVersion")
@Table(name = "trainprov_qualification_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvQualificationVersion {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "qualification_id", nullable = false)
    private UUID qualificationId;
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;
    @Column(nullable = false)
    private String status = "DRAFT";
    @Column(name = "registration_status", nullable = false)
    private String registrationStatus = "UNKNOWN";
    @Column(name = "registration_start")
    private LocalDate registrationStart;
    @Column(name = "registration_end")
    private LocalDate registrationEnd;
    @Column(name = "entry_requirements")
    private String entryRequirements;
    @Column(name = "exit_outcomes")
    private String exitOutcomes;
    @Column(name = "rules_of_combination")
    private String rulesOfCombination;
    @Column(name = "source_note")
    private String sourceNote;
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "made_current_by")
    private UUID madeCurrentBy;
    @Column(name = "made_current_at")
    private Instant madeCurrentAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    public static TrainProvQualificationVersion draft(TenantId t, UUID qualificationId, int versionNo, UUID by) {
        TrainProvQualificationVersion v = new TrainProvQualificationVersion();
        v.tenantId = t.getValue();
        v.qualificationId = qualificationId;
        v.versionNo = versionNo;
        v.createdBy = by;
        v.createdAt = Instant.now();
        v.updatedAt = v.createdAt;
        return v;
    }

    public void edit(String registrationStatus, LocalDate start, LocalDate end, String entry, String exit,
                     String rules, String sourceNote, LocalDate effectiveFrom) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("A published version cannot be edited. Start a new version instead");
        }
        if (registrationStatus == null || !CatalogueRules.REGISTRATION_STATUSES.contains(registrationStatus)) {
            throw new IllegalArgumentException("Registration status is not recognised");
        }
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("The registration end date cannot be before the start date");
        }
        this.registrationStatus = registrationStatus;
        this.registrationStart = start;
        this.registrationEnd = end;
        this.entryRequirements = blank(entry);
        this.exitOutcomes = blank(exit);
        this.rulesOfCombination = blank(rules);
        this.sourceNote = blank(sourceNote);
        this.effectiveFrom = effectiveFrom;
        this.updatedAt = Instant.now();
    }

    public void makeCurrent(UUID by) {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a draft version can be made current");
        }
        this.status = "CURRENT";
        this.madeCurrentBy = by;
        this.madeCurrentAt = Instant.now();
        this.updatedAt = this.madeCurrentAt;
    }

    public void supersede() {
        this.status = "SUPERSEDED";
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
