package za.co.handyflow.platform.compliancetender.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ComplianceRegistrationResponse(
        UUID id, String authority, String registrationType, String registrationNumber, String status,
        LocalDate issuedDate, LocalDate expiryDate, String notes, boolean expiringSoon, Instant createdAt
) {}
