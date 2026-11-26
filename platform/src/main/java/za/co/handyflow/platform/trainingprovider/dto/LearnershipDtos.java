package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public final class LearnershipDtos {
    private LearnershipDtos() {
    }

    public record AgreementRequest(@NotNull UUID enrolmentId, UUID clientId, @NotBlank String employerName, String employerContact, @NotNull LocalDate startDate, LocalDate endDate) {
    }

    public record AgreementUpdate(UUID clientId, @NotBlank String employerName, String employerContact, @NotNull LocalDate startDate, LocalDate endDate) {
    }

    public record SignRequest(@NotNull LocalDate learnerSignedOn, @NotNull LocalDate employerSignedOn, @NotNull LocalDate providerSignedOn) {
    }

    public record RegistrationRequest(@NotBlank String reference, @NotNull LocalDate recordedOn) {
    }

    public record EndRequest(@NotNull LocalDate endedOn, @NotBlank String reason) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record AgreementView(UUID id, String agreementNo, UUID enrolmentId, String enrolmentNo, String learnerName, String programmeTitle, UUID clientId, String employerName,
                                String employerContact, LocalDate startDate, LocalDate endDate, String status, LocalDate learnerSignedOn, LocalDate employerSignedOn,
                                LocalDate providerSignedOn, String registrationRef, LocalDate registrationRecordedOn, LocalDate endedOn, String endReason, String cancelReason,
                                String summary) {
    }
}
