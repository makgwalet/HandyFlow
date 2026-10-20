package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelematicsReadingResponse(UUID id, String source, String providerCode, Instant recordedAt, Instant receivedAt, BigDecimal latitude,
                                        BigDecimal longitude, BigDecimal meterValue, String meterUnit, UUID enteredBy, String note) {}
