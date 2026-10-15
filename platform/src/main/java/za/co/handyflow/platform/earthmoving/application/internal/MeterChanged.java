package za.co.handyflow.platform.earthmoving.application.internal;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** A machine's meter reading was saved. Announced so its maintenance plans are checked without waiting for the nightly run. */
public record MeterChanged(TenantId tenantId, UUID assetId) {}
