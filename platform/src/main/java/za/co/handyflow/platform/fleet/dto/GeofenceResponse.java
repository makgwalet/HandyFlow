package za.co.handyflow.platform.fleet.dto;

import za.co.handyflow.platform.fleet.dto.SaveGeofenceRequest.Point;

import java.util.List;
import java.util.UUID;

public record GeofenceResponse(
        UUID id,
        String name,
        String shape,
        Double centerLatitude,
        Double centerLongitude,
        Integer radiusMeters,
        List<Point> polygon,          // null for a circle
        boolean alertOnEnter,
        boolean alertOnExit,
        Integer speedLimitKph,
        boolean active
) {}