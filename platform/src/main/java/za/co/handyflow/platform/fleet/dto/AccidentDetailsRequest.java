package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.RoadCondition;
import za.co.handyflow.platform.fleet.domain.model.WeatherCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What happened. Used to report an accident and to edit the description later. */
public record AccidentDetailsRequest(
        UUID driverId,
        @Size(max = 255) String driverName,          // used when the driver is not on the driver register
        UUID tripId,
        @NotNull Instant occurredAt,
        @Size(max = 500) String location,
        WeatherCondition weather,
        RoadCondition roadCondition,
        @Size(max = 30) String otherVehicleRegistration,
        @Size(max = 255) String otherDriverName,
        @Size(max = 255) String otherDriverContact,
        @Size(max = 255) String otherInsurer,
        @Size(max = 100) String policeCaseNumber,
        @Size(max = 255) String sapsStation,
        @Size(max = 100) String policeReference,
        @NotBlank String description,
        String witnesses,
        @Min(0) Integer injuriesCount,
        @Min(0) Integer fatalitiesCount,
        @DecimalMin("0.00") BigDecimal estimatedDamage,
        @DecimalMin("0.00") BigDecimal thirdPartyDamage,
        Boolean vehicleDrivable                      // defaults to true
) {}
