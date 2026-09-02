package za.co.handyflow.platform.facilities.dto;

import java.util.UUID;

/** Assign to either a technician or a vendor — the service enforces at least one is present. */
public record AssignWorkOrderRequest(UUID technicianId, String technicianName, UUID vendorId, String vendorName) {}
