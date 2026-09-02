package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * remittanceDate defaults to today when left null. commissionRatePctOverride
 * is optional — when omitted, the client's own rate (or the agency default)
 * applies; see CollAgencyTrustTransactionService.resolveCommissionRate().
 */
public record ProcessRemittanceRequest(LocalDate remittanceDate, BigDecimal commissionRatePctOverride) {}
