package za.co.handyflow.platform.earthmoving.dto;

import java.time.LocalDate;

public record DeployAssetRequest(
        String siteName,
        String clientName,
        String contactName,
        String contactPhone,
        LocalDate startDate,
        LocalDate expectedEndDate,
        String notes
) {}