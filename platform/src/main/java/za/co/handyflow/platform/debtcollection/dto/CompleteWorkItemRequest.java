package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemDisposition;

/** Valid dispositions depend on the item's type (see WorkItemResponse.allowedDispositions); notes are required for NO_ACTION_REQUIRED/OTHER. */
public record CompleteWorkItemRequest(@NotNull WorkItemDisposition disposition, String notes) {}
