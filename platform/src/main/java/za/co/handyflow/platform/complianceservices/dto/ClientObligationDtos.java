package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ClientObligationDtos {

    private ClientObligationDtos() {}

    public record DecisionRequest(@NotBlank @Size(max = 15) String state, @Size(max = 2000) String reason, @Size(max = 300) String evidenceRef,
                                  LocalDate effectiveFrom, LocalDate reviewDue) {}

    public record ApproveDecisionRequest(@Size(max = 1000) String exceptionReason) {}

    public record AssignmentView(UUID obligationId, String code, String name, int versionNumber, String state, String effectiveState,
                                 String suggestedState, List<String> suggestionReasons, boolean basedOnUnconfirmed, boolean overridden,
                                 UUID decidedBy, Instant decidedAt, String decisionReason, String evidenceRef, LocalDate effectiveFrom,
                                 LocalDate reviewDue, String approvalStatus, UUID approvedBy, boolean reviewNeeded) {}

    public record Counts(int applicable, int notApplicable, int pending, int awaitingApproval, int reviewNeeded) {}

    public record AssignmentsResponse(List<AssignmentView> assignments, Counts counts) {}

    public record HistoryView(String fromState, String toState, String event, String reason, UUID actor, Integer versionNumber, Instant occurredAt) {}
}
