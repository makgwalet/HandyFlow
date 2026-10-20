package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Map a tracking device to a machine. The provider code and device id are the provider's own. */
public record MapTelematicsDeviceRequest(@NotNull UUID assetId, String providerCode, String externalId, String label) {}
