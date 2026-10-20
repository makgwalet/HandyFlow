package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;

/** Totals over a period, from the measured segments that ended in it (plus all spend in it). */
public record FuelPeriodSummary(
        int fillups,
        BigDecimal totalLitres,           // all litres put in during the period
        BigDecimal totalSpend,            // all recorded spend during the period
        int measuredKm,                   // km covered by assessable segments
        Double litresPer100Km,            // over assessable segments: total litres / total km
        Double kmPerLitre,
        BigDecimal costPerKm,             // fuel cost / km over segments with known cost
        int flaggedSegments,
        int indicators
) {}