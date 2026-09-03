package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for VatRateProvider — written as part of the VAT
 * consolidation pass (see this class's own Javadoc for the fuller
 * "scattered in 4+ places" finding it closes). No mocking needed: this
 * is a plain value-conversion component with a single constructor
 * argument, so it's exercised directly rather than through Spring's
 * {@code @Value} injection (that wiring itself is a one-line
 * declaration with no branching logic worth a slice test).
 */
class VatRateProviderTest {

    @Test
    @DisplayName("ratePercent() returns exactly the configured value")
    void ratePercentReturnsConfiguredValue() {
        VatRateProvider provider = new VatRateProvider(new BigDecimal("15.00"));
        assertThat(provider.ratePercent()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("rateFraction() converts the configured percentage to a decimal fraction")
    void rateFractionConvertsToDecimal() {
        VatRateProvider provider = new VatRateProvider(new BigDecimal("15.00"));
        assertThat(provider.rateFraction()).isEqualByComparingTo(new BigDecimal("0.1500"));
    }

    @Test
    @DisplayName("rateFraction() reflects a reconfigured rate, not a hardcoded 15%")
    void rateFractionReflectsConfiguredRate() {
        // Regression guard for the exact thing this class exists to fix:
        // the value must come from configuration, not be silently pinned
        // to 15% the way the literals it replaced were.
        VatRateProvider provider = new VatRateProvider(new BigDecimal("14.00"));
        assertThat(provider.ratePercent()).isEqualByComparingTo(new BigDecimal("14.00"));
        assertThat(provider.rateFraction()).isEqualByComparingTo(new BigDecimal("0.1400"));
    }
}
