package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrderCostLine;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderCostLineRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.AddFmCostLineRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmCostLineResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Who may price a cost line: a technician records what was used and the rate card prices it; only a manager may
 * override the client charge or write a line off. Plain JUnit with hand-written fakes — no Mockito.
 */
class FmWorkOrderCostServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID technician = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final List<FmWorkOrderCostLine> lines = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final Set<String> authorities = new HashSet<>();
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();
    private FmWorkOrder wo;

    private final FmWorkOrderRepository workOrderRepo = (FmWorkOrderRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmWorkOrderRepository.class}, (p, m, a) -> {
                if ("findActiveById".equals(m.getName())) return Optional.ofNullable(wo != null && wo.getId().equals(a[1]) ? wo : null);
                throw new UnsupportedOperationException(m.getName());
            });

    private final FmWorkOrderCostLineRepository lineRepo = (FmWorkOrderCostLineRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmWorkOrderCostLineRepository.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "save": lines.add((FmWorkOrderCostLine) a[0]); return a[0];
                    case "delete": lines.remove(a[0]); return null;
                    case "findForWorkOrder": return List.copyOf(lines);
                    case "findByIdForWorkOrder": return lines.stream().filter(l -> l.getId().equals(a[2])).findFirst();
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });

    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
        @Override public boolean hasAuthority(String a) { return authorities.contains(a); }
    };
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };

    private final FmWorkOrderCostService service = new FmWorkOrderCostService(workOrderRepo, lineRepo, policy, auditService);

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    /** An ASSIGNED order carrying a rate card of R450/h labour and 20% parts markup. */
    private void assignedOrder(String coverage) {
        wo = FmWorkOrder.create(tenant, "WO-1", UUID.randomUUID(), UUID.randomUUID(), null, null, "CORRECTIVE", "NORMAL",
                "Leak", "Reception", LocalDate.now());
        wo.applyContract(UUID.randomUUID(), coverage, bd("450.00"), bd("20.00"));
        wo.assign(technician, "Sipho", null, null);
    }

    private AddFmCostLineRequest labour(BigDecimal charge, Boolean billable) {
        return new AddFmCostLineRequest("LABOUR", "2h call-out", bd("2"), bd("200.00"), charge, billable);
    }

    private void actAs(UUID user) { TenantContext.setUserId(user.toString()); }

    @Test
    void labourIsChargedAtTheStampedHourlyRate() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        FmCostLineResponse r = service.add(tenant, wo.getId(), labour(null, null));
        assertEquals(0, bd("400.00").compareTo(r.totalCost()));
        assertEquals(0, bd("900.00").compareTo(r.totalCharge()));
        assertTrue(r.billable());
    }

    @Test
    void partsAreChargedAtCostPlusTheStampedMarkup() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        FmCostLineResponse r = service.add(tenant, wo.getId(),
                new AddFmCostLineRequest("PARTS", "Tap washer", bd("3"), bd("100.00"), null, null));
        assertEquals(0, bd("300.00").compareTo(r.totalCost()));
        assertEquals(0, bd("360.00").compareTo(r.totalCharge())); // 120.00 each x 3
    }

    @Test
    void otherCostTypesChargeAtCostWithNoMarkup() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        FmCostLineResponse r = service.add(tenant, wo.getId(),
                new AddFmCostLineRequest("TRAVEL", "Fuel", bd("1"), bd("85.00"), null, null));
        assertEquals(0, bd("85.00").compareTo(r.totalCharge()));
    }

    @Test
    void aTechnicianCannotOverrideTheClientCharge() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.add(tenant, wo.getId(), labour(bd("1.00"), null)));
        assertEquals("COST_LINE_PRICING_RESTRICTED", e.getErrorCode());
        assertTrue(lines.isEmpty());
    }

    @Test
    void aTechnicianCannotWriteALineOffAsNonBillable() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        assertThrows(HandyFlowException.class, () -> service.add(tenant, wo.getId(), labour(null, false)));
        assertTrue(lines.isEmpty());
    }

    @Test
    void aManagerMayOverrideTheChargeAndWriteOffAndAdminsCanToo() {
        assignedOrder("CHARGEABLE");
        authorities.add("FACILITIESMANAGEMENT_MANAGE");
        actAs(otherUser);
        FmCostLineResponse r = service.add(tenant, wo.getId(), labour(bd("500.00"), null));
        assertEquals(0, bd("1000.00").compareTo(r.totalCharge()));
        authorities.clear();
        authorities.add("FACILITIESMANAGEMENT_ADMIN");
        FmCostLineResponse writeOff = service.add(tenant, wo.getId(), labour(null, false));
        assertFalse(writeOff.billable());
        assertEquals(2, lines.size());
    }

    @Test
    void costsCannotChangeOnceTheJobIsCompletedOrInvoiced() {
        assignedOrder("CHARGEABLE");
        wo.start();
        wo.complete("Done", bd("0"), bd("0"), null, UUID.randomUUID(), null, null);
        actAs(technician);
        assertThrows(IllegalStateException.class, () -> service.add(tenant, wo.getId(), labour(null, null)));
    }

    @Test
    void aTechnicianMayRemoveOnlyTheirOwnLines() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        FmCostLineResponse mine = service.add(tenant, wo.getId(), labour(null, null));
        actAs(otherUser);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.remove(tenant, wo.getId(), mine.id()));
        assertEquals("COST_LINE_NOT_OWNED", e.getErrorCode());
        assertEquals(1, lines.size());
        actAs(technician);
        service.remove(tenant, wo.getId(), mine.id());
        assertTrue(lines.isEmpty());
        assertEquals(List.of("COST_LINE_ADDED", "COST_LINE_REMOVED"), audit);
    }

    @Test
    void aManagerMayRemoveAnyLine() {
        assignedOrder("CHARGEABLE");
        actAs(technician);
        FmCostLineResponse mine = service.add(tenant, wo.getId(), labour(null, null));
        authorities.add("FACILITIESMANAGEMENT_MANAGE");
        actAs(otherUser);
        service.remove(tenant, wo.getId(), mine.id());
        assertTrue(lines.isEmpty());
    }

    @Test
    void aWorkOrderOutsideTheCallersScopeLooksLikeItDoesNotExist() {
        assignedOrder("CHARGEABLE");
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        actAs(technician);
        assertThrows(ResourceNotFoundException.class, () -> service.add(tenant, wo.getId(), labour(null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.list(tenant, wo.getId()));
    }
}
