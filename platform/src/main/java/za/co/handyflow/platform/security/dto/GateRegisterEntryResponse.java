package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * idNumber field carries the MASKED value — masking is enforced in
 * GateAccessService's own mapper, never left to the frontend, same
 * posture as GuardService.toResponse()'s own confirmed reasoning
 * ("a frontend-only mask is trivially bypassed by anyone reading the
 * raw network response").
 */
public record GateRegisterEntryResponse(
        UUID id, UUID siteId, UUID accessPointId, String accessPointName,
        String entryType,
        String personName, String idNumber, String phone, String company,
        String hostName, String hostContact, String purpose,
        String vehicleRegistration, String vehicleMakeModel, String driverName,
        String idScanConfidence,
        UUID loggedInByGuardId, String loggedInByGuardName, Instant loggedInAt,
        UUID loggedOutByGuardId, String loggedOutByGuardName, Instant loggedOutAt,
        String status, Instant createdAt
) {}