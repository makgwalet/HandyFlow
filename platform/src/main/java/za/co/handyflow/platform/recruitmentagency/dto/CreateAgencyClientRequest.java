package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateAgencyClientRequest(
        @NotBlank String tradingName,
        String registrationNumber,
        String industry,
        BigDecimal placementFeePct,
        Integer guaranteePeriodDays,
        String contactName,
        String contactEmail,
        String contactPhone
) {}