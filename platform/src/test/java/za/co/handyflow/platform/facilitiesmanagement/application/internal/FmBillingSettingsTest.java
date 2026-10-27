package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FmBillingSettingsTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    void withoutAProfileThereIsNoVatAndThirtyDayTerms() {
        FmBillingSettings s = FmBillingSettings.resolve(Optional.empty());
        assertFalse(s.vatRegistered());
        assertEquals(30, s.paymentTermsDays());
        assertEquals("1100", s.arAccountCode());
        assertEquals("4000", s.revenueAccountCode());
        assertEquals(0, s.effectiveVatRate().signum());
        assertEquals(0, s.vatOn(bd("1000.00")).signum());
    }

    @Test
    void aVatRegisteredCompanyAddsVatOnTop() {
        FmProfile profile = FmProfile.create(TenantId.generate(), "Acme FM", null, null, null, null);
        profile.updateBillingSettings(true, "4123456789", bd("15.00"), 14, "1200", "4100", "2300", "1000");
        FmBillingSettings s = FmBillingSettings.resolve(Optional.of(profile));
        assertTrue(s.vatRegistered());
        assertEquals(bd("15.00"), s.effectiveVatRate());
        assertEquals(bd("150.00"), s.vatOn(bd("1000.00")));
        assertEquals(14, s.paymentTermsDays());
        assertEquals("1200", s.arAccountCode());
        assertEquals("2300", s.vatAccountCode());
        assertEquals("1000", s.bankAccountCode());
    }

    @Test
    void vatIsRoundedHalfUpToTheCent() {
        FmProfile profile = FmProfile.create(TenantId.generate(), "Acme FM", null, null, null, null);
        profile.updateBillingSettings(true, "4123456789", bd("15.00"), null, null, null, "2300", null);
        FmBillingSettings s = FmBillingSettings.resolve(Optional.of(profile));
        assertEquals(bd("5.00"), s.vatOn(bd("33.33")));   // 4.9995
        assertEquals(bd("0.15"), s.vatOn(bd("1.00")));
        assertEquals(bd("0.08"), s.vatOn(bd("0.50")));    // 0.075 rounds up
    }

    @Test
    void aNonVatCompanyChargesNoVatEvenIfARateIsConfigured() {
        FmProfile profile = FmProfile.create(TenantId.generate(), "Acme FM", null, null, null, null);
        profile.updateBillingSettings(false, null, bd("15.00"), null, null, null, null, null);
        FmBillingSettings s = FmBillingSettings.resolve(Optional.of(profile));
        assertEquals(0, s.effectiveVatRate().signum());
        assertEquals(0, s.vatOn(bd("1000.00")).signum());
    }
}
