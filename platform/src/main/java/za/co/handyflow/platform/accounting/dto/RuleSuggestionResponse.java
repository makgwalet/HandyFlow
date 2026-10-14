package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** What a rule would do to one unmatched statement line. Nothing is posted until the suggestion is applied. */
public record RuleSuggestionResponse(
        UUID transactionId, LocalDate date, String description, String reference, BigDecimal amount, String transactionType,
        UUID ruleId, String ruleName, UUID otherAccountId, String otherAccountName
) {}
