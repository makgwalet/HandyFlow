package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Where a machine was last seen and what its meter last read, by source. Provider and manual readings are shown apart and neither changes the machine's
 * own meter. {@code freshness} is NEVER, FRESH (within 30 minutes), STALE (within a day) or OFFLINE; this is "last seen", not live.
 */
public record TrackingStatusResponse(UUID assetId, String assetName, String fleetNumber, String meterUnit, BigDecimal machineMeter, int activeDevices,
                                     Source provider, Source manual, BigDecimal providerMeterDifference) {

    /** The newest position and the newest meter value of one source, which may come from different readings. */
    public record Source(String freshness, Instant lastSeen, Instant positionAt, BigDecimal latitude, BigDecimal longitude,
                         Instant meterAt, BigDecimal meterValue, String meterUnit, String providerCode) {}
}
