package za.co.handyflow.platform.payrollbureau.dto;

import java.util.UUID;

public record BureauProfileResponse(
        UUID id,
        String firmName,
        String registrationNumber,
        String sdlNumber,
        String email,
        String phone,
        String physicalAddress,
        String logoUrl
) {}