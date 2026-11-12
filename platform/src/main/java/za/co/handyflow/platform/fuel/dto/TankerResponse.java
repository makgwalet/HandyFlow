package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TankerResponse(UUID id, String registration, String name, boolean active, BigDecimal totalCapacity,
                             List<CompartmentResponse> compartments, long openLoads,
                             UUID fleetVehicleId, String fleetVehicleLabel, UUID fleetSuggestionId, String fleetSuggestionLabel) {
    public TankerResponse(UUID id, String registration, String name, boolean active, BigDecimal totalCapacity,
                          List<CompartmentResponse> compartments, long openLoads) {
        this(id, registration, name, active, totalCapacity, compartments, openLoads, null, null, null, null);
    }
    public record CompartmentResponse(UUID id, int number, BigDecimal capacityLitres) {}
}
