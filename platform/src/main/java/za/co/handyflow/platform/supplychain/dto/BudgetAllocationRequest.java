package za.co.handyflow.platform.supplychain.dto;

import java.util.UUID;

/** Allocate a budget out of a parent, or (parentBudgetId null) make it top level again. */
public record BudgetAllocationRequest(UUID parentBudgetId) {}
