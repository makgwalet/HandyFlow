package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One fill-up in the fleet-wide fuel log, with the vehicle's registration so the table needs no second lookup. */
public record FleetFuelLogRow(
        UUID id,
        UUID vehicleId,
        String registration,
        LocalDate filledAt,
        BigDecimal litres,
        BigDecimal pricePerLitre,
        BigDecimal totalCost,
        Integer odometerAtFillup,
        String station,
        String receiptRef,
        boolean fullTank
) {}
