// security/domain/model/AdvanceSurvey.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * AdvanceSurvey — a recon check at an itinerary stop, conducted before the
 * principal arrives.
 *
 * The CP equivalent of a patrol round (Phase 2, Part 6.6) — same "expected
 * vs actual, with a tolerance window" logic, just applied to a single recon
 * event per stop rather than a repeating patrol. The tolerance here is
 * implicit in the relationship to ItineraryStop.scheduledArrival: a survey
 * conducted too close to (or after) the scheduled arrival defeats the
 * purpose of advance recon, but this entity doesn't enforce that timing —
 * it's a query-time concern for whoever builds the compliance dashboard.
 *
 * WHY allow multiple surveys per stop (one per surveying guard) rather than
 * a single survey record?
 * High-threat-level details may want a second guard's independent
 * confirmation before clearing a stop — the unique constraint is on
 * (stop, guard) pairs, not on the stop alone, so a second guard's survey
 * doesn't overwrite the first guard's findings.
 */
@Entity
@Table(name = "security_advance_surveys")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AdvanceSurvey {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "itinerary_stop_id", nullable = false)
    private UUID itineraryStopId;

    @Column(name = "surveyed_by_guard_id", nullable = false)
    private UUID surveyedByGuardId;

    @Column(name = "surveyed_at", nullable = false, updatable = false)
    private Instant surveyedAt;

    @Column(name = "entry_exit_routes_notes")
    private String entryExitRoutesNotes;

    @Column(name = "hazards_noted")
    private String hazardsNoted;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "photo_urls", columnDefinition = "jsonb")
    private String photoUrls;   // JSON array of photo URLs

    @Column(name = "all_clear", nullable = false)
    private boolean allClear = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static AdvanceSurvey conduct(TenantId tenantId, UUID itineraryStopId,
                                        UUID surveyedByGuardId, String entryExitRoutesNotes,
                                        String hazardsNoted, String photoUrls,
                                        boolean allClear) {
        AdvanceSurvey s              = new AdvanceSurvey();
        s.tenantId                   = tenantId;
        s.itineraryStopId            = itineraryStopId;
        s.surveyedByGuardId          = surveyedByGuardId;
        s.entryExitRoutesNotes       = entryExitRoutesNotes;
        s.hazardsNoted               = hazardsNoted;
        s.photoUrls                  = photoUrls;
        s.allClear                   = allClear;
        s.surveyedAt                 = Instant.now();
        s.createdAt                  = Instant.now();
        return s;
    }
}
