package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotNull;

// deviceHardwareId nullable -- a guard could conceivably acknowledge
// from a browser session in an edge case, though the realistic path is
// always the Shield app on a specific device. acknowledgedOffline lets
// the mobile app's own sync queue tell the backend the truth about
// when this genuinely happened, rather than the server timestamp being
// treated as authoritative for an offline-recorded event.
public record AcknowledgePostOrderRequest(
        String deviceHardwareId,
        @NotNull Boolean acknowledgedOffline,
        java.time.Instant acknowledgedAt // when acknowledgedOffline=true, the device's own recorded time; ignored (server "now" used instead) when false
) {}
