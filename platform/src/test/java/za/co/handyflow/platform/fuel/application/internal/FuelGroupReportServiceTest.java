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
import za.co.handyflow.platform.fuel.dto.GroupReportDtos.Report;
import za.co.handyflow.platform.fuel.dto.GroupReportDtos.Row;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Phase 6a: the group report puts each load, tank and open variance with the legal entity that owns its depot. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelGroupReportServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelDeliveryVarianceRepository variances;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock FuelLegalEntityRepository legalEntities;
    @InjectMocks FuelGroupReportService service;

    FuelLegalEntity acme, beta;
    FuelDepot acmeDepot, looseDepot;
    FuelTank acmeTank, looseTank;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelTank tank(UUID depotId, String litres, String wac) {
        FuelTank t = mock(FuelTank.class);
        when(t.getId()).thenReturn(UUID.randomUUID()); when(t.getTenantId()).thenReturn(T); when(t.getDepotId()).thenReturn(depotId);
        when(t.getCurrentLitres()).thenReturn(bd(litres)); when(t.getCostPerLitreWac()).thenReturn(wac == null ? null : bd(wac));
        return t;
    }

    private FuelDelivery load(FuelTank t, String litres, String total, String cost, boolean reversed) {
        UUID tankId = t.getId();   // read the other mock first: calling it inside a thenReturn breaks the stubbing
        FuelDelivery d = mock(FuelDelivery.class);
        when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getTenantId()).thenReturn(T); when(d.getTankId()).thenReturn(tankId);
        when(d.getLitresDelivered()).thenReturn(bd(litres)); when(d.getTotalAmount()).thenReturn(bd(total));
        when(d.getCostPerLitreAtSale()).thenReturn(cost == null ? null : bd(cost));
        when(d.getReversedAt()).thenReturn(reversed ? Instant.now() : null);
        return d;
    }

    @BeforeEach
    void setUp() {
        acme = FuelLegalEntity.create(T, "Acme Fuels", null, null, null, null, USER);
        beta = FuelLegalEntity.create(T, "Beta Transport", null, null, null, null, USER);
        acmeDepot = FuelDepot.create(T, "Germiston", "GER", null); acmeDepot.assignLegalEntity(acme.getId());
        looseDepot = FuelDepot.create(T, "Yard", "YRD", null);               // no legal entity
        acmeTank = tank(acmeDepot.getId(), "10000", "20.00");
        looseTank = tank(looseDepot.getId(), "500", null);
        when(legalEntities.findAllActive(T)).thenReturn(List.of(acme, beta));
        when(depots.findAllActive(T)).thenReturn(List.of(acmeDepot, looseDepot));
        when(tanks.findAllActive(T)).thenReturn(List.of(acmeTank, looseTank));
        when(variances.findAllForTenant(T)).thenReturn(List.of());
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of());
    }

    private Row row(Report r, String name) { return r.entities().stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow(); }

    @Test @DisplayName("loads and stock go to the entity that owns the tank's depot; a depot with no entity goes to 'Not assigned', and the total is the sum")
    void groupsByEntity() {
        FuelDelivery a1 = load(acmeTank, "1000", "20000", "18", false), a2 = load(acmeTank, "500", "10000", "18", false), l1 = load(looseTank, "200", "4000", "18", false);
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(List.of(a1, a2, l1), List.of());
        Report r = service.report(T, 30, true);
        Row acmeRow = row(r, "Acme Fuels");
        assertEquals(2, acmeRow.deliveries()); assertEquals(0, bd("1500.00").compareTo(acmeRow.litres())); assertEquals(0, bd("30000.00").compareTo(acmeRow.revenue()));
        assertEquals(0, bd("3000.00").compareTo(acmeRow.margin()));              // 30000 - 1500 x 18
        assertEquals(0, bd("10000.00").compareTo(acmeRow.stockLitres())); assertEquals(0, bd("200000.00").compareTo(acmeRow.stockValue()));
        Row un = row(r, "Not assigned to a legal entity");
        assertTrue(un.unassigned()); assertEquals(1, un.deliveries()); assertEquals(1, un.tanksWithoutCost());
        assertEquals(3, r.total().deliveries()); assertEquals(0, bd("1700.00").compareTo(r.total().litres()));
        assertEquals(0, bd("0.00").compareTo(row(r, "Beta Transport").litres()));
    }

    @Test @DisplayName("without margin permission no money is returned, but litres and counts still are")
    void moneyHidden() {
        List<FuelDelivery> now = List.of(load(acmeTank, "1000", "20000", "18", false));   // built first: a mock must not be created while another is being stubbed
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(now, List.of());
        Report r = service.report(T, 30, false);
        assertFalse(r.moneyShown());
        Row a = row(r, "Acme Fuels");
        assertNull(a.revenue()); assertNull(a.margin()); assertNull(a.stockValue()); assertNull(a.previousRevenue()); assertNull(r.total().revenue());
        assertEquals(0, bd("1000.00").compareTo(a.litres())); assertEquals(0, bd("10000.00").compareTo(a.stockLitres()));
    }

    @Test @DisplayName("the previous period of equal length is counted separately, and the dates abut")
    void previousPeriod() {
        List<FuelDelivery> thisPeriod = List.of(load(acmeTank, "1000", "20000", "18", false)), lastPeriod = List.of(load(acmeTank, "400", "8000", "18", false));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(thisPeriod, lastPeriod);
        Report r = service.report(T, 7, true);
        Row a = row(r, "Acme Fuels");
        assertEquals(0, bd("1000.00").compareTo(a.litres())); assertEquals(0, bd("400.00").compareTo(a.previousLitres())); assertEquals(1, a.previousDeliveries());
        assertEquals(0, bd("800.00").compareTo(a.previousMargin()));
        assertEquals(r.from().minusDays(1), r.previousTo()); assertEquals(r.previousTo().minusDays(6), r.previousFrom());
        assertEquals(6, java.time.temporal.ChronoUnit.DAYS.between(r.from(), r.to()));
    }

    @Test @DisplayName("a load with no cost at sale counts in revenue and litres but not margin, and is counted; a reversed load is left out and counted")
    void costAndReversals() {
        List<FuelDelivery> mixed = List.of(
                load(acmeTank, "1000", "20000", null, false), load(acmeTank, "100", "2000", "18", false), load(acmeTank, "50", "1000", "18", true));
        when(deliveries.findDeliveredBetween(any(), any(), any())).thenReturn(mixed, List.of());
        Report r = service.report(T, 30, true);
        Row a = row(r, "Acme Fuels");
        assertEquals(2, a.deliveries()); assertEquals(1, a.deliveriesWithoutCost()); assertEquals(0, bd("22000.00").compareTo(a.revenue()));
        assertEquals(0, bd("200.00").compareTo(a.margin()));
        assertEquals(1, r.deliveriesLeftOut());
    }

    @Test @DisplayName("only OPEN variances are counted, against the entity of the load's tank; one on a load that can no longer be found is 'Not assigned'")
    void openVariances() {
        FuelDelivery d = load(acmeTank, "1000", "20000", "18", false);
        FuelDeliveryVariance open = mock(FuelDeliveryVariance.class), closed = mock(FuelDeliveryVariance.class), orphan = mock(FuelDeliveryVariance.class);
        when(open.getStatus()).thenReturn("OPEN"); when(open.getDeliveryId()).thenReturn(d.getId());
        when(closed.getStatus()).thenReturn("REVIEWED"); when(closed.getDeliveryId()).thenReturn(d.getId());
        when(orphan.getStatus()).thenReturn("OPEN"); when(orphan.getDeliveryId()).thenReturn(UUID.randomUUID());
        when(variances.findAllForTenant(T)).thenReturn(List.of(open, closed, orphan));
        when(deliveries.findAllByIds(any(), any())).thenReturn(List.of(d));
        Report r = service.report(T, 30, true);
        assertEquals(1, row(r, "Acme Fuels").openVariances());
        assertEquals(1, row(r, "Not assigned to a legal entity").openVariances());
        assertEquals(2, r.total().openVariances());
    }

    @Test @DisplayName("an empty group gives zero rows of figures and no 'Not assigned' row when nothing is unassigned")
    void emptyGroup() {
        when(tanks.findAllActive(T)).thenReturn(List.of(acmeTank));
        Report r = service.report(T, 30, true);
        assertEquals(2, r.entities().size());
        assertEquals(0, r.total().deliveries());
    }

    @Test @DisplayName("the period is clamped to 1..366 days")
    void clamp() {
        Report big = service.report(T, 5000, true);
        assertEquals(365, java.time.temporal.ChronoUnit.DAYS.between(big.from(), big.to()));
        Report small = service.report(T, 0, true);
        assertEquals(small.from(), small.to());
    }
}
