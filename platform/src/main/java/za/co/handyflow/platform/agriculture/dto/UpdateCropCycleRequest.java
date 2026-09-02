package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateCropCycleRequest(
        String variety,
        String cycleName,
        BigDecimal areaPlantedHectares,
        LocalDate expectedHarvestDate,
        String notes
) {}
