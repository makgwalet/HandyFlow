package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ScheduleNextActionRequest(@NotNull LocalDate nextActionDate) {}
