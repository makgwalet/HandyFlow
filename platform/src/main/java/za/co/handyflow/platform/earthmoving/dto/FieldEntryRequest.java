package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One entry from the phone. The submission id is made on the phone when the entry is saved and never changes, so a retry cannot repeat it. */
public record FieldEntryRequest(UUID submissionId, String kind, UUID assetId, Instant capturedAt, BigDecimal meterValue, BigDecimal latitude,
                                BigDecimal longitude, String note) {}
