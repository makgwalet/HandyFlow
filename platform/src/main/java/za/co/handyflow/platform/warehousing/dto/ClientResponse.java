package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClientResponse(
        UUID id, String tradingName, String registrationNumber, BigDecimal storageRatePerUnitPerMonth,
        BigDecimal receivingFeePerUnit, BigDecimal pickFeePerUnit, BigDecimal packFeePerOrder, String contactName,
        String contactEmail, String contactPhone, String address, LocalDate onboardedAt, String status, String notes
) {}
