package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** A position and/or meter value typed in by a person. The time defaults to now. */
public record ManualTelematicsReadingRequest(Instant recordedAt, BigDecimal latitude, BigDecimal longitude, BigDecimal meterValue, String meterUnit, String note) {}
