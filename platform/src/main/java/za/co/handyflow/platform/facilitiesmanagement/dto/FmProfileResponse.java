package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FmProfileResponse(
        UUID id, String companyName, String registrationNumber, String contactEmail,
        String contactPhone, String notes, Instant createdAt,
        boolean vatRegistered, String vatNumber, BigDecimal vatRate, int paymentTermsDays,
        String arAccountCode, String revenueAccountCode, String vatAccountCode, String bankAccountCode, String address
) {}