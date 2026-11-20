package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code outcome}: CLEARED_AFTER_REVIEW | PROCEED_WITH_CONSENT | DECLINED. */
public record DecideConflictCheckRequest(@NotBlank String outcome, @NotBlank String reason) {}
