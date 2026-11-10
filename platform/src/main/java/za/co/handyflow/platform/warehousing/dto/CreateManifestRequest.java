package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.Size;

/** A dispatch manifest. Vehicle and driver may be added later but are required before loading starts. */
public record CreateManifestRequest(@Size(max = 120) String carrier, @Size(max = 30) String vehicleRegistration,
                                    @Size(max = 120) String driverName, @Size(max = 300) String notes) {}
