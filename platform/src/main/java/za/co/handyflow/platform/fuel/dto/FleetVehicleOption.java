package za.co.handyflow.platform.fuel.dto;

import java.util.UUID;

/** A Fleet vehicle offered when linking a tanker. linkedTankerRegistration is set when a tanker already uses it. */
public record FleetVehicleOption(UUID id, String registration, String label, String linkedTankerRegistration) {}
