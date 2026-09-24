package za.co.handyflow.platform.complianceservices.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClientComplianceRegistrationResponse(
        UUID id, UUID clientId, String authority, String registrationType, String registrationNumber,
        String status, LocalDate issuedDate, LocalDate expiryDate, String notes, boolean expiringSoon, Instant createdAt
) {}
