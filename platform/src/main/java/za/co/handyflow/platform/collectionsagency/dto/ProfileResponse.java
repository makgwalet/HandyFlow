package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
        UUID id, String agencyName, String firmRegistrationNumber, LocalDate firmRegistrationExpiryDate,
        BigDecimal defaultCommissionPct, String contactEmail, String contactPhone, String physicalAddress
) {}
