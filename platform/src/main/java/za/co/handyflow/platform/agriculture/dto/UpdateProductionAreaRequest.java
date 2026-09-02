package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;

public record UpdateProductionAreaRequest(
        String name,
        String areaType,
        BigDecimal sizeHectares,
        Integer capacity,
        String soilType,
        String notes
) {}
