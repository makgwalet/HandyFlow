package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ArmouryResponse(
        UUID      id,
        String    firearmSerial,
        String    firearmType,
        String    makeModel,
        String    sapsLicenseNumber,
        LocalDate licenseIssuedAt,
        LocalDate licenseExpiry,
        boolean   licenseExpired,
        UUID      assignedGuardId,
        String    assignedGuardName,
        String    status,
        LocalDate lastServiceAt,
        LocalDate nextServiceDueAt,
        String    notes,
        Instant   createdAt
) {}
