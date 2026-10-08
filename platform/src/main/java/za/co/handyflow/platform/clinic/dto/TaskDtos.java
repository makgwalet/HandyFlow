package za.co.handyflow.platform.clinic.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Clinic tasks (patch 0151). */
public final class TaskDtos {
    private TaskDtos() {}

    /** assignedTo null = anyone; sourceType/sourceId link a task to what it came from, for example LAB_RESULT and the result id. */
    public record CreateTaskRequest(UUID patientId, UUID assignedTo, String kind, String title, String detail,
                                    LocalDate dueDate, String sourceType, UUID sourceId) {}
    public record NoteRequest(String note) {}
    public record TaskRow(UUID id, UUID patientId, String patientName, UUID assignedTo, String kind, String title, String detail,
                          LocalDate dueDate, boolean overdue, String status, String sourceType, UUID sourceId, UUID createdBy,
                          Instant createdAt, Instant closedAt, String closingNote) {}
}
