package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BankRuleResponse(
        UUID id, String name, UUID bankAccountId, String direction, String matchText, String matchField,
        BigDecimal minAmount, BigDecimal maxAmount, UUID otherAccountId, String otherAccountName,
        String description, int priority, boolean active, int timesApplied
) {}
