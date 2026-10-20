package za.co.handyflow.platform.earthmoving.application.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * What a tracking provider must offer. No provider is chosen yet; when one is, its adapter implements this as a Spring bean and returns readings in
 * the normalised form below. A provider that pushes data instead calls {@link EarthTelematicsService#ingest} directly. Either way the service validates,
 * de-duplicates and stores the readings; an adapter never writes to the database or touches the machine's own meter.
 */
public interface TelematicsProvider {

    /** The code devices are mapped under, e.g. "ACMETRACK". Upper case letters, digits and underscores. */
    String code();

    /** Readings for one device taken after {@code since} (null = as far back as the provider allows). */
    List<ProviderReading> fetchSince(String externalDeviceId, Instant since);

    /** A position, a meter value, or both, at the time the device took it. The unit is HOURS or KILOMETRES and is needed with a meter value. */
    record ProviderReading(Instant recordedAt, BigDecimal latitude, BigDecimal longitude, BigDecimal meterValue, String meterUnit) {}
}
