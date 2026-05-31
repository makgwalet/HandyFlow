package za.co.handyflow.platform.creative.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCommentRequest(
        @NotBlank String comment,
        String authorName   // only used by PUBLIC (client) endpoint
) {}
