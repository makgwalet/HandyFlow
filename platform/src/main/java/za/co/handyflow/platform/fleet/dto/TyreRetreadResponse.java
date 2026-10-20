package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TyreRetreadResponse(
        LocalDate retreadedAt,
        String supplier,
        BigDecimal cost,
        BigDecimal newTreadDepthMm,
        String notes
) {}