package za.co.handyflow.platform.complianceservices.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WorkQueueDtos {

    private WorkQueueDtos() {}

    public record QueueEntry(String type, String severity, UUID clientId, String clientName, String title, String detail, LocalDate dueDate, String tab) {}

    public record Counts(int total, int high, int medium, int low) {}

    public record WorkQueueResponse(Counts counts, List<QueueEntry> items) {}
}
