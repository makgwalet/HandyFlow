package za.co.handyflow.platform.fuel.dto;

import java.util.UUID;

/** A person who can be allocated to a load (an active HR employee). */
public record DriverResponse(UUID id, String name, String jobTitle, String phone) {}
