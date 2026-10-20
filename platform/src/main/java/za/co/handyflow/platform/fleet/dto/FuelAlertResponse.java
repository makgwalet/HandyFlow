package za.co.handyflow.platform.fleet.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One line of the fuel alert feed: an abnormal consumption segment or a theft / data-quality indicator. */
public record FuelAlertResponse(
        String kind,           // CONSUMPTION | INDICATOR
        String type,           // HIGH_CONSUMPTION | LOW_CONSUMPTION | DATA_SUSPECT, or an indicator type
        String severity,
        UUID vehicleId,
        String registration,
        LocalDate date,
        String message
) {}