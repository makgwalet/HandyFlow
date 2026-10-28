package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.Emp201ActionDtos.PayRequest;
import za.co.handyflow.platform.hr.dto.Emp201ActionDtos.SubmitRequest;
import za.co.handyflow.platform.hr.dto.Emp201Response;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Filing and paying an EMP201. Uses hand-written fakes for the repositories. */
class Emp201ServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
    private final HrPayRun run = HrPayRun.create(tenant, "PR00005", today.minusMonths(1).withDayOfMonth(1), today.withDayOfMonth(1).minusDays(1), today, 2027);
    private final HrEmp201 emp = HrEmp201.create(tenant, run.getId(), run.getPeriodStart(), run.getPeriodEnd(),
            new BigDecimal("10000.00"), new BigDecimal("1000.00"), new BigDecimal("345.67"));
    private final List<String> events = new ArrayList<>();

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

    private final HrEmp201Repository repo = fake(HrEmp201Repository.class, (n, a) -> switch (n) {
        case "findByTenantAndId" -> emp.getId().equals(a[1]) ? Optional.of(emp) : Optional.empty();
        case "save" -> a[0];
        default -> NOPE;
    });
    private final HrPayRunEventRepository eventRepo = fake(HrPayRunEventRepository.class, (n, a) -> {
        if (!"save".equals(n)) return NOPE;
        events.add(((HrPayRunEvent) a[0]).getAction() + ":" + ((HrPayRunEvent) a[0]).getNote());
        return a[0];
    });
    private final Emp201Fakes fakes = new Emp201Fakes();
    private final Emp201Service service = new Emp201Service(repo, eventRepo, fakes.merge);

    { fakes.add(run); fakes.links.add(HrEmp201Run.of(emp.getId(), run.getId())); }

    private BigDecimal total() { return emp.getTotalPayable(); }

    @Test
    void theDueDateComesFromTheRule() {
        assertEquals(Emp201Rules.dueDate(emp.getPeriodEnd()), emp.getDueDate());
        assertEquals(new BigDecimal("11345.67"), total());
    }

    @Test
    void cannotFileBeforeThePayRunIsFinalised() {
        assertThrows(IllegalStateException.class, () -> service.submit(tenant, emp.getId(), new SubmitRequest("712345678901"), actor));
        assertEquals("DRAFT", emp.getStatus());
        assertTrue(events.isEmpty());
    }

    @Test
    void filingKeepsTheReferenceWithoutSpacesAndWritesHistory() {
        run.finalise(UUID.randomUUID());
        Emp201Response r = service.submit(tenant, emp.getId(), new SubmitRequest(" 7123 4567 8901 "), actor);
        assertEquals("SUBMITTED", r.status());
        assertEquals("712345678901", r.paymentRef());
        assertTrue(r.runIssued());
        assertEquals(actor, emp.getSubmittedBy());
        assertEquals("EMP201_FILED:Reference 712345678901, R11345.67", events.get(0));
    }

    @Test
    void cannotFileTwice() {
        run.finalise(UUID.randomUUID());
        service.submit(tenant, emp.getId(), new SubmitRequest("712345678901"), actor);
        assertThrows(IllegalStateException.class, () -> service.submit(tenant, emp.getId(), new SubmitRequest("999999999999"), actor));
        assertEquals("712345678901", emp.getPaymentRef());
    }

    @Test
    void cannotPayBeforeFiling() {
        run.finalise(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> service.pay(tenant, emp.getId(), new PayRequest(today, total()), actor));
        assertEquals("DRAFT", emp.getStatus());
    }

    @Test
    void payingRecordsTheDateAmountAndWho() {
        run.finalise(UUID.randomUUID());
        service.submit(tenant, emp.getId(), new SubmitRequest("712345678901"), actor);
        Emp201Response r = service.pay(tenant, emp.getId(), new PayRequest(today, total()), actor);
        assertEquals("PAID", r.status());
        assertEquals(today, r.paidOn());
        assertEquals(new BigDecimal("11345.67"), r.paidAmount());
        assertFalse(r.overdue());
        assertEquals(actor, emp.getPaidBy());
        assertTrue(events.get(1).startsWith("EMP201_PAID:R11345.67 on " + today));
    }

    @Test
    void aShortPaymentIsRefusedAndTheDeclarationStaysFiled() {
        run.finalise(UUID.randomUUID());
        service.submit(tenant, emp.getId(), new SubmitRequest("712345678901"), actor);
        assertThrows(IllegalStateException.class, () -> service.pay(tenant, emp.getId(), new PayRequest(today, new BigDecimal("100")), actor));
        assertEquals("SUBMITTED", emp.getStatus());
        assertNull(emp.getPaidOn());
    }

    @Test
    void aPaymentAfterTheDueDateIsMarkedLate() {
        run.finalise(UUID.randomUUID());
        service.submit(tenant, emp.getId(), new SubmitRequest("712345678901"), actor);
        LocalDate paidOn = emp.getDueDate().plusDays(1).isAfter(today) ? today : emp.getDueDate().plusDays(1);
        Emp201Response r = service.pay(tenant, emp.getId(), new PayRequest(paidOn, total()), actor);
        assertEquals(paidOn.isAfter(emp.getDueDate()), r.late());
    }

    @Test
    void everyStepNeedsAKnownUserAndAKnownDeclaration() {
        assertThrows(AccessDeniedException.class, () -> service.submit(tenant, emp.getId(), new SubmitRequest("712345678901"), null));
        assertThrows(AccessDeniedException.class, () -> service.pay(tenant, emp.getId(), new PayRequest(today, total()), null));
        assertThrows(ResourceNotFoundException.class, () -> service.submit(tenant, UUID.randomUUID(), new SubmitRequest("712345678901"), actor));
    }
}
