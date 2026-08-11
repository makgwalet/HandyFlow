package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp.Outcome;

import java.time.LocalDate;

/**
 * rescheduleDate is required only when outcome == RESCHEDULED — validated
 * in CustomerFollowUpService.complete(), not here, since @NotNull can't
 * express "required conditionally on a sibling field" without a custom
 * validator that isn't worth the ceremony for one field.
 */
public record CompleteFollowUpRequest(
        @NotNull Outcome outcome,
        @FutureOrPresent LocalDate rescheduleDate
) {}