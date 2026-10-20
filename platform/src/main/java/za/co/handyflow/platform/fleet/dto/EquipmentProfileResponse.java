package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything about a vehicle as a fleet asset. {@code configured=false} means it has no profile and is a plain road vehicle. */
public record EquipmentProfileResponse(
        UUID vehicleId,
        String registration,
        String make,
        String model,
        boolean configured,
        String assetClass,

        BigDecimal engineHours,
        BigDecimal ptoHours,
        BigDecimal hydraulicHours,

        BigDecimal operatingRatePerHour,
        BigDecimal targetHoursPerDay,
        BigDecimal costPerEngineHour,        // all-time service + fuel cost over engine hours; null until it has hours

        Integer serviceIntervalHours,
        BigDecimal lastServiceHours,
        BigDecimal hoursSinceService,
        BigDecimal hoursUntilService,        // negative when overdue
        boolean dueByHours,
        boolean dueForService,               // by hours, OR the vehicle's own km / calendar interval

        String siteName,
        Instant siteUpdatedAt,
        String axleConfiguration,
        String operatorCompetency,
        UUID operatorDriverId,
        String operatorName,

        UUID coupledToVehicleId,
        String coupledToRegistration,
        List<LinkedAsset> trailers,          // trailers this vehicle is hauling
        List<LinkedAttachment> attachments,  // implements currently hitched to it
        String notes
) {
    public record LinkedAsset(UUID vehicleId, String registration) {}

    public record LinkedAttachment(UUID id, String name, String category, Instant attachedAt) {}
}