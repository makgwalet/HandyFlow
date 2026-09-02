package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Rate fields may all be left null — the client then falls through to WhseProfile's own defaults. */
public record CreateClientRequest(
        @NotBlank String tradingName, String registrationNumber, BigDecimal storageRatePerUnitPerMonth,
        BigDecimal receivingFeePerUnit, BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder, String contactName,
        String contactEmail, String contactPhone, String address
) {}
