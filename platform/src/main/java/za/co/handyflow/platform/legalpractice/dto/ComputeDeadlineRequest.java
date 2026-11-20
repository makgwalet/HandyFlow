package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** {@code description} defaults to the rule's name. */
public record ComputeDeadlineRequest(@NotNull UUID ruleId, @NotNull LocalDate triggerDate, String description, String notes) {}
