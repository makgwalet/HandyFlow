package za.co.handyflow.platform.invoicing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.application.internal.VatSettingService;
import za.co.handyflow.platform.invoicing.domain.model.VatSetting;
import za.co.handyflow.platform.invoicing.domain.repository.VatSettingRepository;
import za.co.handyflow.platform.invoicing.dto.VatSettingRequest;
import za.co.handyflow.platform.invoicing.dto.VatSettingsOverview;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VatSettingServiceTest {
    VatSettingRepository repo = mock(VatSettingRepository.class);
    TenantFacade tenants = mock(TenantFacade.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    VatSettingService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    List<VatSetting> stored = new ArrayList<>();

    static BigDecimal n(String s) { return new BigDecimal(s); }

    void vatNumber(String number) {
        when(tenants.findTenantDetails(tenant)).thenReturn(Optional.of(new TenantDetails(UUID.randomUUID(), "Zeta", "zeta", number, null, null, null, null,
                null, null, null, null, null, null, null)));
    }

    @BeforeEach
    void setUp() {
        service = new VatSettingService(repo, tenants, new VatRateProvider(n("15.00")), trail);
        vatNumber("4560123456");
        when(repo.findLive(tenant)).thenAnswer(i -> stored.stream().filter(v -> v.getCancelledAt() == null)
                .sorted(Comparator.comparing(VatSetting::getEffectiveFrom).reversed()).toList());
        when(repo.save(any())).thenAnswer(i -> { VatSetting v = i.getArgument(0); if (!stored.contains(v)) stored.add(v); return v; });
        when(repo.findOwned(eq(tenant), any())).thenAnswer(i -> stored.stream().filter(v -> v.getId().equals(i.getArgument(1))).findFirst());
    }

    @Test void untilItIsSetACompanyIsRegisteredAtThePlatformRate() {
        VatSettingsOverview o = service.overview(tenant);
        assertTrue(o.vatRegistered());
        assertEquals(n("15.00"), o.ratePercent());
        assertFalse(o.configured());
        assertTrue(o.vatNumberOnFile());
    }

    @Test void aCompanyCanStopChargingVatFromADateAndItIsAudited() {
        VatSettingsOverview o = service.set(tenant, new VatSettingRequest(false, null, today, "Deregistered with SARS"), actor);
        assertFalse(o.vatRegistered());
        assertEquals(n("0.00"), o.ratePercent());
        assertTrue(o.configured());
        verify(trail).record(eq(tenant), eq("VAT_SETTING_CHANGED"), eq("VAT_SETTING"), any(UUID.class), anyMap(), anyMap());
    }

    @Test void aFutureChangeShowsAsUpcomingAndDoesNotChangeTodaysTreatment() {
        VatSettingsOverview o = service.set(tenant, new VatSettingRequest(false, null, today.plusDays(30), null), actor);
        assertTrue(o.vatRegistered());
        assertEquals(1, o.upcoming().size());
        assertFalse(service.treatment(tenant, today.plusDays(31)).registered());
        assertTrue(service.treatment(tenant, today).registered());
    }

    @Test void switchingVatOnNeedsTheVatNumberOnTheCompanyProfile() {
        vatNumber(" ");
        assertThrows(IllegalStateException.class, () -> service.set(tenant, new VatSettingRequest(true, n("15"), today, null), actor));
        assertTrue(stored.isEmpty());
    }

    @Test void aChangeInThePastIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.set(tenant, new VatSettingRequest(false, null, today.minusDays(1), null), actor));
        assertTrue(stored.isEmpty());
    }

    @Test void aChangeThatHasNotStartedCanBeCancelledButOneThatHasCannot() {
        service.set(tenant, new VatSettingRequest(false, null, today.plusDays(30), null), actor);
        service.set(tenant, new VatSettingRequest(false, null, today, null), actor);
        VatSetting future = stored.stream().filter(v -> v.getEffectiveFrom().isAfter(today)).findFirst().orElseThrow();
        VatSetting started = stored.stream().filter(v -> !v.getEffectiveFrom().isAfter(today)).findFirst().orElseThrow();
        VatSettingsOverview o = service.cancel(tenant, future.getId(), actor);
        assertEquals(0, o.upcoming().size());
        assertThrows(IllegalStateException.class, () -> service.cancel(tenant, started.getId(), actor));
        assertThrows(IllegalStateException.class, () -> service.cancel(tenant, future.getId(), actor));
    }
}
