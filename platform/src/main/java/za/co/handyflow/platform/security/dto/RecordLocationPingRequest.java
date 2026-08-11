// security/dto/RecordLocationPingRequest.java
package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * No guardId or sessionId here on purpose -- both are resolved server-side
 * from the DeviceSession the ping is posted against (path variable), same
 * "never trust a client-supplied identity claim" posture as
 * CheckpointScanController's guardId resolution (bug #13 fix).
 */
public record RecordLocationPingRequest(
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        BigDecimal accuracyMetres
) {}