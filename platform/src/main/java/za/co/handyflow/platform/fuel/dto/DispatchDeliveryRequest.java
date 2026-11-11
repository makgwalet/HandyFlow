package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Marks a scheduled delivery as on its way, recording the driver and vehicle. */
public record DispatchDeliveryRequest(
        @NotBlank @Size(max = 120) String driverName,
        @NotBlank @Size(max = 30) String vehicleReg
) {}
