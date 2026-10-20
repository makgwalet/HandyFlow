package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Register (POST) or edit (PUT, full replace) a device. {@code externalId} is the provider's unit id and must be unique. */
public record SaveTelematicsDeviceRequest(
        @NotBlank @Size(max = 100) String externalId,
        @Size(max = 50) String provider,          // e.g. CARTRACK, CTRACK, MIX, TELTONIKA; defaults to GENERIC
        @NotNull UUID vehicleId,
        @Size(max = 150) String name,
        Boolean active                             // defaults to true
) {}