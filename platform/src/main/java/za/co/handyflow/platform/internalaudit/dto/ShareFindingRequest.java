package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

// FIX: closes the confirmed "no internal-audit engine reachable by an
// external auditor" gap. reason is required, not optional — matching
// share()'s own governance intent: a sharing decision should always
// carry a stated reason, not just a timestamp and who clicked it.
public record ShareFindingRequest(@NotBlank String reason) {}
