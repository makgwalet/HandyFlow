package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A health-related event against either one {@link AgAnimal} or one
 * {@link AgGroup} — vaccination, treatment, illness/injury observation, or
 * routine deworming, distinguished by {@code eventType}.
 * <p>
 * REVISION FROM THE DELIVERED ARCHITECTURE PLAN: the plan document listed
 * a separate {@code AgVaccinationRecord} entity alongside a health-event
 * entity. Building it, these were merged into this single entity — a
 * vaccination is structurally identical to a treatment (a product
 * administered on a date, by someone, at a cost, with an optional next-due
 * date), and every field a vaccination needs is a field a treatment also
 * needs. {@code eventType = VACCINATION} plus the shared fields covers it
 * without a second table and a second notification-sweep query. Flagged
 * in the status report as a further simplification.
 * <p>
 * {@code nextDueDate} + {@code reminderAcknowledged} is this module's own
 * "PPM due" sweep target — see package-info.java's "NO {@code tasks}
 * REUSE" section for why this module generates its own actionable record
 * instead of depending on a cross-module Tasks integration that doesn't
 * exist. The daily notification sweep alerts once per due reminder and
 * sets {@code reminderAcknowledged}; it does NOT re-alert indefinitely —
 * a deliberate Increment 1 simplification, flagged in the status report.
 */
@Entity
@Table(name = "ag_health_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgHealthEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "animal_id")
    private UUID animalId;

    @Column(name = "group_id")
    private UUID groupId;

    /** VACCINATION | TREATMENT | OBSERVATION | ILLNESS | INJURY | DEWORMING */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "product_used")
    private String productUsed;

    private String dosage;

    @Column(name = "administered_by")
    private UUID administeredBy;

    @Column(name = "administered_by_name")
    private String administeredByName;

    private String veterinarian;

    @Column(precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "withdrawal_period_days")
    private Integer withdrawalPeriodDays;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "reminder_acknowledged", nullable = false)
    private boolean reminderAcknowledged = false;

    @Column(nullable = false)
    private String status = "COMPLETED"; // COMPLETED | SCHEDULED

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgHealthEvent create(TenantId tenantId, UUID animalId, UUID groupId, String eventType,
                                        LocalDate eventDate, String description, String productUsed, String dosage,
                                        UUID administeredBy, String administeredByName, String veterinarian,
                                        BigDecimal cost, Integer withdrawalPeriodDays, LocalDate nextDueDate,
                                        String status, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgTrackingTarget.requireExactlyOne(animalId, groupId);
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (eventDate == null) throw new IllegalArgumentException("eventDate is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");

        AgHealthEvent e = new AgHealthEvent();
        e.tenantId = tenantId;
        e.animalId = animalId;
        e.groupId = groupId;
        e.eventType = eventType;
        e.eventDate = eventDate;
        e.description = description;
        e.productUsed = productUsed;
        e.dosage = dosage;
        e.administeredBy = administeredBy;
        e.administeredByName = administeredByName;
        e.veterinarian = veterinarian;
        e.cost = cost;
        e.withdrawalPeriodDays = withdrawalPeriodDays;
        e.nextDueDate = nextDueDate;
        if (status != null && !status.isBlank()) e.status = status;
        e.notes = notes;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void update(String description, String productUsed, String dosage, String veterinarian,
                        BigDecimal cost, Integer withdrawalPeriodDays, LocalDate nextDueDate, String notes) {
        if (description != null && !description.isBlank()) this.description = description;
        this.productUsed = productUsed;
        this.dosage = dosage;
        this.veterinarian = veterinarian;
        this.cost = cost;
        this.withdrawalPeriodDays = withdrawalPeriodDays;
        this.nextDueDate = nextDueDate;
        this.reminderAcknowledged = false;
        this.notes = notes;
    }

    public void markCompleted() { this.status = "COMPLETED"; }

    public void acknowledgeReminder() { this.reminderAcknowledged = true; }

    public boolean isForAnimal() { return animalId != null; }

    public boolean isForGroup() { return groupId != null; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
