package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A record of something the organisation owes a regulator (Information Officer registration, PAIA manual review, annual report, self-assessment).
 * The platform does not file anything: it tracks the preparation and keeps proof. PLANNED → PREPARED → SUBMITTED → ACKNOWLEDGED. Marking it
 * SUBMITTED needs the date it was submitted, who submitted it and at least one proof file attached (the service checks and snapshots the files).
 * Marking it ACKNOWLEDGED needs the regulator's reference.
 */
@Entity
@Table(name = "legalcompliance_filings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Filing extends AggregateRoot<Filing> {

    @Column(nullable = false, length = 20) private String reference;
    @Enumerated(EnumType.STRING) @Column(name = "filing_type", nullable = false, length = 40) private FilingType filingType;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "period_label", length = 50) private String periodLabel;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private FilingStatus status = FilingStatus.PLANNED;
    @Column(name = "prepared_at") private Instant preparedAt;
    @Column(name = "submitted_on") private LocalDate submittedOn;
    @Column(name = "submitted_by_name", length = 255) private String submittedByName;
    @Column(name = "regulator_reference", length = 100) private String regulatorReference;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "proof_snapshot", columnDefinition = "jsonb") private java.util.List<java.util.Map<String, Object>> proofSnapshot;
    @Column(columnDefinition = "TEXT") private String notes;

    public static Filing plan(TenantId tenantId, String reference, FilingType type, String title, String period, LocalDate due, String owner, String notes) {
        Filing f = new Filing();
        f.initTenantId(tenantId);
        f.reference = reference;
        f.apply(type, title, period, due, owner, notes);
        return f;
    }

    public void update(FilingType type, String title, String period, LocalDate due, String owner, String notes) {
        if (status == FilingStatus.SUBMITTED || status == FilingStatus.ACKNOWLEDGED) throw new IllegalStateException("A submitted filing cannot be edited — current: " + status);
        apply(type, title, period, due, owner, notes);
    }

    private void apply(FilingType type, String title, String period, LocalDate due, String owner, String notes) {
        if (type == null) throw new IllegalArgumentException("A filing type is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (due == null) throw new IllegalArgumentException("A due date is required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("An owner is required");
        this.filingType = type;
        this.title = title.trim();
        this.periodLabel = period == null || period.isBlank() ? null : period.trim();
        this.dueDate = due;
        this.ownerName = owner.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void markPrepared() {
        if (status != FilingStatus.PLANNED) throw new IllegalStateException("Only a planned filing can be marked prepared — current: " + status);
        this.status = FilingStatus.PREPARED;
        this.preparedAt = Instant.now();
    }

    public void markSubmitted(LocalDate on, String by, java.util.List<java.util.Map<String, Object>> proof, LocalDate today) {
        if (status != FilingStatus.PREPARED) throw new IllegalStateException("Only a prepared filing can be marked submitted — current: " + status);
        if (on == null) throw new IllegalArgumentException("The submission date is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("A submission date cannot be in the future");
        if (by == null || by.isBlank()) throw new IllegalArgumentException("Who submitted it is required");
        if (proof == null || proof.isEmpty()) throw new IllegalArgumentException("Attach proof of submission (for example the confirmation receipt) first");
        this.submittedOn = on;
        this.submittedByName = by.trim();
        this.proofSnapshot = proof;
        this.status = FilingStatus.SUBMITTED;
    }

    public void acknowledge(String regulatorReference) {
        if (status != FilingStatus.SUBMITTED) throw new IllegalStateException("Only a submitted filing can be acknowledged — current: " + status);
        if (regulatorReference == null || regulatorReference.isBlank()) throw new IllegalArgumentException("The regulator's reference is required");
        this.regulatorReference = regulatorReference.trim();
        this.status = FilingStatus.ACKNOWLEDGED;
    }
}
