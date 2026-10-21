package za.co.handyflow.platform.hr.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for onboarding. */
public final class OnboardingDtos {

    private OnboardingDtos() {}

    public record Summary(UUID id, UUID employeeId, String employeeNumber, String employeeName, String jobTitle, String department,
                          LocalDate startDate, LocalDate probationEndDate, String status,
                          int total, int finished, int requiredOpen, int overdue, int percent) {}

    /**
     * {@code status} is what the task shows now: DONE, WAIVED or OPEN. {@code automatic} is true when the task is checked against
     * the employee record, and so cannot be ticked by hand.
     */
    public record Task(UUID id, String title, String description, String category, boolean required, LocalDate dueDate, boolean automatic,
                       String check, UUID assigneeId, String status, boolean overdue, String note, Instant resolvedAt) {}

    public record Detail(Summary summary, String cancelReason, List<Task> tasks, List<String> finishProblems, boolean canManage) {}

    public record Template(UUID id, String title, String description, String category, boolean required, int dueOffsetDays,
                           String dueBasis, String check, int sortOrder, boolean active) {}

    public record TemplateRequest(String title, String description, String category, Boolean required, Integer dueOffsetDays,
                                  String dueBasis, String check) {}

    public record TaskRequest(String title, String description, String category, Boolean required, LocalDate dueDate, UUID assigneeId) {}

    public record NoteRequest(String note) {}

    public record AssignRequest(UUID assigneeId) {}

    public record DueRequest(LocalDate dueDate) {}

    public record ProbationRequest(LocalDate probationEndDate) {}

    public record ActiveRequest(boolean active) {}
}
