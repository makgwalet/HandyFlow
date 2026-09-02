package za.co.handyflow.platform.trainingprovider.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String tradingName,
        String registrationNumber,
        String accreditationBody,
        String accreditationNumber,
        LocalDate accreditationExpiry,
        String address,
        String phone,
        String email,
        String logoUrl
) {}
