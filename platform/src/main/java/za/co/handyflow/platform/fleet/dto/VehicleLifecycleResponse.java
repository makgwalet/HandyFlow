package za.co.handyflow.platform.fleet.dto;

import java.util.List;
import java.util.UUID;

public record VehicleLifecycleResponse(
        UUID vehicleId,
        String registration,
        String lifecycleStatus,
        String operationalStatus,
        List<LifecycleEventResponse> events      // newest first
) {}