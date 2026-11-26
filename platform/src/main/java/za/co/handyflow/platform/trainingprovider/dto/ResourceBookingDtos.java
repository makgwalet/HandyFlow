package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ResourceBookingDtos {
    private ResourceBookingDtos() {
    }

    /** overrideReason is read only when the booking would clash, and only from an administrator. */
    public record BookRequest(@NotNull UUID resourceId, Integer quantity, String overrideReason) {
    }

    public record ReleaseRequest(@NotBlank String reason) {
    }

    public record BookingView(UUID id, UUID slotId, UUID resourceId, String resourceNo, String resourceName, String resourceType, String resourceStatus,
                              int quantity, String status, String clashNote, String overrideReason, Instant overrideAt, String releasedReason, Instant releasedAt,
                              String slotStatus, boolean clashesNow, String clashNowNote, String venueNote, Instant createdAt) {
    }

    public record CheckResult(boolean ok, String clash) {
    }

    public record UpcomingView(UUID bookingId, UUID slotId, UUID cohortId, String cohortName, String slotTitle, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                               int quantity, boolean overridden) {
    }

    public record ResourceSchedule(UUID resourceId, int quantity, boolean shareable, List<UpcomingView> upcoming) {
    }
}
