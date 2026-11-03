package za.co.handyflow.platform.projects.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * shiftDependents = true pushes later-starting dependent tasks out so their dependencies are
 * respected again (never earlier; completed / started tasks are never moved).
 */
public record UpdateTaskDatesRequest(
        @NotNull(message = "plannedStart is required") LocalDate plannedStart,
        @NotNull(message = "plannedEnd is required")   LocalDate plannedEnd,
        boolean shiftDependents
) {}