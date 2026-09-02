package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

/** newStatus: IN_PROGRESS | DISPUTED | PAYMENT_PLAN_ACTIVE | RECOVERED | RETURNED_TO_CLIENT | WRITTEN_OFF | CLOSED — see CollAgencyDebtorAccount for the terminal subset. */
public record AdvanceStatusRequest(@NotBlank String newStatus) {}
