package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {
    private AttendanceDtos() {
    }

    public record Entry(@NotNull UUID memberId, @NotBlank String status, String note) {
    }

    public record RecordRequest(@NotEmpty List<@Valid Entry> entries) {
    }

    public record CorrectRequest(@NotBlank String status, String note, @NotBlank String reason) {
    }

    public record HistoryLine(UUID id, String action, String fromStatus, String toStatus, String reason, UUID actedBy, Instant actedAt) {
    }

    public record RegisterRow(UUID memberId, UUID enrolmentId, String enrolmentNo, UUID learnerId, String learnerName, UUID attendanceId,
                              String status, String note, int corrections) {
    }

    public record Register(UUID slotId, UUID cohortId, String cohortName, String title, LocalDate slotDate, LocalTime startTime, LocalTime endTime,
                           String slotStatus, boolean canRecord, String cannotRecordBecause, List<RegisterRow> rows) {
    }

    public record SummaryRow(UUID memberId, UUID enrolmentId, String enrolmentNo, UUID learnerId, String learnerName, int expected, int attended,
                             int absent, int excused, int unrecorded, Integer ratePercent) {
    }

    public record CohortSummary(UUID cohortId, String note, List<SummaryRow> rows) {
    }
}
