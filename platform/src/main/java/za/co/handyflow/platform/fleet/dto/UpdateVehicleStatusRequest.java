package za.co.handyflow.platform.fleet.dto;
import jakarta.validation.constraints.NotBlank;
public record UpdateVehicleStatusRequest(@NotBlank String status) {}