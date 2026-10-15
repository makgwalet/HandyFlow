package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;

/** {@code contractThreshold} is the hire value from which a signed contract is expected; null means contracts are not expected. */
public record SettingsResponse(BigDecimal contractThreshold) {}
