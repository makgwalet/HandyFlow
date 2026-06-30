package za.co.handyflow.platform.security.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateDispatchRequest(
        @NotBlank String dispatchedUnitType,   // ARMED_RESPONSE | GUARD | POLICE | OTHER
        UUID dispatchedGuardId                  // nullable — e.g. for POLICE
) {}
