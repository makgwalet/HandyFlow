package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The three disclosure flags are @AssertTrue-validated here so a non-
 * compliant request fails fast at the API boundary with a normal 400 —
 * CollAgencyContactLog.record() enforces the same rule again at the
 * domain layer regardless (belt-and-braces, not a replacement for it).
 */
public record RecordContactRequest(
        LocalDate contactDate, @NotBlank String contactMethod, @NotBlank String outcome,
        @AssertTrue(message = "Third-party-collector status must be disclosed on every contact") boolean disclosedThirdPartyCollector,
        @AssertTrue(message = "The original creditor must be disclosed on every contact") boolean disclosedOriginalCreditor,
        @AssertTrue(message = "The debtor's statutory rights must be disclosed on every contact") boolean disclosedDebtorRights,
        String notes, LocalDate promisedPaymentDate, BigDecimal promisedPaymentAmount
) {}
