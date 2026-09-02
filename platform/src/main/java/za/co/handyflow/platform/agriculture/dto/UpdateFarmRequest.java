package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;

public record UpdateFarmRequest(
        String name,
        String farmType,
        String registrationNumber,
        String province,
        String region,
        Double gpsLatitude,
        Double gpsLongitude,
        BigDecimal totalHectares,
        String notes
) {}
