package za.co.handyflow.platform.marketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddSuppressionRequest(
        @NotBlank @Email String email,
        @Pattern(regexp = "MANUAL|COMPLAINT|HARD_BOUNCE",
                message = "reason must be MANUAL, COMPLAINT or HARD_BOUNCE") String reason,
        @Size(max = 1000) String note
) {}