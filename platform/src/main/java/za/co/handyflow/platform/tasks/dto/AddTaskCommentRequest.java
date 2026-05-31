package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddTaskCommentRequest(
        @NotBlank @Size(max = 5000) String body) {}
