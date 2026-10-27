package za.co.handyflow.platform.facilitiesmanagement.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Core profile fields (full-replace) plus billing settings (partial: a null billing field leaves the
 * existing setting unchanged). A VAT-registered company needs a VAT number and a VAT ledger account code.
 */
public record UpsertFmProfileRequest(
        @NotBlank String companyName, String registrationNumber, String contactEmail,
        String contactPhone, String notes,
        Boolean vatRegistered, String vatNumber, BigDecimal vatRate, Integer paymentTermsDays,
        String arAccountCode, String revenueAccountCode, String vatAccountCode, String bankAccountCode
) {}