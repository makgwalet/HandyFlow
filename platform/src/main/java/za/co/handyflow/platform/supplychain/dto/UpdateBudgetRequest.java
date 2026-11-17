package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;

/** Null fields are unchanged. status = CLOSED closes the budget (it stops applying to new spend). */
public record UpdateBudgetRequest(String name, BigDecimal amount, String controlMode, String status) {}
