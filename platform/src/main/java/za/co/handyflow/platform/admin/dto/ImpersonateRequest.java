package za.co.handyflow.platform.admin.dto;
import jakarta.validation.constraints.NotBlank;
public record ImpersonateRequest(
        @NotBlank String tenantSlug,
        String reason
) {}