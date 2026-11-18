package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Request and response shapes for contract obligations and renewals. */
public final class ContractObligationDtos {
    private ContractObligationDtos() {}

    public record ObligationRequest(@NotBlank String title, String description, String obligationType,
                                    @NotBlank String responsibleSide, String ownerEmail,
                                    @NotNull LocalDate dueDate, String recurrence) {}

    public record CompleteObligationRequest(String note) {}

    public record WaiveObligationRequest(@NotBlank String reason) {}

    public record ObligationResponse(UUID id, UUID contractId, String contractNumber, String contractTitle, String title,
                                     String description, String obligationType, String responsibleSide, String ownerEmail,
                                     LocalDate dueDate, String recurrence, String status, boolean overdue, long daysUntilDue,
                                     Instant completedAt, String completionNote, String waivedReason) {}

    public record ObligationSummary(long open, long overdue, long dueNext7Days) {}

    /** state: DECISION_NEEDED, NOTICE_PASSED (the notice deadline has gone with no decision) or DECIDED. */
    public record RenewalItem(UUID contractId, String contractNumber, String title, LocalDate endDate, boolean autoRenew,
                              int renewalNoticeDays, LocalDate noticeDeadline, long daysToEnd, long daysToNotice, String state,
                              String latestDecision, Instant latestDecisionAt, UUID successorContractId) {}

    public record RenewalDecisionRequest(@NotBlank String decision, String note, LocalDate newStartDate, LocalDate newEndDate) {}

    public record RenewalDecisionResponse(UUID id, UUID contractId, String decision, String note, Instant decidedAt, UUID successorContractId) {}
}
