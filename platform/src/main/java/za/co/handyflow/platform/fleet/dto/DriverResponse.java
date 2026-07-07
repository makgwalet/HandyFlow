package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        String email,
        String idNumber,
        String licenseNumber,
        String licenseCode,
        LocalDate licenseExpiry,
        boolean licenseExpiringSoon,
        boolean prdpRequired,
        String prdpNumber,
        String prdpCategory,
        LocalDate prdpExpiry,
        boolean prdpExpiringSoon,
        String status,
        String notes,
        Instant createdAt
) {}
