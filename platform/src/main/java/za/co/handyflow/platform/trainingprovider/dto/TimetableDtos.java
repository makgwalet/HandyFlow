package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TimetableDtos {
    private TimetableDtos() {
    }

    /**
     * timeZone is optional (default Africa/Johannesburg). overrideReason is only for knowingly booking over a clash and is honoured only
     * for an administrator.
     */
    public record SlotRequest(@NotBlank String title, @NotNull LocalDate slotDate, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                              UUID moduleId, UUID venueId, UUID practitionerId, String timeZone, String overrideReason) {
        public SlotRequest(String title, LocalDate slotDate, LocalTime startTime, LocalTime endTime, UUID moduleId, UUID venueId, UUID practitionerId) {
            this(title, slotDate, startTime, endTime, moduleId, venueId, practitionerId, null, null);
        }
    }

    public record ClashView(String resourceType, UUID resourceId, String resourceLabel, UUID otherSlotId, UUID otherCohortId, String message) {
    }

    /** What a check found: clashes (empty means the slot can be booked as it is). */
    public record ClashCheck(boolean clear, List<ClashView> clashes) {
    }

    public record OverrideView(UUID id, UUID slotId, String resourceType, UUID resourceId, String resourceLabel, UUID clashingSlotId,
                               Instant slotStartsAt, Instant slotEndsAt, Instant clashStartsAt, Instant clashEndsAt, String reason,
                               UUID approvedBy, Instant approvedAt) {
    }

    /** facilitatorStanding is worked out for the slot's own date, so a credential that ends before then shows up here. */
    public record SlotView(UUID id, UUID cohortId, String cohortName, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String title,
                           UUID moduleId, String moduleTitle, UUID venueId, String venueName, UUID practitionerId, String facilitatorName,
                           String facilitatorStanding, String facilitatorStandingNote, String status, String cancelledReason,
                           String timeZone, Instant startsAt, Instant endsAt, long overrides) {
    }
}
