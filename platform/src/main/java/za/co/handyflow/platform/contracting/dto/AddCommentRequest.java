package za.co.handyflow.platform.contracting.dto;

import jakarta.validation.constraints.NotBlank;

// Request to post a comment
public record AddCommentRequest(
        @NotBlank String comment,
        String clauseRef,           // optional clause reference
        boolean isAmendmentRequest  // true = flags for owner attention
) {}
