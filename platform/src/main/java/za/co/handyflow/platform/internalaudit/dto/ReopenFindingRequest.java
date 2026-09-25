package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

// FIX: closes a real gap in reopen() — it previously took no reason at
// all, so a reopened finding could never explain why. required, not
// optional, matching ShareFindingRequest's own governance intent.
public record ReopenFindingRequest(@NotBlank String reason) {}
