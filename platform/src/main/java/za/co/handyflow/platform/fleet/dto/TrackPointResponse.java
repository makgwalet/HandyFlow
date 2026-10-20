package za.co.handyflow.platform.fleet.dto;

import java.time.Instant;

public record TrackPointResponse(
        Instant recordedAt,
        double latitude,
        double longitude,
        Double speedKph,
        Integer heading,
        Boolean ignitionOn
) {}