package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String name,
        String farmType,
        String registrationNumber,
        String province,
        String region,
        Double gpsLatitude,
        Double gpsLongitude,
        BigDecimal totalHectares,
        UUID managerId,
        String managerName,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String brandMark,
        String earMark,
        String markRegistrationNumber,
        String ownerName,
        String physicalAddress
) {
    public FarmResponse(UUID id, String name, String farmType, String registrationNumber, String province, String region,
                        Double gpsLatitude, Double gpsLongitude, BigDecimal totalHectares, UUID managerId, String managerName,
                        String status, String notes, Instant createdAt, Instant updatedAt,
                        String brandMark, String earMark, String markRegistrationNumber) {
        this(id, name, farmType, registrationNumber, province, region, gpsLatitude, gpsLongitude, totalHectares, managerId,
                managerName, status, notes, createdAt, updatedAt, brandMark, earMark, markRegistrationNumber, null, null);
    }

    public FarmResponse(UUID id, String name, String farmType, String registrationNumber, String province, String region,
                        Double gpsLatitude, Double gpsLongitude, BigDecimal totalHectares, UUID managerId, String managerName,
                        String status, String notes, Instant createdAt, Instant updatedAt) {
        this(id, name, farmType, registrationNumber, province, region, gpsLatitude, gpsLongitude, totalHectares, managerId,
                managerName, status, notes, createdAt, updatedAt, null, null, null, null, null);
    }
}
