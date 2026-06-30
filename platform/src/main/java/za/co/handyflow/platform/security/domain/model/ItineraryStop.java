// security/domain/model/ItineraryStop.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * ItineraryStop — one location/time on a protection detail's schedule.
 *
 * The CP equivalent of a checkpoint, but for mobile protection: instead of
 * a guard patrolling fixed checkpoints on a site, the team moves through a
 * sequence of locations on a schedule, each with expected vs. actual
 * arrival/departure (same "tolerance window" logic as patrol rounds, Part
 * 6.6, just keyed to a moving schedule of locations instead of fixed
 * checkpoints).
 *
 * advanceSurveyRequired is reserved for Phase 3.5's security_advance_surveys
 * table — whether a recon check is mandated before the principal arrives.
 * Present in the schema now, not yet enforced by any service code here.
 */
@Entity
@Table(name = "security_itinerary_stops")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ItineraryStop {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "detail_id", nullable = false)
    private UUID detailId;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "location_name", nullable = false, length = 200)
    private String locationName;

    @Column
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "scheduled_arrival")
    private Instant scheduledArrival;

    @Column(name = "scheduled_departure")
    private Instant scheduledDeparture;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Column(name = "actual_departure")
    private Instant actualDeparture;

    @Column(name = "advance_survey_required", nullable = false)
    private boolean advanceSurveyRequired = false;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static ItineraryStop create(TenantId tenantId, UUID detailId, int sequence,
                                       String locationName, String address,
                                       BigDecimal latitude, BigDecimal longitude,
                                       Instant scheduledArrival, Instant scheduledDeparture,
                                       boolean advanceSurveyRequired, String notes) {
        ItineraryStop s          = new ItineraryStop();
        s.tenantId               = tenantId;
        s.detailId               = detailId;
        s.sequence               = sequence;
        s.locationName           = locationName.strip();
        s.address                = address;
        s.latitude               = latitude;
        s.longitude              = longitude;
        s.scheduledArrival       = scheduledArrival;
        s.scheduledDeparture     = scheduledDeparture;
        s.advanceSurveyRequired  = advanceSurveyRequired;
        s.notes                  = notes;
        s.createdAt              = Instant.now();
        s.updatedAt              = Instant.now();
        return s;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void recordArrival() {
        if (this.actualArrival != null) {
            throw new IllegalStateException("Arrival already recorded for this stop");
        }
        this.actualArrival = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public void recordDeparture() {
        if (this.actualArrival == null) {
            throw new IllegalStateException("Cannot record departure before arrival");
        }
        if (this.actualDeparture != null) {
            throw new IllegalStateException("Departure already recorded for this stop");
        }
        this.actualDeparture = Instant.now();
        this.updatedAt       = Instant.now();
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isCompleted() { return actualDeparture != null; }
    public boolean isInProgress() { return actualArrival != null && actualDeparture == null; }
    public boolean isPending() { return actualArrival == null; }
}
