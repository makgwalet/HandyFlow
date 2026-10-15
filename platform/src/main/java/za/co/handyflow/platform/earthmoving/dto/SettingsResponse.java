package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;

/**
 * {@code contractThreshold} is the hire value from which a signed contract is expected; null means contracts are not expected.
 * {@code vatRegistered} and {@code vatRate} are the company's VAT treatment today (set under Settings), which the hire charge form uses as its default.
 */
public record SettingsResponse(BigDecimal contractThreshold, boolean vatRegistered, BigDecimal vatRate) {
    public SettingsResponse(BigDecimal contractThreshold) { this(contractThreshold, true, new BigDecimal("15.00")); }
}
