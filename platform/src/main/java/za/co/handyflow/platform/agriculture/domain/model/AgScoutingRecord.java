package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A pest/disease/weed/nutrient-deficiency observation against one
 * {@link AgCropCycle} — the Crops sub-domain's direct structural
 * counterpart to {@link AgHealthEvent}, including the same
 * {@code followUpDate}/{@code followUpAcknowledged} shape that backs the
 * daily notification sweep ("scouting follow-up due"), for the identical
 * reason {@code AgHealthEvent} carries {@code nextDueDate} — this module
 * generates its own actionable record rather than depending on a
 * cross-module Tasks integration that doesn't exist (see package-info.java's
 * "NO tasks REUSE" section, which applies here exactly as it did in
 * Increment 1).
 * <p>
 * Mutable ({@code updatedAt}/{@code version}) — an open scouting finding
 * transitions to resolved once the recommended action has been taken.
 */
@Entity
@Table(name = "ag_scouting_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgScoutingRecord {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "crop_cycle_id", nullable = false)
    private UUID cropCycleId;

    @Column(name = "scouting_date", nullable = false)
    private LocalDate scoutingDate;

    /** PEST | DISEASE | WEED | NUTRIENT_DEFICIENCY | WEATHER_DAMAGE | GENERAL */
    @Column(name = "observation_type", nullable = false)
    private String observationType;

    @Column(nullable = false)
    private String severity = "LOW"; // LOW | MEDIUM | HIGH

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "scouted_by")
    private UUID scoutedBy;

    @Column(name = "scouted_by_name")
    private String scoutedByName;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_acknowledged", nullable = false)
    private boolean followUpAcknowledged = false;

    @Column(nullable = false)
    private String status = "OPEN"; // OPEN | RESOLVED

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgScoutingRecord create(TenantId tenantId, UUID cropCycleId, LocalDate scoutingDate,
                                           String observationType, String severity, String description,
                                           String recommendedAction, UUID scoutedBy, String scoutedByName,
                                           LocalDate followUpDate, String notes) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (cropCycleId == null) throw new IllegalArgumentException("cropCycleId is required");
        if (scoutingDate == null) throw new IllegalArgumentException("scoutingDate is required");
        if (observationType == null || observationType.isBlank()) throw new IllegalArgumentException("observationType is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");

        AgScoutingRecord r = new AgScoutingRecord();
        r.tenantId = tenantId;
        r.cropCycleId = cropCycleId;
        r.scoutingDate = scoutingDate;
        r.observationType = observationType;
        if (severity != null && !severity.isBlank()) r.severity = severity;
        r.description = description;
        r.recommendedAction = recommendedAction;
        r.scoutedBy = scoutedBy;
        r.scoutedByName = scoutedByName;
        r.followUpDate = followUpDate;
        r.notes = notes;
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void update(String severity, String description, String recommendedAction, LocalDate followUpDate, String notes) {
        if (severity != null && !severity.isBlank()) this.severity = severity;
        if (description != null && !description.isBlank()) this.description = description;
        this.recommendedAction = recommendedAction;
        this.followUpDate = followUpDate;
        this.followUpAcknowledged = false;
        this.notes = notes;
    }

    public void resolve() { this.status = "RESOLVED"; }

    public void reopen() { this.status = "OPEN"; }

    public void acknowledgeFollowUp() { this.followUpAcknowledged = true; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
