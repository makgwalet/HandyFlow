package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.ContractApproval;
import za.co.handyflow.platform.contracting.domain.model.ContractApprovalEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ContractApprovalDtos {
    private ContractApprovalDtos() {}

    public record PolicyView(ApprovalAction action, boolean required, boolean allowSoleUserException, boolean usingDefault) {}

    public record PolicyRequest(boolean required, boolean allowSoleUserException) {}

    public record RequestApproval(@NotNull ApprovalAction action, @NotNull UUID subjectId, @Size(max = 1000) String note) {}

    public record Decision(@Size(max = 1000) String note, boolean soleUserException, @Size(max = 1000) String exceptionReason) {}

    public record ApprovalView(UUID id, ApprovalAction action, UUID subjectId, String subjectLabel, String status,
                               UUID requestedBy, Instant requestedAt, String requestNote,
                               UUID decidedBy, Instant decidedAt, String decisionNote,
                               boolean soleUserException, String exceptionReason, Instant closedAt,
                               boolean mine, boolean canDecide) {
        public static ApprovalView of(ContractApproval a, UUID me, boolean canDecideRole) {
            boolean mine = a.getRequestedBy().equals(me);
            return new ApprovalView(a.getId(), a.getAction(), a.getSubjectId(), a.getSubjectLabel(), a.getStatus(),
                    a.getRequestedBy(), a.getRequestedAt(), a.getRequestNote(), a.getDecidedBy(), a.getDecidedAt(), a.getDecisionNote(),
                    a.isSoleUserException(), a.getExceptionReason(), a.getClosedAt(), mine, a.isPending() && canDecideRole);
        }
    }

    public record EventView(String eventType, UUID actorUserId, String detail, Instant at) {
        public static EventView of(ContractApprovalEvent e) { return new EventView(e.getEventType(), e.getActorUserId(), e.getDetail(), e.getCreatedAt()); }
    }

    public record Detail(ApprovalView approval, List<EventView> events) {}
}
