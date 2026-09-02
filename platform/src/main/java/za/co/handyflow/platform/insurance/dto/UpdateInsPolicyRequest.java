package za.co.handyflow.platform.insurance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code policyNumber} and {@code lineOfBusiness} are deliberately not
 * editable here — a policy number changing suggests a new policy (use
 * renew or create a fresh one), and reclassifying a policy's line of
 * business after the fact is more likely a data-entry mistake worth
 * catching than a real business event. Everything else can be corrected.
 */
public record UpdateInsPolicyRequest(
        String insurerName,
        String assetType,
        String assetReference,
        BigDecimal sumInsured,
        BigDecimal premiumAmount,
        String premiumFrequency,
        BigDecimal excessAmount,
        String brokerOrInsurerContact,
        LocalDate startDate,
        LocalDate expiryDate,
        String notes
) {}
