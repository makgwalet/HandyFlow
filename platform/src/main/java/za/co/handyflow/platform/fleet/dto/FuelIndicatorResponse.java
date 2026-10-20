package za.co.handyflow.platform.fleet.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A possible fuel-theft or data-quality flag on a fill-up. An indicator is a reason to look, not proof. */
public record FuelIndicatorResponse(
        String type,           // OVERFILL | SAME_DAY_MULTIPLE | DUPLICATE_RECEIPT | NO_DISTANCE | ODOMETER_REGRESSION
        String severity,       // INFO | WARNING | ALERT
        UUID fillupId,
        UUID vehicleId,
        String registration,
        LocalDate date,
        String message
) {}