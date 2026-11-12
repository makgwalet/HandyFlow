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
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.RouteCostDtos.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelRouteCostServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelRouteRepository routes;
    @Mock FuelRouteCostEntryRepository entries;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelCustomerSiteRepository sites;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock FuelSettingsService settingsService;
    @Mock HrFacade hr;
    @InjectMocks FuelRouteCostService service;

    FuelRoute route; FuelTanker tanker; FuelSettings settings; EmployeeSummary driver;
    final List<FuelRouteCostEntry> actuals = new ArrayList<>();
    final UUID depotA = UUID.randomUUID(), depotB = UUID.randomUUID();
    FuelTank tankA, tankB;

    @BeforeEach
    void setUp() {
        tanker = FuelTanker.create(T, "GP 12 AB", null);
        driver = new EmployeeSummary(UUID.randomUUID(), "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE");
        route = FuelRoute.create(T, DAY, "Morning run", tanker.getId(), driver.id(), BY);
        settings = FuelSettings.forTenant(T);
        settings.update(bd("100"), bd("5"), BigDecimal.ZERO, BigDecimal.ZERO, bd("6"), null); // R100/h, R5/km, 6 h typical
        tankA = mock(FuelTank.class); when(tankA.getId()).thenReturn(UUID.randomUUID()); when(tankA.getDepotId()).thenReturn(depotA);
        tankB = mock(FuelTank.class); when(tankB.getId()).thenReturn(UUID.randomUUID()); when(tankB.getDepotId()).thenReturn(depotB);
        FuelDepot dA = mock(FuelDepot.class); when(dA.getId()).thenReturn(depotA); when(dA.getName()).thenReturn("North depot");
        FuelDepot dB = mock(FuelDepot.class); when(dB.getId()).thenReturn(depotB); when(dB.getName()).thenReturn("South depot");
        when(tanks.findAllActive(T)).thenReturn(List.of(tankA, tankB));
        when(depots.findAllActive(T)).thenReturn(List.of(dA, dB));
        when(tankers.findAllActive(T)).thenReturn(List.of(tanker));
        when(settingsService.current(T)).thenReturn(settings);
        when(hr.findEmployeeById(T, driver.id())).thenReturn(Optional.of(driver));
        when(routes.findOne(eq(T), eq(route.getId()))).thenReturn(Optional.of(route));
        when(routes.findBetween(eq(T), any(), any())).thenReturn(List.of(route));
        when(entries.findActiveByRoutes(eq(T), any())).thenAnswer(i -> actuals.stream().filter(FuelRouteCostEntry::isActive).toList());
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private FuelCustomerSite site(UUID id, String km) {
        FuelCustomerSite s = mock(FuelCustomerSite.class);
        when(s.getId()).thenReturn(id); when(s.getDistanceKm()).thenReturn(km == null ? null : bd(km)); when(s.getTenantId()).thenReturn(T);
        return s;
    }

    /** A delivered stop from the given tank to a site, optionally. */
    private FuelDelivery delivered(FuelTank tank, UUID siteId, String litres) {
        FuelDelivery d = FuelDelivery.create(T, tank.getId(), UUID.randomUUID(), Map.of(), "DIESEL", bd(litres), bd("22"), Instant.now(), null, null);
        if (siteId != null) d.assignSite(siteId);
        d.joinRoute(route.getId(), 1);
        d.dispatch("D", "V");
        d.complete(bd(litres), "R", null, null, null, false, null, null);
        return d;
    }

    private void withStops(FuelDelivery... ds) { when(deliveries.findByRoutes(eq(T), any())).thenReturn(List.of(ds)); }

    private FuelRouteCostEntry entry(String category, String amount) { FuelRouteCostEntry e = FuelRouteCostEntry.create(T, route.getId(), category, bd(amount), null, null, "x", BY); actuals.add(e); return e; }

    @Test @DisplayName("the estimate is a round trip to the farthest stop at the tenant's per-km rate plus typical hours at the hourly rate")
    void estimate() {
        UUID near = UUID.randomUUID(), far = UUID.randomUUID();
        List<FuelCustomerSite> found = List.of(site(near, "40"), site(far, "120"));
        when(sites.findAllById(any())).thenReturn(found);
        withStops(delivered(tankA, near, "3000"), delivered(tankA, far, "5000"));
        RouteCostRow r = service.report(T, DAY, DAY).routes().get(0);
        assertEquals(0, bd("1200.00").compareTo(r.estimateVehicle())); // 2 x 120 x 5
        assertEquals(0, bd("600.00").compareTo(r.estimateDriver()));   // 6 x 100
        assertEquals(0, bd("1800.00").compareTo(r.estimateTotal()));
        assertEquals("COMPLETED", r.state()); assertEquals(0, bd("8000").compareTo(r.litresDelivered()));
        assertNull(r.variance()); assertFalse(r.reconciled()); assertTrue(r.flags().contains("NO_ACTUALS"));
    }

    @Test @DisplayName("variance compares vehicle and driver only where both an estimate and an actual exist; tolls and other are not compared")
    void varianceOnComparableCategories() {
        UUID s1 = UUID.randomUUID();
        List<FuelCustomerSite> found = List.of(site(s1, "100"));
        when(sites.findAllById(any())).thenReturn(found);
        withStops(delivered(tankA, s1, "4000")); // estimate vehicle 1000, driver 600
        entry("VEHICLE", "1300"); entry("TOLLS", "250"); entry("OTHER", "50");
        RouteCostRow r = service.report(T, DAY, DAY).routes().get(0);
        assertEquals(0, bd("300.00").compareTo(r.variance()));        // 1300 - 1000, driver has no actual so is not compared
        assertEquals(0, bd("30.0").compareTo(r.variancePct()));        // 300 / 1000
        assertEquals(0, bd("1600").compareTo(r.actualTotal()));        // 1300 + 250 + 50
        assertTrue(r.reconciled());
        assertEquals(0, bd("0.4000").compareTo(r.actualCostPerLitre())); // 1600 / 4000
        // add a driver actual: now both categories compare
        entry("DRIVER", "500");
        RouteCostRow both = service.report(T, DAY, DAY).routes().get(0);
        assertEquals(0, bd("200.00").compareTo(both.variance()));      // (1300+500) - (1000+600)
    }

    @Test @DisplayName("a missing distance or unset rates leave that estimate empty and say why; nothing is guessed")
    void missingInputsAreFlagged() {
        when(sites.findAllById(any())).thenReturn(List.of());
        withStops(delivered(tankA, null, "1000"));
        RouteCostRow r = service.report(T, DAY, DAY).routes().get(0);
        assertNull(r.estimateVehicle()); assertEquals(0, bd("600.00").compareTo(r.estimateDriver()));
        assertTrue(r.flags().contains("NO_DISTANCE"));
        settings.update(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, bd("6"), null);
        RouteCostReport rep = service.report(T, DAY, DAY);
        assertFalse(rep.ratesSet()); assertNull(rep.routes().get(0).estimateTotal()); assertTrue(rep.routes().get(0).flags().contains("RATES_NOT_SET"));
    }

    @Test @DisplayName("a voided entry is not counted, and a route with nothing delivered or a cancelled route is not reported")
    void voidedAndUndelivered() {
        when(sites.findAllById(any())).thenReturn(List.of());
        withStops(delivered(tankA, null, "1000"));
        FuelRouteCostEntry e = entry("VEHICLE", "999");
        e.voidEntry("typed wrongly", BY);
        assertNull(service.report(T, DAY, DAY).routes().get(0).actualVehicle());
        FuelDelivery notDone = FuelDelivery.create(T, tankA.getId(), UUID.randomUUID(), Map.of(), "DIESEL", bd("1000"), bd("22"), Instant.now(), null, null);
        notDone.joinRoute(route.getId(), 1);
        withStops(notDone);
        assertTrue(service.report(T, DAY, DAY).routes().isEmpty());
        withStops(delivered(tankA, null, "1000"));
        route.cancel("Customer closed");
        assertTrue(service.report(T, DAY, DAY).routes().isEmpty());
    }

    @Test @DisplayName("rollups by tanker count unreconciled routes and add up the comparable figures")
    void tankerRollup() {
        UUID s1 = UUID.randomUUID();
        List<FuelCustomerSite> found = List.of(site(s1, "100"));
        when(sites.findAllById(any())).thenReturn(found);
        withStops(delivered(tankA, s1, "4000"));
        RouteCostReport none = service.report(T, DAY, DAY);
        assertEquals(1, none.unreconciledRoutes());
        assertEquals(0, none.byTanker().get(0).reconciledRoutes());
        entry("VEHICLE", "1100"); entry("DRIVER", "700");
        RouteCostReport rep = service.report(T, DAY, DAY);
        Rollup t = rep.byTanker().get(0);
        assertEquals("GP 12 AB", t.label()); assertEquals(1, t.reconciledRoutes()); assertEquals(0, rep.unreconciledRoutes());
        assertEquals(0, bd("1600.00").compareTo(t.estimate())); assertEquals(0, bd("1800.00").compareTo(t.comparableActual())); assertEquals(0, bd("200.00").compareTo(t.variance()));
    }

    @Test @DisplayName("by depot, a route's figures are shared by litres delivered from each depot's tanks")
    void depotRollupSharesByLitres() {
        UUID s1 = UUID.randomUUID();
        List<FuelCustomerSite> found = List.of(site(s1, "100"));
        when(sites.findAllById(any())).thenReturn(found);
        withStops(delivered(tankA, s1, "3000"), delivered(tankB, s1, "1000")); // 75% / 25%
        entry("VEHICLE", "1000"); entry("DRIVER", "600"); entry("TOLLS", "400");
        List<Rollup> d = service.report(T, DAY, DAY).byDepot();
        assertEquals(2, d.size());
        Rollup north = d.stream().filter(x -> x.label().equals("North depot")).findFirst().orElseThrow();
        Rollup south = d.stream().filter(x -> x.label().equals("South depot")).findFirst().orElseThrow();
        assertEquals(0, bd("1500.00").compareTo(north.actualTotal())); // 2000 x 75%
        assertEquals(0, bd("500.00").compareTo(south.actualTotal()));
        assertEquals(0, bd("3000.00").compareTo(north.litres()));
        assertEquals(0, bd("1200.00").compareTo(north.estimate()));       // compared estimate 1000 + 600 = 1600, 75% of it
        assertEquals(0, bd("400.00").compareTo(south.estimate()));
        assertEquals(0, bd("0.00").compareTo(north.variance()));          // compared actual 1000 + 600 matches the estimate
    }

    @Test @DisplayName("recording a cost needs a valid type and amount; Other needs a note; a cancelled route takes none")
    void addRules() {
        assertEquals("VEHICLE", service.add(T, route.getId(), new CostEntryRequest("VEHICLE", bd("10"), bd("20"), "km", null), BY).category());
        assertThrows(IllegalArgumentException.class, () -> service.add(T, route.getId(), new CostEntryRequest("FUEL", bd("10"), null, null, null), BY));
        assertThrows(IllegalArgumentException.class, () -> service.add(T, route.getId(), new CostEntryRequest("VEHICLE", bd("-1"), null, null, null), BY));
        assertThrows(IllegalArgumentException.class, () -> service.add(T, route.getId(), new CostEntryRequest("OTHER", bd("10"), null, null, " "), BY));
        assertThrows(ResourceNotFoundException.class, () -> service.add(T, UUID.randomUUID(), new CostEntryRequest("VEHICLE", bd("10"), null, null, null), BY));
        route.cancel("Customer closed");
        assertThrows(IllegalStateException.class, () -> service.add(T, route.getId(), new CostEntryRequest("VEHICLE", bd("10"), null, null, null), BY));
    }

    @Test @DisplayName("voiding needs a reason and happens once")
    void voidRules() {
        FuelRouteCostEntry e = FuelRouteCostEntry.create(T, route.getId(), "TOLLS", bd("10"), null, null, null, BY);
        when(entries.findByIdForUpdate(eq(T), eq(e.getId()))).thenReturn(Optional.of(e));
        assertThrows(IllegalArgumentException.class, () -> service.voidEntry(T, e.getId(), " ", BY));
        assertEquals("VOIDED", service.voidEntry(T, e.getId(), "Duplicate", BY).status());
        assertThrows(IllegalStateException.class, () -> service.voidEntry(T, e.getId(), "Again", BY));
        assertThrows(ResourceNotFoundException.class, () -> service.voidEntry(T, UUID.randomUUID(), "x", BY));
    }

    @Test @DisplayName("a bad period is refused")
    void periodChecks() {
        assertThrows(IllegalArgumentException.class, () -> service.report(T, DAY.plusDays(1), DAY));
        assertThrows(IllegalArgumentException.class, () -> service.report(T, DAY.minusDays(400), DAY));
    }
}
