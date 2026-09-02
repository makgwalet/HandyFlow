package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** commissionRatePct may be left null — the client then uses the agency's own default (CollAgencyProfile.defaultCommissionPct). */
public record CreateClientRequest(
        @NotBlank String tradingName, String registrationNumber, BigDecimal commissionRatePct, String contactName,
        String contactEmail, String contactPhone, String address
) {}
