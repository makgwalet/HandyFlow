package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfileResponse(
        UUID id, String warehouseName, String registrationNumber, BigDecimal defaultStorageRatePerUnitPerMonth,
        BigDecimal defaultReceivingFeePerUnit, BigDecimal defaultPickFeePerUnit, BigDecimal defaultPackFeePerOrder,
        String contactEmail, String contactPhone, String physicalAddress
) {}
