package za.co.handyflow.platform.legalpractice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() { }

    public record TaskSummary(UUID id, UUID matterId, String matterLabel, UUID clientId, String title, String priority, String status, UUID assigneeId, String assigneeName,
                              LocalDate dueDate, boolean overdue, UUID workflowRunId, Instant updatedAt) { }

    public record EventResponse(String eventType, String actorName, String detail, Instant occurredAt) { }

    public record TaskDetail(TaskSummary task, String description, Instant completedAt, String completedByName, String cancelReason, String createdByName, Instant createdAt,
                             List<EventResponse> events) { }

    public record MyTasks(long overdue) { }

    public record StepResponse(int stepNo, String title, String description, String priority, int offsetDays, String assignTo) { }

    public record TemplateSummary(UUID id, String name, String description, String matterType, boolean active, int steps, Instant updatedAt) { }

    public record TemplateDetail(TemplateSummary template, String createdByName, List<StepResponse> steps) { }

    public record RunResponse(UUID id, UUID templateId, String templateName, LocalDate startDate, int tasksCreated, String appliedByName, Instant appliedAt) { }

    public record ApplyResult(RunResponse run, List<TaskSummary> tasks, List<String> warnings) { }

    public record CreateTaskRequest(UUID matterId, String title, String description, String priority, LocalDate dueDate, UUID assigneeId, String assigneeName) { }

    public record EditTaskRequest(String title, String description, String priority, LocalDate dueDate) { }

    public record AssignRequest(UUID assigneeId, String assigneeName) { }

    public record ReasonRequest(String reason) { }

    public record StepRequest(String title, String description, String priority, Integer offsetDays, String assignTo) { }

    public record TemplateRequest(String name, String description, String matterType, List<StepRequest> steps) { }

    public record ApplyRequest(UUID matterId, LocalDate startDate) { }
}
