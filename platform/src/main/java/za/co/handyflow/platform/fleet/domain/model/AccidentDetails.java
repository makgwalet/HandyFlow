package za.co.handyflow.platform.fleet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The editable description of an accident — what happened, to whom, where. Claim and repair have their own updates. */
public record AccidentDetails(
        UUID driverId,
        String driverName,
        UUID tripId,
        Instant occurredAt,
        String location,
        WeatherCondition weather,
        RoadCondition roadCondition,
        String otherVehicleRegistration,
        String otherDriverName,
        String otherDriverContact,
        String otherInsurer,
        String policeCaseNumber,
        String sapsStation,
        String policeReference,
        String description,
        String witnesses,
        int injuriesCount,
        int fatalitiesCount,
        BigDecimal estimatedDamage,
        BigDecimal thirdPartyDamage,
        boolean vehicleDrivable
) {}