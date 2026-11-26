package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class LearnerPortalDtos {

    private LearnerPortalDtos() {
    }

    public record InviteRequest(@NotBlank String email, @NotBlank String reason) {
    }

    public record EndRequest(@NotBlank String reason) {
    }

    public record GrantResponse(UUID id, String email, String status, String reason, Instant acceptedAt, String endedReason, Instant createdAt) {
    }

    public record AttendanceCounts(int recorded, int present, int late, int absent, int excused) {
    }

    /** One enrolment as the learner or the employer sees it. No scores, no competence wording. */
    public record EnrolmentView(String enrolmentNo, String programmeCode, String programmeTitle, String status, LocalDate startDate, LocalDate expectedEndDate,
                                String cohortName, AttendanceCounts attendance, String outcome, LocalDate outcomeOn) {
    }

    public record SessionView(String cohortName, String title, LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    public record LearnerMe(String learnerNo, String fullName, String note, List<EnrolmentView> enrolments, List<SessionView> upcoming) {
    }

    public record EmployerRow(String learnerNo, String fullName, EnrolmentView enrolment) {
    }

    public record EmployerLearners(String note, List<EmployerRow> rows, int hiddenForConsent) {
    }
}
