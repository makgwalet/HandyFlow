package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CalendarEntryRequest(@NotNull LocalDate date, @NotBlank String kind, @NotBlank String reason) {}
