package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.CostToServeResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelCostToServeServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID CUST = UUID.randomUUID();

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerSiteRepository sites;
    @Mock FuelSettingsService settingsService;
    @Mock CrmFacade crm;
    @InjectMocks FuelCostToServeService service;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelSettings settings(String driverHr, String perKm, String fixed, String overheadPct, String tripHours) {
        FuelSettings s = FuelSettings.forTenant(T);
        s.update(bd(driverHr), bd(perKm), bd(fixed), bd(overheadPct), bd(tripHours), bd("2"));
        return s;
    }

    /** 1000 L at R22, cost R20 => margin R2000, revenue R22000. */
    private FuelDelivery delivery(UUID site, Instant loaded, Instant delivered, String cost) {
        FuelDelivery d = mock(FuelDelivery.class);
        when(d.getCustomerId()).thenReturn(CUST);
        when(d.getSiteId()).thenReturn(site);
        when(d.getLitresDelivered()).thenReturn(bd("1000"));
        when(d.getPricePerLitre()).thenReturn(bd("22"));
        when(d.getTotalAmount()).thenReturn(bd("22000"));
        when(d.getCostPerLitreAtSale()).thenReturn(cost == null ? null : bd(cost));
        when(d.getLoadedAt()).thenReturn(loaded);
        when(d.getDeliveredAt()).thenReturn(delivered);
        return d;
    }

    private FuelCustomerSite site(UUID id, String km) {
        FuelCustomerSite s = mock(FuelCustomerSite.class);
        when(s.getId()).thenReturn(id);
        when(s.getTenantId()).thenReturn(T);
        when(s.getDistanceKm()).thenReturn(km == null ? null : bd(km));
        return s;
    }

    @BeforeEach
    void stubs() { when(crm.findCustomerById(any(), any())).thenReturn(Optional.empty()); }

    @Test @DisplayName("net margin = margin less driver time, round-trip vehicle cost, fixed cost and overhead share")
    void fullCalculation() {
        UUID siteId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-01T06:00:00Z");
        when(settingsService.current(any())).thenReturn(settings("100", "5", "50", "1", "2"));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(delivery(siteId, t0, t0.plusSeconds(3 * 3600), "20")));
        when(sites.findAllById(any())).thenReturn(List.of(site(siteId, "40")));

        CostToServeResponse r = service.costToServe(T, 30);
        assertEquals(0, bd("2000.00").compareTo(r.margin()));
        assertEquals(0, bd("300.00").compareTo(r.driverCost()));      // 3 h x R100
        assertEquals(0, bd("400.00").compareTo(r.vehicleCost()));     // 80 km round trip x R5
        assertEquals(0, bd("50.00").compareTo(r.fixedCost()));
        assertEquals(0, bd("220.00").compareTo(r.overheadCost()));    // 1% of R22000
        assertEquals(0, bd("1030.00").compareTo(r.netMargin()));
        assertEquals(0, r.deliveriesWithoutDistance());
        assertEquals(0, r.deliveriesWithEstimatedHours());
        assertTrue(r.ratesConfigured());
        assertEquals(0, bd("1.0300").compareTo(r.customers().get(0).netPerLitre()));
    }

    @Test @DisplayName("missing times use the typical trip hours and are flagged; a site with no distance gets no vehicle cost and is flagged")
    void estimatesAreFlagged() {
        UUID noKm = UUID.randomUUID();
        when(settingsService.current(any())).thenReturn(settings("100", "5", "0", "0", "2"));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(delivery(noKm, null, Instant.now(), "20"), delivery(null, null, null, "20")));
        when(sites.findAllById(any())).thenReturn(List.of(site(noKm, null)));

        CostToServeResponse r = service.costToServe(T, 30);
        assertEquals(2, r.deliveriesWithEstimatedHours());
        assertEquals(2, r.deliveriesWithoutDistance());
        assertEquals(0, bd("400.00").compareTo(r.driverCost()));      // 2 x (2 h x R100)
        assertEquals(0, BigDecimal.ZERO.compareTo(r.vehicleCost()));
    }

    @Test @DisplayName("an implausible gap between loading and delivery (over 16 h, or negative) is treated as missing, not billed")
    void implausibleHours() {
        Instant t0 = Instant.parse("2026-11-01T06:00:00Z");
        assertNull(FuelCostToServeService.measuredHours(delivery(null, t0, t0.plusSeconds(20 * 3600), "20")));
        assertNull(FuelCostToServeService.measuredHours(delivery(null, t0, t0.minusSeconds(60), "20")));
        assertEquals(0, bd("1.50").compareTo(FuelCostToServeService.measuredHours(delivery(null, t0, t0.plusSeconds(5400), "20"))));
    }

    @Test @DisplayName("deliveries with no cost-at-sale are counted but left out of every money figure; reversed ones are ignored")
    void uncostedAndReversed() {
        when(settingsService.current(any())).thenReturn(settings("100", "0", "0", "0", "2"));
        FuelDelivery reversed = delivery(null, null, null, "20");
        when(reversed.getReversedAt()).thenReturn(Instant.now());
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(delivery(null, null, null, null), reversed));
        when(sites.findAllById(any())).thenReturn(List.of());
        CostToServeResponse r = service.costToServe(T, 30);
        assertEquals(1, r.deliveriesWithoutCost());
        assertEquals(0, r.deliveries());
        assertTrue(r.customers().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.netMargin()));
    }

    @Test @DisplayName("with no rates set, net margin equals plain margin and the response says rates are not configured")
    void noRates() {
        when(settingsService.current(any())).thenReturn(FuelSettings.forTenant(T));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(delivery(null, null, null, "20")));
        when(sites.findAllById(any())).thenReturn(List.of());
        CostToServeResponse r = service.costToServe(T, 30);
        assertFalse(r.ratesConfigured());
        assertEquals(0, r.margin().compareTo(r.netMargin()));
    }

    @Test @DisplayName("another tenant's site distance is never used")
    void tenantIsolation() {
        UUID siteId = UUID.randomUUID();
        FuelCustomerSite foreign = mock(FuelCustomerSite.class);
        when(foreign.getId()).thenReturn(siteId);
        when(foreign.getTenantId()).thenReturn(TenantId.of(UUID.randomUUID()));
        when(foreign.getDistanceKm()).thenReturn(bd("100"));
        when(settingsService.current(any())).thenReturn(settings("0", "5", "0", "0", "2"));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(delivery(siteId, null, null, "20")));
        when(sites.findAllById(any())).thenReturn(List.of(foreign));
        CostToServeResponse r = service.costToServe(T, 30);
        assertEquals(1, r.deliveriesWithoutDistance());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.vehicleCost()));
    }
}
