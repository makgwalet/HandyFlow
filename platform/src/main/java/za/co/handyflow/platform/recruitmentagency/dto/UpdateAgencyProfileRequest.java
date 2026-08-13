package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record UpdateAgencyProfileRequest(
        @NotBlank String agencyName,
        String registrationNumber,
        String email,
        String phone,
        String physicalAddress,
        String logoUrl,
        BigDecimal defaultPlacementFeePct
) {}