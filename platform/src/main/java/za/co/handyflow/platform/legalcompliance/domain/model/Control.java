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
 * An internal control. Activating a control, or assessing its design, never makes it "effective": operating effectiveness is set
 * only by recordTestOutcome, which the control-test service calls when a reviewer (not the tester) approves a test.
 * A material change to a control's definition resets both its design assessment and its tested effectiveness — the earlier test
 * was of a control that no longer exists in that form.
 */
@Entity
@Table(name = "legalcompliance_controls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Control extends AggregateRoot<Control> {

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String objective;
    @Column(name = "procedure_text", columnDefinition = "TEXT") private String procedureText;
    @Enumerated(EnumType.STRING) @Column(name = "control_type", nullable = false, length = 12) private ControlType controlType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ControlNature nature;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private ControlFrequency frequency;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "expected_evidence", columnDefinition = "TEXT") private String expectedEvidence;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ControlStatus status = ControlStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(name = "design_rating", nullable = false, length = 20) private DesignRating designRating = DesignRating.NOT_ASSESSED;
    @Column(name = "design_assessed_by") private UUID designAssessedBy;
    @Column(name = "design_assessed_by_name", length = 255) private String designAssessedByName;
    @Column(name = "design_assessed_at") private Instant designAssessedAt;
    @Column(name = "design_note", columnDefinition = "TEXT") private String designNote;
    @Enumerated(EnumType.STRING) @Column(name = "operating_effectiveness", nullable = false, length = 20) private OperatingEffectiveness operatingEffectiveness = OperatingEffectiveness.NOT_TESTED;
    @Column(name = "last_tested_at") private Instant lastTestedAt;
    @Column(name = "next_test_due") private LocalDate nextTestDue;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "retired_at") private Instant retiredAt;
    @Column(name = "retire_reason", columnDefinition = "TEXT") private String retireReason;
    @Column(name = "created_by") private UUID createdBy;

    public static Control create(TenantId tenantId, String reference, String title, String objective, String procedure, ControlType type,
                                 ControlNature nature, ControlFrequency frequency, UUID ownerId, String ownerName, String expectedEvidence, UUID by) {
        require(title, "A title is required");
        require(ownerName, "A control owner is required");
        Control c = new Control();
        c.initTenantId(tenantId);
        c.reference = reference;
        c.title = title.trim();
        c.objective = objective;
        c.procedureText = procedure;
        c.controlType = type;
        c.nature = nature;
        c.frequency = frequency;
        c.ownerId = ownerId;
        c.ownerName = ownerName.trim();
        c.expectedEvidence = expectedEvidence;
        c.createdBy = by;
        return c;
    }

    /** Returns true when the change was material and the design assessment was reset. */
    public boolean update(String title, String objective, String procedure, ControlType type, ControlNature nature, ControlFrequency frequency,
                          UUID ownerId, String ownerName, String expectedEvidence) {
        if (status == ControlStatus.RETIRED) throw new IllegalStateException("A retired control cannot be edited");
        require(title, "A title is required");
        require(ownerName, "A control owner is required");
        boolean material = !java.util.Objects.equals(norm(this.objective), norm(objective)) || !java.util.Objects.equals(norm(this.procedureText), norm(procedure))
                || this.controlType != type || this.nature != nature || this.frequency != frequency;
        this.title = title.trim();
        this.objective = objective;
        this.procedureText = procedure;
        this.controlType = type;
        this.nature = nature;
        this.frequency = frequency;
        this.ownerId = ownerId;
        this.ownerName = ownerName.trim();
        this.expectedEvidence = expectedEvidence;
        boolean reset = false;
        if (material && designRating != DesignRating.NOT_ASSESSED) {
            this.designRating = DesignRating.NOT_ASSESSED;
            this.designAssessedBy = null; this.designAssessedByName = null; this.designAssessedAt = null; this.designNote = null;
            reset = true;
        }
        if (material && operatingEffectiveness != OperatingEffectiveness.NOT_TESTED) {
            this.operatingEffectiveness = OperatingEffectiveness.NOT_TESTED;
            this.nextTestDue = null;
            reset = true;
        }
        return reset;
    }

    /** The service checks that at least one risk or obligation is linked before calling this. */
    public void activate() {
        if (status != ControlStatus.DRAFT) throw new IllegalStateException("Only a draft control can be activated — current: " + status);
        require(objective, "A control objective is required before activation");
        require(procedureText, "A control procedure is required before activation");
        require(expectedEvidence, "The expected evidence must be stated before activation");
        this.status = ControlStatus.ACTIVE;
        this.activatedAt = Instant.now();
    }

    public void assessDesign(DesignRating rating, String note, UUID by, String byName) {
        if (status == ControlStatus.RETIRED) throw new IllegalStateException("A retired control cannot be assessed");
        if (rating == null || rating == DesignRating.NOT_ASSESSED) throw new IllegalArgumentException("Choose ADEQUATE, NEEDS_IMPROVEMENT or INADEQUATE");
        if (rating != DesignRating.ADEQUATE && (note == null || note.trim().length() < 15))
            throw new IllegalArgumentException("Explain what is wrong with the design (at least 15 characters)");
        this.designRating = rating;
        this.designNote = note;
        this.designAssessedBy = by;
        this.designAssessedByName = byName;
        this.designAssessedAt = Instant.now();
    }

    /** Called only when a control test is approved. */
    public void recordTestOutcome(OperatingEffectiveness effectiveness, Instant testedAt, LocalDate nextDue) {
        if (status != ControlStatus.ACTIVE) throw new IllegalStateException("Only an active control can record a test outcome");
        if (effectiveness == null || effectiveness == OperatingEffectiveness.NOT_TESTED) throw new IllegalArgumentException("A test outcome must be a tested result");
        this.operatingEffectiveness = effectiveness;
        this.lastTestedAt = testedAt;
        this.nextTestDue = nextDue;
    }

    public void retire(String reason) {
        if (status == ControlStatus.RETIRED) throw new IllegalStateException("The control is already retired");
        if (reason == null || reason.trim().length() < 15) throw new IllegalArgumentException("A reason of at least 15 characters is required");
        this.status = ControlStatus.RETIRED;
        this.retiredAt = Instant.now();
        this.retireReason = reason.trim();
    }

    public boolean isActive() { return status == ControlStatus.ACTIVE; }

    private static String norm(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }
    private static void require(String s, String msg) { if (s == null || s.isBlank()) throw new IllegalArgumentException(msg); }
}
