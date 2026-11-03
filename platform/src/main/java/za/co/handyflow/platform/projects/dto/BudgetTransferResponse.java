package za.co.handyflow.platform.projects.dto;

import java.util.UUID;

public record BudgetTransferResponse(UUID transferId, BudgetLineResponse from, BudgetLineResponse to) {}