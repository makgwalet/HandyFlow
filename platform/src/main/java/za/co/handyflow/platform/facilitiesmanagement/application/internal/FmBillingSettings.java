package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmProfile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The billing configuration in force for a tenant, resolved from its FM profile (or defaults when the
 * tenant has no profile yet). Defaults match the behaviour before Chunk 7: no VAT, 30-day terms, AR 1100,
 * revenue 4000.
 * <p>
 * VAT convention: fees, retainers and work-order charges are VAT-EXCLUSIVE; VAT is added on top at
 * {@code vatRate} when the company is VAT-registered.
 */
public record FmBillingSettings(boolean vatRegistered, BigDecimal vatRate, int paymentTermsDays,
                                String arAccountCode, String revenueAccountCode,
                                String vatAccountCode, String bankAccountCode) {

    public static FmBillingSettings defaults() {
        return new FmBillingSettings(false, new BigDecimal("15.00"), 30, "1100", "4000", null, null);
    }

    public static FmBillingSettings resolve(Optional<FmProfile> profile) {
        return profile.map(p -> new FmBillingSettings(p.isVatRegistered(), p.getVatRate(), p.getPaymentTermsDays(),
                        p.getArAccountCode(), p.getRevenueAccountCode(), p.getVatAccountCode(), p.getBankAccountCode()))
                .orElseGet(FmBillingSettings::defaults);
    }

    /** The VAT rate actually applied to new invoices: the configured rate when registered, otherwise zero. */
    public BigDecimal effectiveVatRate() {
        return vatRegistered ? vatRate : BigDecimal.ZERO;
    }

    public BigDecimal vatOn(BigDecimal subtotal) {
        return subtotal.multiply(effectiveVatRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}