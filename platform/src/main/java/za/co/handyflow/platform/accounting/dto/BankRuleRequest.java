package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code bankAccountId} empty means the rule applies to every bank account. */
public record BankRuleRequest(
        String name, UUID bankAccountId, String direction, String matchText, String matchField,
        BigDecimal minAmount, BigDecimal maxAmount, UUID otherAccountId, String description,
        Integer priority, Boolean active
) {}
