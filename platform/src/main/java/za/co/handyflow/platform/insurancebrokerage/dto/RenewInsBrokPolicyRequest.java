package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carries insurer/line-of-business/asset/excess/commission-rate forward
 * unchanged from the current term — same InsPolicyService.renew()
 * precedent (only policy number, premium, sum insured, and dates
 * genuinely change at renewal).
 */
public record RenewInsBrokPolicyRequest(
        @NotBlank String policyNumber,
        BigDecimal sumInsured,
        @NotNull BigDecimal premiumAmount,
        @NotNull LocalDate startDate,
        @NotNull LocalDate expiryDate
) {}
