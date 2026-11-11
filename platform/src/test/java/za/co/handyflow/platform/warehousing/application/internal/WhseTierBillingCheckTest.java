package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseTierBillingCheck.Finding;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WhseTierBillingCheckTest {
    private static final BigDecimal CAT = new BigDecimal("699.00");

    @Test @DisplayName("subscribed with no tier: unrestricted, billed at the catalogue price")
    void noTier() { assertEquals(Finding.NO_TIER, WhseTierBillingCheck.classify(true, false, null, CAT)); }

    @Test @DisplayName("a tier equal to the catalogue price matches, whatever the scale of the numbers")
    void matches() { assertEquals(Finding.MATCHES, WhseTierBillingCheck.classify(true, true, new BigDecimal("699"), CAT)); }

    @Test @DisplayName("a dearer tier is flagged because the platform still bills the catalogue price")
    void differs() { assertEquals(Finding.PRICE_DIFFERS, WhseTierBillingCheck.classify(true, true, new BigDecimal("4999.00"), CAT)); }

    @Test @DisplayName("Enterprise has no price and is billed by quotation")
    void custom() { assertEquals(Finding.CUSTOM_QUOTE, WhseTierBillingCheck.classify(true, true, null, CAT)); }

    @Test @DisplayName("a tier on a tenant without the module is flagged; neither is not reported")
    void withoutModule() {
        assertEquals(Finding.TIER_WITHOUT_MODULE, WhseTierBillingCheck.classify(false, true, new BigDecimal("699"), CAT));
        assertNull(WhseTierBillingCheck.classify(false, false, null, CAT));
    }

    @Test @DisplayName("a missing catalogue price cannot match, so a priced tier is flagged")
    void noCatalogue() { assertEquals(Finding.PRICE_DIFFERS, WhseTierBillingCheck.classify(true, true, new BigDecimal("699"), null)); }
}
