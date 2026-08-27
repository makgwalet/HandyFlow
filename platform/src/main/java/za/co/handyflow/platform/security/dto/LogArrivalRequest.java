package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * deviceHardwareId resolves the logging guard's identity server-side via
 * DeviceSessionService.resolveGuardId() — the same open-session-based
 * resolution CheckpointScanController's own scan endpoint uses, not a
 * client-supplied guard field.
 * <p>
 * idScanConfidence is nullable — no ID scan attempted (e.g. a delivery
 * entry with no personal ID captured) is a valid, honest state, not an
 * error.
 */
public record LogArrivalRequest(
        @NotNull UUID accessPointId,
        @NotBlank String deviceHardwareId,

        @NotBlank String entryType,   // VISITOR | CONTRACTOR | DELIVERY | STAFF_VEHICLE | OTHER
        @NotBlank String personName,
        String idNumber,
        String phone,
        String company,

        String hostName,
        String hostContact,
        String purpose,

        String vehicleRegistration,
        String vehicleMakeModel,
        String driverName,

        String idScanConfidence   // BARCODE_DECODED | OCR_EXTRACTED | MANUAL_ENTRY | null
) {}