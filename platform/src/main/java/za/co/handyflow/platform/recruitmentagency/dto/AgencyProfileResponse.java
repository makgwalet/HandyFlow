package za.co.handyflow.platform.recruitmentagency.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AgencyProfileResponse(
        UUID id,
        String agencyName,
        String registrationNumber,
        String email,
        String phone,
        String physicalAddress,
        String logoUrl,
        BigDecimal defaultPlacementFeePct
) {}