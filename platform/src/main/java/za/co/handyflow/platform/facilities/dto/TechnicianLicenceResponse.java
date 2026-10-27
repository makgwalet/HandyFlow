package za.co.handyflow.platform.facilities.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code inForce}: valid today (not revoked, not expired). {@code technicianName} is filled in the cross-technician list. */
public record TechnicianLicenceResponse(
        UUID id, UUID technicianId, String technicianName, String licenceType, String reference, String issuedBy,
        LocalDate issueDate, LocalDate expiryDate, Long daysToExpiry, String status, boolean inForce,
        String revokedReason, Instant createdAt
) {}
