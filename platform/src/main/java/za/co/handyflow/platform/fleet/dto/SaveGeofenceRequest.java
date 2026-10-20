package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.handyflow.platform.fleet.domain.model.GeofenceShape;

import java.util.List;

/**
 * Create (POST) or replace (PUT) a geofence. CIRCLE needs {@code centerLatitude}, {@code centerLongitude}
 * and {@code radiusMeters}; POLYGON needs {@code polygon} (3–200 points, in order around the boundary).
 */
public record SaveGeofenceRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull GeofenceShape shape,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double centerLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double centerLongitude,
        @Min(10) @Max(100000) Integer radiusMeters,
        @Size(max = 200) List<@Valid Point> polygon,
        boolean alertOnEnter,
        boolean alertOnExit,
        @Min(5) @Max(200) Integer speedLimitKph,     // optional lower speed limit inside the zone
        Boolean active                                // defaults to true
) {
    public record Point(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {}
}