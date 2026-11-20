package za.co.handyflow.platform.legalpractice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateStatementRequest(@NotNull BigDecimal statementBalance, String note) {}
