package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class TimetableDtos {
    private TimetableDtos() {
    }

    public record SlotRequest(@NotBlank String title, @NotNull LocalDate slotDate, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                              UUID moduleId, UUID venueId, UUID practitionerId) {
    }

    /** facilitatorStanding is worked out for the slot's own date, so a credential that ends before then shows up here. */
    public record SlotView(UUID id, UUID cohortId, String cohortName, LocalDate slotDate, LocalTime startTime, LocalTime endTime, String title,
                           UUID moduleId, String moduleTitle, UUID venueId, String venueName, UUID practitionerId, String facilitatorName,
                           String facilitatorStanding, String facilitatorStandingNote, String status, String cancelledReason) {
    }
}
