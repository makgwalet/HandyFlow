package za.co.handyflow.platform.supplychain.dto;

import java.util.List;

/** One budget with its sub-budgets, for the allocation view. {@code budget} carries the rolled-up usage. */
public record BudgetTreeNode(BudgetResponse budget, List<BudgetTreeNode> children) {}
