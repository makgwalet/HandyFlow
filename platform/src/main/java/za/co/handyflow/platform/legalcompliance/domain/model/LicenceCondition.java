package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A condition attached to a licence ("submit an annual return", "keep the premises certificate displayed"). PENDING → MET, or PENDING → BREACHED.
 * A recurring condition (frequency in months, needs a first due date) goes back to PENDING with the next due date when it is met. A breached condition
 * can be marked met again only with a note saying how it was put right. Evidence for a condition is not held here; a breach is for people to act on,
 * for example by raising an incident or a corrective action, which this record does not do automatically.
 */
@Entity
@Table(name = "legalcompliance_licence_conditions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LicenceCondition extends AggregateRoot<LicenceCondition> {

    public static final int MIN_TEXT = 15;

    @Column(name = "licence_id", nullable = false) private UUID licenceId;
    @Column(nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "frequency_months") private Integer frequencyMonths;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private ConditionStatus status = ConditionStatus.PENDING;
    @Column(name = "last_met_on") private LocalDate lastMetOn;
    @Column(columnDefinition = "TEXT") private String note;

    public static LicenceCondition add(TenantId tenantId, UUID licenceId, String description, LocalDate due, Integer frequencyMonths) {
        LicenceCondition c = new LicenceCondition();
        c.initTenantId(tenantId);
        c.licenceId = licenceId;
        c.apply(description, due, frequencyMonths);
        return c;
    }

    public void update(String description, LocalDate due, Integer frequencyMonths) {
        apply(description, due, frequencyMonths);
    }

    private void apply(String description, LocalDate due, Integer freq) {
        if (description == null || description.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the condition (at least " + MIN_TEXT + " characters)");
        if (freq != null && (freq < 1 || freq > 120)) throw new IllegalArgumentException("The frequency must be between 1 and 120 months");
        if (freq != null && due == null) throw new IllegalArgumentException("A recurring condition needs a first due date");
        this.description = description.trim();
        this.dueDate = due;
        this.frequencyMonths = freq;
    }

    /** nextDue is computed by the caller for recurring conditions (LicenceRules.nextConditionDue). */
    public void markMet(LocalDate on, String note, LocalDate today, LocalDate nextDue) {
        if (status == ConditionStatus.MET) throw new IllegalStateException("This condition is already met");
        if (on == null) throw new IllegalArgumentException("The date it was met is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("A condition cannot be met in the future");
        if (status == ConditionStatus.BREACHED && (note == null || note.trim().length() < MIN_TEXT))
            throw new IllegalArgumentException("Say how the breach was put right (at least " + MIN_TEXT + " characters)");
        this.lastMetOn = on;
        this.note = note == null || note.isBlank() ? null : note.trim();
        if (frequencyMonths != null) {
            if (nextDue == null) throw new IllegalArgumentException("A recurring condition needs its next due date");
            this.dueDate = nextDue;
            this.status = ConditionStatus.PENDING;
        } else {
            this.status = ConditionStatus.MET;
        }
    }

    public void markBreached(String note) {
        if (status != ConditionStatus.PENDING) throw new IllegalStateException("Only a pending condition can be marked breached — current: " + status);
        if (note == null || note.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the breach (at least " + MIN_TEXT + " characters)");
        this.note = note.trim();
        this.status = ConditionStatus.BREACHED;
    }
}
