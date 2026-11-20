package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** {@code useCurrentRule} true re-reads the rule as it is now; false (the default) keeps the rule as it was when first computed. */
public record RecomputeDeadlineRequest(@NotNull LocalDate triggerDate, @NotBlank String reason, boolean useCurrentRule) {}
