package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LearnerDtos {

    private LearnerDtos() {
    }

    public record LearnerRequest(@NotBlank String firstNames, @NotBlank String surname, @NotBlank String idType, String idNumber,
                                 LocalDate dateOfBirth, String email, String phone, String address, String province, UUID clientId,
                                 UUID delegateId, boolean consentGiven, LocalDate consentOn, String consentNote, String linkReason) {
        public LearnerRequest(String firstNames, String surname, String idType, String idNumber, LocalDate dateOfBirth, String email, String phone,
                              String address, String province, UUID clientId, UUID delegateId, boolean consentGiven, LocalDate consentOn,
                              String consentNote) {
            this(firstNames, surname, idType, idNumber, dateOfBirth, email, phone, address, province, clientId, delegateId, consentGiven,
                    consentOn, consentNote, null);
        }
    }

    /** The list shows the identity number masked; the detail shows it in full. */
    public record LearnerView(UUID id, String learnerNo, String firstNames, String surname, String idType, String idNumber,
                              String idNumberMasked, LocalDate dateOfBirth, String email, String phone, String address, String province,
                              UUID clientId, String clientName, UUID delegateId, boolean consentGiven, LocalDate consentOn,
                              String consentNote, String status, String archivedReason, long liveEnrolments, UUID mergedIntoId, String delegateName) {
    }

    public record DelegateLinkRequest(UUID delegateId, String reason) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record ApplicationRequest(@NotNull UUID learnerId, @NotNull UUID programmeId, UUID siteId, LocalDate preferredStart, String sourceNote) {
    }

    public record ApplicationTransitionRequest(@NotBlank String toStatus, String reason, Boolean entryRequirementsMet,
                                               String entryCheckNote, String overrideReason) {
    }

    public record ApplicationView(UUID id, UUID learnerId, String learnerName, UUID programmeId, String programmeTitle, UUID siteId,
                                  String siteName, LocalDate preferredStart, String sourceNote, String status, String statusReason,
                                  Boolean entryRequirementsMet, String entryCheckNote, String entryOverrideReason, List<String> nextStatuses,
                                  Instant createdAt) {
    }

    public record EventView(UUID id, String fromStatus, String toStatus, String reason, String overrideReason, UUID actedBy, Instant actedAt) {
    }

    public record ApplicationDetail(ApplicationView application, List<EventView> history) {
    }

    public record EnrolRequest(@NotNull UUID learnerId, @NotNull UUID programmeId, @NotNull UUID siteId, UUID applicationId, UUID clientId,
                               @NotNull LocalDate startDate, LocalDate expectedEndDate, String overrideReason) {
    }

    public record EnrolmentTransitionRequest(@NotBlank String toStatus, String reason) {
    }

    public record EnrolmentView(UUID id, String enrolmentNo, UUID learnerId, String learnerName, UUID programmeId, String programmeTitle,
                                UUID programmeVersionId, Integer programmeVersionNo, UUID siteId, String siteName, UUID applicationId,
                                UUID clientId, LocalDate startDate, LocalDate expectedEndDate, String status, String statusReason,
                                String trainingActivity, String coverOutcome, LocalDate coverCheckedOn, String coverOverrideReason,
                                List<String> nextStatuses, Instant createdAt) {
    }

    public record EnrolmentDetail(EnrolmentView enrolment, List<EventView> history) {
    }

    public record LearnerDetail(LearnerView learner, List<ApplicationView> applications, List<EnrolmentView> enrolments) {
    }
}
