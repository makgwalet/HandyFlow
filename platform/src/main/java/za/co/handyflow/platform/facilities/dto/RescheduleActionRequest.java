package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RescheduleActionRequest(@NotNull LocalDate dueDate) {}
