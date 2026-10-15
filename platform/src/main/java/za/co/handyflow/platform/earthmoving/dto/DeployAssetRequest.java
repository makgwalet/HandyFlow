package za.co.handyflow.platform.earthmoving.dto;

import java.time.LocalDate;

public record DeployAssetRequest(
        @jakarta.validation.constraints.NotBlank String siteName,
        String clientName,
        String contactName,
        String contactPhone,
        LocalDate startDate,
        LocalDate expectedEndDate,
        String notes
) {}