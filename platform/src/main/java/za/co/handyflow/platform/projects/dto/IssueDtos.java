package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.projects.domain.model.ProjectActionItem;
import za.co.handyflow.platform.projects.domain.model.ProjectIssue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Requests and responses for the issues register and action items. */
public final class IssueDtos {
    private IssueDtos() {}

    public record CreateIssueRequest(
            @NotBlank(message = "Title is required") @Size(max = 300, message = "Title must not exceed 300 characters") String title,
            @Size(max = 4000, message = "Description must not exceed 4 000 characters") String description,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH or CRITICAL") String priority,
            UUID riskId, UUID ownerId, LocalDate dueDate) {}

    /** Every field optional; clearOwner=true removes the owner. */
    public record UpdateIssueRequest(
            @Size(min = 1, max = 300, message = "Title must be 1 to 300 characters") String title,
            @Size(max = 4000, message = "Description must not exceed 4 000 characters") String description,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH or CRITICAL") String priority,
            UUID ownerId, Boolean clearOwner, LocalDate dueDate) {}

    /** action: START | RESOLVE (resolution required) | CLOSE | REOPEN. */
    public record IssueActionRequest(
            @NotBlank(message = "Action is required") @Pattern(regexp = "START|RESOLVE|CLOSE|REOPEN", message = "Action must be START, RESOLVE, CLOSE or REOPEN") String action,
            @Size(max = 4000, message = "Resolution must not exceed 4 000 characters") String resolution) {}

    public record CreateActionItemRequest(
            @NotBlank(message = "Say whether the action is for a RISK or an ISSUE") @Pattern(regexp = "RISK|ISSUE", message = "parentType must be RISK or ISSUE") String parentType,
            @NotNull(message = "The risk or issue is required") UUID parentId,
            @NotBlank(message = "Say what has to be done") @Size(max = 300, message = "Title must not exceed 300 characters") String title,
            UUID ownerId, LocalDate dueDate) {}

    public record UpdateActionItemRequest(
            @Size(min = 1, max = 300, message = "Title must be 1 to 300 characters") String title,
            UUID ownerId, Boolean clearOwner, LocalDate dueDate) {}

    /** action: DONE | CANCEL | REOPEN. */
    public record ActionItemActionRequest(
            @NotBlank(message = "Action is required") @Pattern(regexp = "DONE|CANCEL|REOPEN", message = "Action must be DONE, CANCEL or REOPEN") String action) {}

    public record IssueResponse(UUID id, UUID projectId, String issueNumber, String title, String description, String priority, String status,
                                UUID riskId, UUID ownerId, String ownerName, LocalDate dueDate, boolean overdue,
                                String raisedByName, String resolution, Instant resolvedAt, String resolvedByName, Instant closedAt,
                                Instant createdAt, Instant updatedAt, int openActions) {
        public static IssueResponse of(ProjectIssue i, LocalDate today, int openActions) {
            return new IssueResponse(i.getId(), i.getProjectId(), i.getIssueNumber(), i.getTitle(), i.getDescription(), i.getPriority(), i.getStatus(),
                    i.getRiskId(), i.getOwnerId(), i.getOwnerName(), i.getDueDate(), i.isOpen() && i.getDueDate() != null && i.getDueDate().isBefore(today),
                    i.getRaisedByName(), i.getResolution(), i.getResolvedAt(), i.getResolvedByName(), i.getClosedAt(),
                    i.getCreatedAt(), i.getUpdatedAt(), openActions);
        }
    }

    public record ActionItemResponse(UUID id, UUID projectId, String parentType, UUID parentId, String title, UUID ownerId, String ownerName,
                                     LocalDate dueDate, String status, boolean overdue, Instant doneAt, String doneByName,
                                     String createdByName, Instant createdAt) {
        public static ActionItemResponse of(ProjectActionItem a, LocalDate today) {
            return new ActionItemResponse(a.getId(), a.getProjectId(), a.getParentType(), a.getParentId(), a.getTitle(), a.getOwnerId(), a.getOwnerName(),
                    a.getDueDate(), a.getStatus(), a.isOverdue(today), a.getDoneAt(), a.getDoneByName(), a.getCreatedByName(), a.getCreatedAt());
        }
    }
}
