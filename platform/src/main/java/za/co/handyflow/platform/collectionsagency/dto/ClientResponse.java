package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClientResponse(
        UUID id, String tradingName, String registrationNumber, BigDecimal commissionRatePct, String contactName,
        String contactEmail, String contactPhone, String address, BigDecimal trustBalance, LocalDate onboardedAt,
        String status, String notes
) {}
