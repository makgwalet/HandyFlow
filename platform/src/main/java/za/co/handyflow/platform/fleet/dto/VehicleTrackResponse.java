package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VehicleTrackResponse(
        UUID vehicleId,
        String registration,
        Instant from,
        Instant to,
        int totalPoints,              // fixes recorded in the range
        int returnedPoints,           // after thinning to maxPoints (first and last always kept)
        double distanceKm,            // straight-line sum over all fixes — a GPS estimate, not the odometer
        List<TrackPointResponse> points
) {}