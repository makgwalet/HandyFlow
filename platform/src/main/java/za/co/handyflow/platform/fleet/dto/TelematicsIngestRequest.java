package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEventType;
import za.co.handyflow.platform.fleet.domain.model.TelematicsSeverity;

import java.time.Instant;
import java.util.List;

/**
 * The provider-neutral ingest payload: GPS positions and/or native device events, each tagged with
 * the provider's own unit id ({@code deviceId}), which must have been registered as a device.
 * At most 1,000 items per request (positions + events together). A provider adapter's whole job is to
 * translate its own feed into this shape.
 */
public record TelematicsIngestRequest(
        @Size(max = 1000) List<@Valid PositionItem> positions,
        @Size(max = 1000) List<@Valid EventItem> events
) {
    public record PositionItem(
            @NotBlank @Size(max = 100) String deviceId,
            @NotNull Instant recordedAt,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @DecimalMin("0.0") @DecimalMax("400.0") Double speedKph,
            @Min(0) @Max(360) Integer heading,
            @PositiveOrZero Double odometerKm,
            Boolean ignitionOn
    ) {}

    public record EventItem(
            @NotBlank @Size(max = 100) String deviceId,
            @NotNull TelematicsEventType type,
            @NotNull Instant occurredAt,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            Double value,                       // e.g. speed, or g-force, as the provider reports it
            TelematicsSeverity severity,        // defaults: ALERT for DEVICE_UNPLUGGED, otherwise WARNING
            @Size(max = 500) String details
    ) {}
}