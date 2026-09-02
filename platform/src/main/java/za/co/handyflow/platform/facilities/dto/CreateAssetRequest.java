package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAssetRequest(
        @NotNull UUID siteId, String assetTag, @NotBlank String name, @NotBlank String assetType,
        String location, String manufacturer, String model, String serialNumber,
        LocalDate installDate, LocalDate warrantyExpiryDate, String criticality, String notes
) {}
