package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotBlank;

/** status: ACTIVE | RESTING | QUARANTINE | INACTIVE */
public record ChangeAreaStatusRequest(@NotBlank String status) {}
