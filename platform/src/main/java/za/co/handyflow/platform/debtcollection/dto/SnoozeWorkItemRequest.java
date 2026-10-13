package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SnoozeWorkItemRequest(@NotNull LocalDate until, @NotBlank String reason) {}
