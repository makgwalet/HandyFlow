package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record DeadlinePreviewRequest(@NotNull UUID ruleId, @NotNull LocalDate triggerDate) {}
