package za.co.handyflow.platform.admin.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record ExtendPilotRequest(
        @NotBlank String tenantSlug,
        @Min(1)   int    days
) {}