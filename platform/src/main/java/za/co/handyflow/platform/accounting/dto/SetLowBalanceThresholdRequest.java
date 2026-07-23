package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;

// threshold is deliberately nullable — null clears it, disabling
// low-balance alerting for that account rather than erroring.
public record SetLowBalanceThresholdRequest(
        BigDecimal threshold
) {}