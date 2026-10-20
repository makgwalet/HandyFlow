package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Consumption measured between two full fill-ups. */
public record FuelSegmentResponse(
        LocalDate startDate,
        LocalDate endDate,
        int startOdometer,
        int endOdometer,
        int km,
        double litres,
        int partialFills,                 // part-fills between the two full ones, included in litres
        Double litresPer100Km,
        Double kmPerLitre,
        BigDecimal cost,                  // null if any fill-up in the segment had no cost
        BigDecimal costPerKm,
        Double baselineLitresPer100Km,
        String baselineSource,            // MANUAL | ROLLING_MEDIAN | NONE
        Double deviationPercent,          // vs baseline; positive = thirstier
        String status                     // NORMAL | HIGH_CONSUMPTION | LOW_CONSUMPTION | NO_BASELINE | NOT_ASSESSED | DATA_SUSPECT
) {}