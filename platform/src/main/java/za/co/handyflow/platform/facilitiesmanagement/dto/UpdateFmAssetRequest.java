package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.LocalDate;

public record UpdateFmAssetRequest(
        String name, String location, String manufacturer, String model, String serialNumber,
        LocalDate warrantyExpiryDate, String criticality, String notes
) {}
