package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record MoveTaskRequest(
        @NotNull UUID columnId,
        int sortOrder) {}