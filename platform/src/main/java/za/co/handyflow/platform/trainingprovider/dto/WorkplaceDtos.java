package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkplaceDtos {
    private WorkplaceDtos() {
    }

    public record PlacementRequest(@NotNull UUID enrolmentId, @NotBlank String hostName, String hostContact, @NotBlank String mentorName, String mentorContact,
                                   String agreementRef, @NotNull LocalDate startDate, LocalDate endDate) {
    }

    public record PlacementUpdate(@NotBlank String hostName, String hostContact, @NotBlank String mentorName, String mentorContact, String agreementRef,
                                  @NotNull LocalDate startDate, LocalDate endDate) {
    }

    public record DayRequest(@NotNull LocalDate on) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record LogRequest(@NotNull LocalDate logDate, @NotNull BigDecimal hours, @NotBlank String activity) {
    }

    public record ConfirmRequest(@NotNull LocalDate confirmedOn, @NotBlank String mentorName) {
    }

    public record LogView(UUID id, UUID placementId, LocalDate logDate, BigDecimal hours, String activity, String status, String voidReason, LocalDate confirmedOn,
                          String confirmedByName) {
    }

    public record PlacementView(UUID id, String placementNo, UUID enrolmentId, String enrolmentNo, String learnerName, String programmeTitle, String hostName,
                                String hostContact, String mentorName, String mentorContact, String agreementRef, LocalDate startDate, LocalDate endDate, String status,
                                LocalDate startedOn, LocalDate endedOn, String cancelReason, List<String> nextStatuses, BigDecimal confirmedHours, BigDecimal unconfirmedHours,
                                String summary) {
    }

    public record PlacementDetail(PlacementView placement, List<LogView> logs) {
    }
}
