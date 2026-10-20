package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hours worked by an asset in a period, from its meter readings: the last reading before the period (or the
 * first one inside it) to the last reading inside it. Utilisation compares that with what the working
 * weekdays (Monday–Friday) of the period would give at the asset's target hours per day.
 */
public record UtilisationResponse(
        UUID vehicleId,
        String registration,
        String assetClass,
        LocalDate from,
        LocalDate to,
        int readingsUsed,
        String dataQuality,            // OK | NO_DATA (fewer than two readings to compare)
        BigDecimal engineHoursWorked,
        BigDecimal ptoHoursWorked,
        BigDecimal hydraulicHoursWorked,
        int workingDays,
        BigDecimal availableHours,     // working days x target hours per day
        Double utilisationPercent,     // may exceed 100 for overtime or weekend work
        BigDecimal chargeOutValue,     // engine hours worked x operating rate; null without a rate
        BigDecimal litresPerHour       // approximate: litres filled in the period / engine hours worked; null if unknown
) {}