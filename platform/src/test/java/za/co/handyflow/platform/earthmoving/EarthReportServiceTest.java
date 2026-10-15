package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.ProfitabilityResponse;
import za.co.handyflow.platform.earthmoving.dto.UtilisationResponse;
import za.co.handyflow.platform.earthmoving.dto.MachineUtilisationResponse;
import za.co.handyflow.platform.earthmoving.dto.CustomerRevenueResponse;
import za.co.handyflow.platform.earthmoving.dto.DowntimeResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthReportServiceTest {
    EarthHireChargeRepository charges = mock(EarthHireChargeRepository.class);
    MaintenanceRepository maintenance = mock(MaintenanceRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    OperatorLogRepository logs = mock(OperatorLogRepository.class);
    EarthStatusEventRepository events = mock(EarthStatusEventRepository.class);
    za.co.handyflow.platform.crm.CrmFacade crm = mock(za.co.handyflow.platform.crm.CrmFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthReportService service;
    TenantId tenant = TenantId.generate();
    EarthAsset d9, loader;
    UUID customer = UUID.randomUUID();
    static final LocalDate FROM = LocalDate.of(2026, 1, 1), TO = LocalDate.of(2026, 12, 31);
    static BigDecimal n(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        service = new EarthReportService(charges, maintenance, assets, logs, events, crm, access);
        d9 = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null, "OWN", null, null, null, n("1000"), null, null);
        loader = EarthAsset.create(tenant, "Volvo L120", "L-02", "LOADER", null, null, null, null, null, "OWN", null, null, null, n("500"), null, null);
        when(assets.findActiveById(tenant, d9.getId())).thenReturn(Optional.of(d9));
        when(assets.findActiveById(tenant, loader.getId())).thenReturn(Optional.of(loader));
        when(charges.findRevenueBetween(any(), any(), any())).thenReturn(List.of());
        when(maintenance.findCostedBetween(any(), any(), any())).thenReturn(List.of());
        granted.add("EARTHMOVING_ADMIN");
    }

    EarthHireCharge charge(EarthAsset a, String amount, boolean invoiced) {
        EarthHireCharge c = EarthHireCharge.create(tenant, UUID.randomUUID(), a.getId(), customer, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1),
                "DAILY", BigDecimal.ONE, n(amount), null, "Hire", null);
        if (invoiced) c.markInvoiced(UUID.randomUUID(), "INV-1");
        return c;
    }
    MaintenanceRecord service(EarthAsset a, String cost) {
        MaintenanceRecord m = mock(MaintenanceRecord.class);
        when(m.getAssetId()).thenReturn(a.getId());
        when(m.getCost()).thenReturn(n(cost));
        return m;
    }

    @Test void revenueAndCostAreAddedPerMachineAndMarginWorkedOut() {
        when(charges.findRevenueBetween(any(), any(), any())).thenReturn(List.of(charge(d9, "1000", true), charge(d9, "500", false), charge(loader, "200", false)));
        when(maintenance.findCostedBetween(any(), any(), any())).thenReturn(List.of(service(d9, "300"), service(loader, "250")));
        ProfitabilityResponse r = service.profitability(tenant, FROM, TO);
        assertEquals(2, r.machines().size());
        ProfitabilityResponse.Row first = r.machines().get(0);
        assertEquals("D9-001 CAT D9", first.machine());
        assertEquals(n("1500.00"), first.revenue());
        assertEquals(n("1000.00"), first.invoiced());
        assertEquals(n("500.00"), first.notInvoiced());
        assertEquals(n("300"), first.maintenanceCost());
        assertEquals(n("1200.00"), first.margin());
        assertEquals(n("80.0"), first.marginPercent());
        assertEquals(n("-50.00"), r.machines().get(1).margin());
        assertEquals(n("1700.00"), r.totals().revenue());
        assertEquals(n("550"), r.totals().maintenanceCost());
    }

    @Test void aMachineWithOnlyCostsShowsALossAndNoPercentage() {
        when(maintenance.findCostedBetween(any(), any(), any())).thenReturn(List.of(service(d9, "300")));
        ProfitabilityResponse.Row row = service.profitability(tenant, FROM, TO).machines().get(0);
        assertEquals(n("-300"), row.margin());
        assertNull(row.marginPercent());
    }

    @Test void anEmptyPeriodGivesZerosNotAnError() {
        ProfitabilityResponse r = service.profitability(tenant, FROM, TO);
        assertTrue(r.machines().isEmpty());
        assertEquals(0, r.totals().revenue().signum());
    }

    @Test void itNeedsAdministratorAuthorityAndASensiblePeriod() {
        assertThrows(IllegalArgumentException.class, () -> service.profitability(tenant, TO, FROM));
        granted.clear();
        granted.add("EARTHMOVING_READ");
        assertThrows(HandyFlowException.class, () -> service.profitability(tenant, FROM, TO));
    }

    @Test void utilisationCountsOnlyHourMeteredMachinesAndNeedsReadAuthority() {
        loader.changeMeter(za.co.handyflow.platform.earthmoving.domain.model.MeterRules.KILOMETRES, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
        when(assets.findAllActive(eq(tenant), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(d9, loader)));
        java.time.Instant now = java.time.Instant.now();
        OperatorLog a = OperatorLog.create(tenant, d9.getId(), null, "A", null, now, n("10"));
        a.complete(now.plusSeconds(3600), n("18"), null, null);
        OperatorLog b = OperatorLog.create(tenant, loader.getId(), null, "B", null, now, n("100"));
        b.complete(now.plusSeconds(3600), n("160"), null, null);
        when(logs.findCompletedBetween(eq(tenant), any(), any())).thenReturn(List.of(a, b));
        granted.clear();
        granted.add("EARTHMOVING_READ");
        UtilisationResponse r = service.utilisation(tenant, 30);
        assertEquals(30, r.perDay().size());
        assertEquals(1, r.machines());
        assertEquals(0, n("8").compareTo(r.totalHours()));
        granted.clear();
        assertThrows(HandyFlowException.class, () -> service.utilisation(tenant, 30));
        granted.add("EARTHMOVING_READ");
        assertThrows(IllegalArgumentException.class, () -> service.utilisation(tenant, 3));
    }

    @Test void machineUtilisationListsEveryHourMachineAndCountsDaysWorked() {
        when(assets.findAllActive(eq(tenant), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(d9, loader)));
        java.time.Instant t = FROM.plusDays(10).atTime(7, 0).atZone(java.time.ZoneId.of("Africa/Johannesburg")).toInstant();
        OperatorLog a = OperatorLog.create(tenant, d9.getId(), null, "A", null, t, n("10"));
        a.complete(t.plusSeconds(3600), n("18"), null, null);
        OperatorLog b = OperatorLog.create(tenant, d9.getId(), null, "A", null, t.plusSeconds(7200), n("18"));
        b.complete(t.plusSeconds(9000), n("20"), null, null);
        when(logs.findCompletedBetween(eq(tenant), any(), any())).thenReturn(List.of(a, b));
        granted.clear(); granted.add("EARTHMOVING_READ");
        MachineUtilisationResponse r = service.machineUtilisation(tenant, FROM, FROM.plusDays(9 + 21));
        assertEquals(2, r.machines().size());
        MachineUtilisationResponse.Row top = r.machines().get(0);
        assertEquals(d9.getId(), top.assetId());
        assertEquals(2, top.shifts());
        assertEquals(1, top.daysWorked());
        assertEquals(0, n("10.0").compareTo(top.hours()));
        assertEquals(0, r.machines().get(1).shifts());
    }

    @Test void customerRevenueGroupsByCustomerAndIsAdministratorOnly() {
        UUID other = UUID.randomUUID();
        EarthHireCharge c1 = charge(customer, "800.00"), c2 = charge(customer, "200.00"), c3 = charge(other, "1000.00");
        when(charges.findRevenueBetween(any(), any(), any())).thenReturn(List.of(c1, c2, c3));
        when(crm.findCustomerById(tenant, customer)).thenReturn(Optional.of(new za.co.handyflow.platform.crm.CustomerSummary(customer, "Acme Civils", null, null, null, null, null, null)));
        when(crm.findCustomerById(tenant, other)).thenReturn(Optional.empty());
        CustomerRevenueResponse r = service.customerRevenue(tenant, FROM, TO);
        assertEquals(2, r.customers().size());
        assertEquals(0, n("2000").compareTo(r.totals().revenue()));
        CustomerRevenueResponse.Row acme = r.customers().stream().filter(x -> "Acme Civils".equals(x.customer())).findFirst().orElseThrow();
        assertEquals(2, acme.charges());
        assertEquals(0, n("50.0").compareTo(acme.sharePercent()));
        assertTrue(r.customers().stream().anyMatch(x -> "A removed customer".equals(x.customer())));
        granted.clear(); granted.add("EARTHMOVING_READ");
        assertThrows(HandyFlowException.class, () -> service.customerRevenue(tenant, FROM, TO));
    }

    @Test void downtimeCountsBreakdownAndMaintenanceAndSkipsMachinesThatNeverStopped() {
        when(assets.findAllActive(eq(tenant), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(d9, loader)));
        granted.clear(); granted.add("EARTHMOVING_READ");
        DowntimeResponse r = service.downtime(tenant, FROM, TO);
        assertTrue(r.machines().isEmpty());
        assertEquals(0, r.totals().totalHours().signum());
        assertThrows(IllegalArgumentException.class, () -> service.downtime(tenant, TO, FROM));
    }

    private EarthHireCharge charge(UUID customerId, String amount) {
        EarthHireCharge c = mock(EarthHireCharge.class);
        when(c.getCustomerId()).thenReturn(customerId);
        when(c.getAmount()).thenReturn(n(amount));
        when(c.getStatus()).thenReturn("PENDING");
        return c;
    }
}
