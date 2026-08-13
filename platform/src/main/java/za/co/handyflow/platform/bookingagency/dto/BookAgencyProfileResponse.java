package za.co.handyflow.platform.bookingagency.dto;

import java.util.UUID;

public record BookAgencyProfileResponse(
        UUID id,
        String agencyName,
        String registrationNumber,
        String email,
        String phone,
        String physicalAddress,
        String logoUrl
) {}