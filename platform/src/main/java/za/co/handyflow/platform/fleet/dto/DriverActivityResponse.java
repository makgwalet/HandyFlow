package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.util.List;

public record DriverActivityResponse(
        int tripCount,
        int completedTrips,
        int totalKm,
        int businessKm,
        int privateKm,
        BigDecimal fuelLitres,             // litres recorded on trips
        BigDecimal litresPer100Km,         // over trips that recorded fuel; null if none
        int inspectionsCompleted,
        int inspectionsFailed,
        List<String> vehiclesDriven        // registrations
) {}