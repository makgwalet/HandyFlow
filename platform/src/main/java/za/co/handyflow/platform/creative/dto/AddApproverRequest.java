package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddApproverRequest(
        @NotBlank String approverName,
        @NotBlank @Email String approverEmail
) {}
