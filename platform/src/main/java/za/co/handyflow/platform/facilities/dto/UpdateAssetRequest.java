package za.co.handyflow.platform.facilities.dto;

import java.time.LocalDate;

public record UpdateAssetRequest(
        String name, String location, String manufacturer, String model, String serialNumber,
        LocalDate warrantyExpiryDate, String criticality, String notes
) {}
