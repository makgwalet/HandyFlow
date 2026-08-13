package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCandidateRequest(
        @NotBlank String fullName,
        String email,
        String phone,
        String currentTitle,
        String currentEmployer,
        String skills,
        String source
) {}