package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPerformanceResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Per-client SLA report after the tally was shared with the portfolio report: numbers must not have moved. */
class FmSlaReportServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final List<FmWorkOrder> orders = new ArrayList<>();
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();

    private final FmWorkOrderRepository repo = (FmWorkOrderRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmWorkOrderRepository.class}, (p, m, a) -> {
                if ("findWithSlaForClient".equals(m.getName())) return List.copyOf(orders);
                throw new UnsupportedOperationException(m.getName());
            });
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
    };
    private final FmSlaReportService service = new FmSlaReportService(repo, policy);

    private static void set(Object o, String f, Object v) {
        try { Field x = o.getClass().getDeclaredField(f); x.setAccessible(true); x.set(o, v); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    /** responded/completed {@code actualMin} after creation against targets of 60 / 240 minutes. */
    private FmWorkOrder job(String priority, Integer respondedAfterMin, Integer completedAfterMin) {
        FmWorkOrder w = FmWorkOrder.create(tenant, "WO-" + (orders.size() + 1), client, UUID.randomUUID(), null, null,
                "CORRECTIVE", priority, "Fault", "Reception", null);
        Instant created = Instant.now().minus(Duration.ofDays(1));
        set(w, "createdAt", created);
        w.applySla(60, 240, "24X7", created.plus(Duration.ofMinutes(60)), created.plus(Duration.ofMinutes(240)));
        if (respondedAfterMin != null) set(w, "respondedAt", created.plus(Duration.ofMinutes(respondedAfterMin)));
        if (completedAfterMin != null) { set(w, "completedAt", created.plus(Duration.ofMinutes(completedAfterMin))); set(w, "status", "COMPLETED"); }
        orders.add(w);
        return w;
    }

    @Test
    void countsMetBreachedAndOpenForResponseAndResolution() {
        job("URGENT", 30, 200);   // both met
        job("URGENT", 90, 300);   // both breached
        job("NORMAL", 20, null);  // response met, resolution breached (due 240 min after creation, a day ago)
        FmSlaPerformanceResponse r = service.clientPerformance(tenant, client, LocalDate.now().minusDays(7), LocalDate.now());
        assertEquals(3, r.workOrdersWithSla());
        assertEquals(2, r.responseMet());
        assertEquals(1, r.responseBreached());
        assertEquals(1, r.resolutionMet());
        assertEquals(2, r.resolutionBreached());
        assertEquals(0, new BigDecimal("66.67").compareTo(r.responseCompliancePercent()));
        assertEquals(0, new BigDecimal("33.33").compareTo(r.resolutionCompliancePercent()));
    }

    @Test
    void breaksDownBreachesByPriority() {
        job("URGENT", 90, 300);
        job("NORMAL", 10, 100);
        FmSlaPerformanceResponse r = service.clientPerformance(tenant, client, LocalDate.now().minusDays(7), LocalDate.now());
        FmSlaPerformanceResponse.PriorityBreakdown urgent = r.byPriority().stream().filter(b -> b.priority().equals("URGENT")).findFirst().orElseThrow();
        assertEquals(1, urgent.total());
        assertEquals(1, urgent.responseBreached());
        assertEquals(1, urgent.resolutionBreached());
        assertEquals(0, r.byPriority().stream().filter(b -> b.priority().equals("NORMAL")).findFirst().orElseThrow().responseBreached());
    }

    @Test
    void nothingConcludedMeansNoPercentage() {
        FmSlaPerformanceResponse r = service.clientPerformance(tenant, client, LocalDate.now().minusDays(7), LocalDate.now());
        assertEquals(0, r.workOrdersWithSla());
        assertNull(r.responseCompliancePercent());
        assertNull(r.resolutionCompliancePercent());
    }

    @Test
    void aClientOutsideTheCallersScopeIsNotFound() {
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.clientPerformance(tenant, client, LocalDate.now().minusDays(7), LocalDate.now()));
    }

    @Test
    void periodRulesStillApply() {
        assertThrows(IllegalArgumentException.class, () -> service.clientPerformance(tenant, client, LocalDate.now(), LocalDate.now().minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.clientPerformance(tenant, client, LocalDate.now().minusDays(800), LocalDate.now()));
    }
}
