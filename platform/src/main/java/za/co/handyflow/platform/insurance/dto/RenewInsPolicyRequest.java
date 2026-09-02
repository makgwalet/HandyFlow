package za.co.handyflow.platform.insurance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Insurer, line of business, asset type/reference and excess all carry
 * forward unchanged from the policy being renewed (a renewal is the same
 * cover, a new term) — only the fields that genuinely change at renewal
 * are captured here. Use {@code UpdateInsPolicyRequest} on the new policy
 * afterwards if something else about the cover changed too.
 */
public record RenewInsPolicyRequest(
        @NotBlank String policyNumber, // insurers typically issue a new policy/schedule number at renewal
        @NotNull BigDecimal premiumAmount,
        BigDecimal sumInsured, // null = carry forward the previous sumInsured
        @NotNull LocalDate startDate,
        @NotNull LocalDate expiryDate
) {}
