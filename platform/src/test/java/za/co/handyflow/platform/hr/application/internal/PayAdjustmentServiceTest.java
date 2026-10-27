package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPayAdjustment;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayAdjustmentRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos.AddRequest;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos.CancelRequest;
import za.co.handyflow.platform.hr.dto.PayAdjustmentDtos.View;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Entering, cancelling and handing bonuses and back pay to a pay run. Uses hand-written fakes for the repositories. */
class PayAdjustmentServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final HrEmployee jane = employee("EMP-1");
    private final List<HrPayAdjustment> store = new ArrayList<>();
    private final Map<UUID, HrPayRun> runs = new HashMap<>();

    private static HrEmployee employee(String number) {
        return HrEmployee.create(TenantId.of(UUID.randomUUID()), number, "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
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

    private final HrPayAdjustmentRepository repo = fake(HrPayAdjustmentRepository.class, (n, a) -> switch (n) {
        case "save" -> { HrPayAdjustment x = (HrPayAdjustment) a[0]; if (!store.contains(x)) store.add(x); yield x; }
        case "findByTenantAndId" -> store.stream().filter(x -> x.getId().equals(a[1])).findFirst();
        case "forEmployee" -> store.stream().filter(x -> x.getEmployeeId().equals(a[1])).toList();
        case "byStatus" -> store.stream().filter(x -> a[1] == null || x.getStatus().equals(a[1])).toList();
        case "payableIn" -> store.stream().filter(x -> "OPEN".equals(x.getStatus()) && !x.getPayFrom().isAfter((LocalDate) a[1])
                && (x.getPayRunId() == null || x.getPayRunId().equals(a[2]))).toList();
        case "takenBy" -> store.stream().filter(x -> a[1].equals(x.getPayRunId())).toList();
        default -> NOPE;
    });
    private final HrEmployeeRepository employeeRepo = fake(HrEmployeeRepository.class, (n, a) -> switch (n) {
        case "findActiveById" -> jane.getId().equals(a[1]) ? Optional.of(jane) : Optional.empty();
        case "findAllById" -> ((Iterable<UUID>) a[0]).iterator().hasNext() && jane.getId().equals(((Iterable<UUID>) a[0]).iterator().next()) ? List.of(jane) : List.of();
        default -> NOPE;
    });
    private final HrPayRunRepository payRunRepo = fake(HrPayRunRepository.class, (n, a) -> {
        if (!"findAllById".equals(n)) return NOPE;
        List<HrPayRun> out = new ArrayList<>();
        for (UUID id : (Iterable<UUID>) a[0]) if (runs.containsKey(id)) out.add(runs.get(id));
        return out;
    });
    private final PayAdjustmentService service = new PayAdjustmentService(repo, employeeRepo, payRunRepo);

    private AddRequest bonus(String amount) {
        return new AddRequest("BONUS", new BigDecimal(amount), LocalDate.now(), "Annual bonus", null, null, "Board decision");
    }

    @Test
    void enteringABonusSavesItOpenAndRecordsWho() {
        View v = service.add(tenant, jane.getId(), bonus("20000.5"), actor);
        assertEquals(1, store.size());
        assertEquals("OPEN", store.get(0).getStatus());
        assertEquals(actor, store.get(0).getCreatedBy());
        assertEquals(new BigDecimal("20000.50"), store.get(0).getAmount());
        assertEquals("Bonus", v.kindLabel());
        assertTrue(v.canCancel());
    }

    @Test
    void anInvalidEntryIsRefusedAndNothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.add(tenant, jane.getId(), bonus("0"), actor));
        assertTrue(store.isEmpty());
    }

    @Test
    void aUserCannotEnterAPaymentForTheirOwnRecord() {
        jane.setUserId(actor);
        assertThrows(AccessDeniedException.class, () -> service.add(tenant, jane.getId(), bonus("100"), actor));
        assertTrue(store.isEmpty());
    }

    @Test
    void anUnknownUserCannotEnterAnything() {
        assertThrows(AccessDeniedException.class, () -> service.add(tenant, jane.getId(), bonus("100"), null));
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.add(tenant, UUID.randomUUID(), bonus("100"), actor));
    }

    @Test
    void anOpenPaymentCanBeCancelledWithAReasonAndThenCannotBeCancelledAgain() {
        View v = service.add(tenant, jane.getId(), bonus("100"), actor);
        View c = service.cancel(tenant, v.id(), new CancelRequest("Entered twice"), actor);
        assertEquals("CANCELLED", c.status());
        assertEquals("Entered twice", store.get(0).getCancelReason());
        assertFalse(c.canCancel());
        assertThrows(IllegalStateException.class, () -> service.cancel(tenant, v.id(), new CancelRequest("again"), actor));
    }

    @Test
    void cancellingNeedsAReason() {
        View v = service.add(tenant, jane.getId(), bonus("100"), actor);
        assertThrows(IllegalArgumentException.class, () -> service.cancel(tenant, v.id(), new CancelRequest(" "), actor));
        assertEquals("OPEN", store.get(0).getStatus());
    }

    @Test
    void aPaymentARunHasTakenCannotBeCancelled() {
        View v = service.add(tenant, jane.getId(), bonus("100"), actor);
        store.get(0).linkTo(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> service.cancel(tenant, v.id(), new CancelRequest("Oops"), actor));
        assertEquals("OPEN", store.get(0).getStatus());
    }

    @Test
    void aRunTakesOpenItemsDatedUpToItsPeriodEndAndNotLaterOnes() {
        service.add(tenant, jane.getId(), new AddRequest("BONUS", new BigDecimal("100"), LocalDate.of(2026, 10, 15), "In period", null, null, "r"), actor);
        service.add(tenant, jane.getId(), new AddRequest("BONUS", new BigDecimal("200"), LocalDate.of(2026, 11, 15), "Next month", null, null, "r"), actor);
        UUID run = UUID.randomUUID();
        Map<UUID, List<HrPayAdjustment>> taken = service.takeFor(tenant, run, LocalDate.of(2026, 10, 31));
        assertEquals(1, taken.get(jane.getId()).size());
        assertEquals(new BigDecimal("100.00"), taken.get(jane.getId()).get(0).getAmount());
    }

    @Test
    void aCancelledItemIsNeverTaken() {
        View v = service.add(tenant, jane.getId(), new AddRequest("BONUS", new BigDecimal("100"), LocalDate.of(2026, 10, 15), "x", null, null, "r"), actor);
        service.cancel(tenant, v.id(), new CancelRequest("Entered twice"), actor);
        assertTrue(service.takeFor(tenant, UUID.randomUUID(), LocalDate.of(2026, 10, 31)).isEmpty());
    }

    @Test
    void markedItemsAreHeldByTheRunAndNotTakenByAnother() {
        service.add(tenant, jane.getId(), new AddRequest("BONUS", new BigDecimal("100"), LocalDate.of(2026, 10, 15), "x", null, null, "r"), actor);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        service.markTaken(service.takeFor(tenant, first, LocalDate.of(2026, 10, 31)).get(jane.getId()), first);
        assertTrue(service.takeFor(tenant, second, LocalDate.of(2026, 10, 31)).isEmpty());
        assertEquals(first, store.get(0).getPayRunId());
    }

    @Test
    void calculatingTheSameRunAgainGivesBackAndRetakesItsItems() {
        service.add(tenant, jane.getId(), new AddRequest("BONUS", new BigDecimal("100"), LocalDate.of(2026, 10, 15), "x", null, null, "r"), actor);
        UUID run = UUID.randomUUID();
        service.markTaken(service.takeFor(tenant, run, LocalDate.of(2026, 10, 31)).get(jane.getId()), run);
        Map<UUID, List<HrPayAdjustment>> again = service.takeFor(tenant, run, LocalDate.of(2026, 10, 31));
        assertEquals(1, again.get(jane.getId()).size());
        assertNull(store.get(0).getPayRunId());
    }

    @Test
    void releasingARunGivesItsItemsBackForTheNextRun() {
        service.add(tenant, jane.getId(), new AddRequest("BONUS", new BigDecimal("100"), LocalDate.of(2026, 10, 15), "x", null, null, "r"), actor);
        UUID run = UUID.randomUUID();
        service.markTaken(service.takeFor(tenant, run, LocalDate.of(2026, 10, 31)).get(jane.getId()), run);
        assertEquals(1, service.releaseFor(tenant, run));
        assertNull(store.get(0).getPayRunId());
        assertEquals(1, service.takeFor(tenant, UUID.randomUUID(), LocalDate.of(2026, 10, 31)).get(jane.getId()).size());
    }

    @Test
    void totalsAreKeptPerKind() {
        service.add(tenant, jane.getId(), bonus("100"), actor);
        service.add(tenant, jane.getId(), new AddRequest("BACK_PAY", new BigDecimal("250"), LocalDate.now(), "Raise", LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30), "Agreed late"), actor);
        assertEquals(new BigDecimal("100.00"), PayAdjustmentService.total(store, "BONUS"));
        assertEquals(new BigDecimal("250.00"), PayAdjustmentService.total(store, "BACK_PAY"));
    }

    @Test
    void theListShowsEmployeeAndRunAndCanBeFilteredByStatus() {
        View v = service.add(tenant, jane.getId(), bonus("100"), actor);
        service.add(tenant, jane.getId(), bonus("300"), actor);
        service.cancel(tenant, v.id(), new CancelRequest("Entered twice"), actor);
        HrPayRun run = HrPayRun.create(tenant, "PR00009", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 25), 2027);
        runs.put(run.getId(), run);
        store.get(1).linkTo(run.getId());
        List<View> open = service.list(tenant, "open");
        assertEquals(1, open.size());
        assertEquals("PR00009", open.get(0).payRunNumber());
        assertFalse(open.get(0).canCancel());
        assertEquals("Jane Dlamini", open.get(0).employeeName());
        assertEquals(2, service.list(tenant, " ").size());
    }
}
