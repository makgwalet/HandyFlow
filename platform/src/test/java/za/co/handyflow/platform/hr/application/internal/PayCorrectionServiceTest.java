package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos.View;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Correcting an issued payslip: the right kind of pay item, linked to the payslip, written to the run's history. Hand-written fakes. */
class PayCorrectionServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final HrPayRun run = HrPayRun.create(tenant, "PR00005", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 25), 2027);
    private final HrEmployee jane = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
    private final HrPayslip slip = HrPayslip.create(tenant, run.getId(), jane.getId(), new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    private final List<HrPayAdjustment> store = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    {
        slip.applyCalculations(new BigDecimal("4000"), new BigDecimal("177.12"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("30000"), BigDecimal.ZERO, BigDecimal.ZERO, 2027);
    }

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final HrPayslipRepository payslipRepo = fake(HrPayslipRepository.class, (n, a) ->
            "findByTenantAndId".equals(n) ? (slip.getId().equals(a[1]) ? Optional.of(slip) : Optional.empty()) : NOPE);
    private final HrPayRunRepository payRunRepo = fake(HrPayRunRepository.class, (n, a) -> switch (n) {
        case "findByTenantAndId" -> run.getId().equals(a[1]) ? Optional.of(run) : Optional.empty();
        case "findAllById" -> List.of(run);
        default -> NOPE;
    });
    @SuppressWarnings("unchecked")
    private final HrEmployeeRepository employeeRepo = fake(HrEmployeeRepository.class, (n, a) -> switch (n) {
        case "findActiveById" -> jane.getId().equals(a[1]) ? Optional.of(jane) : Optional.empty();
        case "findAllById" -> List.of(jane);
        default -> NOPE;
    });
    private final HrPayAdjustmentRepository adjRepo = fake(HrPayAdjustmentRepository.class, (n, a) -> switch (n) {
        case "save" -> { HrPayAdjustment x = (HrPayAdjustment) a[0]; if (!store.contains(x)) store.add(x); yield x; }
        case "forEmployee" -> store.stream().filter(x -> x.getEmployeeId().equals(a[1])).toList();
        case "forPayslip" -> store.stream().filter(x -> slip.getId().equals(x.getCorrectsPayslipId())).toList();
        default -> NOPE;
    });
    private final HrPayRunEventRepository eventRepo = fake(HrPayRunEventRepository.class, (n, a) -> {
        if (!"save".equals(n)) return NOPE;
        events.add(((HrPayRunEvent) a[0]).getAction() + ":" + ((HrPayRunEvent) a[0]).getNote());
        return a[0];
    });
    private final PayAdjustmentService adjustments = new PayAdjustmentService(adjRepo, employeeRepo, payRunRepo);
    private final PayCorrectionService service = new PayCorrectionService(payslipRepo, payRunRepo, employeeRepo, eventRepo, adjustments);

    private void finalised() { run.finalise(UUID.randomUUID()); }

    private PayCorrectionService.EnterRequest req(String direction, String amount) {
        return new PayCorrectionService.EnterRequest(direction, new BigDecimal(amount), "Overtime was left off");
    }

    @Test
    void underpaidBecomesBackPayForThePayslipsPeriodLinkedToIt() {
        finalised();
        View v = service.enter(tenant, slip.getId(), req("UNDERPAID", "1500"), actor);
        assertEquals("Back pay", v.kindLabel());
        assertEquals(new BigDecimal("1500.00"), v.amount());
        assertEquals("Correction of payslip PR00005", v.description());
        assertEquals(LocalDate.of(2026, 10, 1), v.relatesFrom());
        assertEquals(slip.getId(), store.get(0).getCorrectsPayslipId());
        assertEquals("OPEN", store.get(0).getStatus());
    }

    @Test
    void overpaidBecomesARecoveryWithoutDates() {
        finalised();
        View v = service.enter(tenant, slip.getId(), req("OVERPAID", "800"), actor);
        assertEquals("Recovery of overpayment", v.kindLabel());
        assertNull(v.relatesFrom());
        assertEquals(slip.getId(), store.get(0).getCorrectsPayslipId());
    }

    @Test
    void theRunsHistoryRecordsTheCorrection() {
        finalised();
        service.enter(tenant, slip.getId(), req("OVERPAID", "800"), actor);
        assertEquals(1, events.size());
        assertTrue(events.get(0).startsWith("PAYSLIP_CORRECTION_ENTERED:EMP-1: overpaid, recovery 800.00"));
    }

    @Test
    void aRunThatIsNotFinalisedIsRefusedAndNothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.enter(tenant, slip.getId(), req("UNDERPAID", "100"), actor));
        assertTrue(store.isEmpty());
        assertTrue(events.isEmpty());
    }

    @Test
    void anOverpaymentLargerThanTheNetPayIsRefused() {
        finalised();
        assertThrows(IllegalArgumentException.class, () -> service.enter(tenant, slip.getId(), req("OVERPAID", "99999"), actor));
        assertTrue(store.isEmpty());
    }

    @Test
    void theSameKindCannotBeEnteredTwiceWhileTheFirstIsWaiting() {
        finalised();
        service.enter(tenant, slip.getId(), req("UNDERPAID", "100"), actor);
        assertThrows(IllegalArgumentException.class, () -> service.enter(tenant, slip.getId(), req("UNDERPAID", "100"), actor));
        service.enter(tenant, slip.getId(), req("OVERPAID", "50"), actor);   // the other kind is a different thing
        assertEquals(2, store.size());
    }

    @Test
    void nobodyCorrectsTheirOwnPayslip() {
        finalised();
        jane.setUserId(actor);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.enter(tenant, slip.getId(), req("UNDERPAID", "100"), actor));
        assertTrue(store.isEmpty());
    }

    @Test
    void anUnknownPayslipAndAnUnknownUserAreRefused() {
        finalised();
        assertThrows(ResourceNotFoundException.class, () -> service.enter(tenant, UUID.randomUUID(), req("UNDERPAID", "100"), actor));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.enter(tenant, slip.getId(), req("UNDERPAID", "100"), null));
    }

    @Test
    void theCorrectionsOfAPayslipAreListed() {
        finalised();
        service.enter(tenant, slip.getId(), req("UNDERPAID", "100"), actor);
        assertEquals(1, service.forPayslip(tenant, slip.getId()).size());
        assertThrows(ResourceNotFoundException.class, () -> service.forPayslip(tenant, UUID.randomUUID()));
    }
}
