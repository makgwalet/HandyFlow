package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;

/** Send null to switch the contract expectation off. */
public record SaveSettingsRequest(BigDecimal contractThreshold) {}
