package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBranchRequest(
        @NotBlank String name,
        String region,
        String description
) {}
