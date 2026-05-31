package za.co.handyflow.platform.security.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateCheckpointRequest(@NotBlank String name, String description) {}
