// security/dto/CheckpointAdminDtos.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** The checkpoints screen: every checkpoint with how it is used. NFC and BLE identifiers are reported as set or not set only. */
public final class CheckpointAdminDtos {
    private CheckpointAdminDtos() {}

    public record Row(UUID id, UUID siteId, String siteName, String name, String description, boolean active,
                      boolean hasNfc, boolean hasBle, boolean siteRequiresSignedQr,
                      int scans30d, Instant lastScanAt, int activeRoutes) {}

    /** nfcTagUid / bleBeaconId: null = leave as is, blank = remove, a value = replace. */
    public record UpdateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
                                @Size(max = 50) String nfcTagUid, @Size(max = 50) String bleBeaconId, boolean active) {}
}
