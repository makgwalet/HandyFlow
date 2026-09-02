package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

/** status: ACTIVE | SOLD | DECEASED | CULLED | TRANSFERRED_OUT */
public record ChangeAnimalStatusRequest(@NotBlank String status) {}
