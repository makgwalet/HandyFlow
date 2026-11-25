package za.co.handyflow.platform.trainingprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record ObligationRequest(@NotNull UUID authorityId, UUID programmeId, @NotBlank String name, @NotBlank String cadence, @NotNull Integer dueDaysAfterEnd,
                                    @NotBlank String sourceNote) {
    }

    public record ObligationUpdate(@NotBlank String name, @NotBlank String cadence, @NotNull Integer dueDaysAfterEnd, @NotBlank String sourceNote) {
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    public record PeriodRequest(@NotNull UUID obligationId, @NotNull LocalDate periodStart, @NotNull LocalDate periodEnd, LocalDate dueOn, String dueOverrideReason) {
    }

    public record SubmitRequest(@NotNull LocalDate submittedOn, String submissionRef, String note) {
    }

    public record AcknowledgeRequest(@NotNull LocalDate acknowledgedOn, String acknowledgementRef) {
    }

    public record ObligationView(UUID id, String obligationNo, UUID authorityId, String authorityName, UUID programmeId, String programmeTitle, String name, String cadence,
                                 int dueDaysAfterEnd, String sourceNote, String status, String retiredReason) {
    }

    public record PeriodView(UUID id, String periodNo, UUID obligationId, String obligationNo, String obligationName, String authorityName, String programmeTitle,
                             LocalDate periodStart, LocalDate periodEnd, LocalDate dueOn, String dueOverrideReason, String status, String state, LocalDate submittedOn,
                             String submissionRef, LocalDate acknowledgedOn, String acknowledgementRef, String waivedReason, String voidReason, String note) {
    }
}
