package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateColumnRequest(
        @NotBlank String name, String color, int sortOrder, boolean isDoneColumn) {}
