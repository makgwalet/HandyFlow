package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeAuditRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EtiServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final HrEmployee jane = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2025, 1, 1), "PERMANENT", new BigDecimal("5000"), "MONTHLY");
    private final List<String> audits = new ArrayList<>();

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

    private final HrEmployeeRepository employees = fake(HrEmployeeRepository.class, (n, a) -> switch (n) {
        case "findActiveById" -> jane.getId().equals(a[1]) ? Optional.of(jane) : Optional.empty();
        case "save" -> a[0];
        default -> NOPE;
    });
    private final HrEmployeeAuditRepository auditRepo = fake(HrEmployeeAuditRepository.class, (n, a) -> {
        if (!"save".equals(n)) return NOPE;
        audits.add(String.valueOf(a[0]));
        return a[0];
    });
    private final EtiService service = new EtiService(null, employees, new EmployeeAuditService(auditRepo));

    @Test
    void theFactsAreSavedAndReadBack() {
        service.update(tenant, jane.getId(), new EtiService.UpdateRequest(5, " Domestic worker "), actor);
        EtiService.EmployeeEti e = service.get(tenant, jane.getId());
        assertEquals(5, e.priorMonths());
        assertEquals("Domestic worker", e.excludedReason());
        assertEquals(1, audits.size());
    }

    @Test
    void aBlankReasonMeansTheyMayQualify() {
        service.update(tenant, jane.getId(), new EtiService.UpdateRequest(0, "  "), actor);
        assertNull(service.get(tenant, jane.getId()).excludedReason());
    }

    @Test
    void monthsOutsideZeroToTwentyFourAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.update(tenant, jane.getId(), new EtiService.UpdateRequest(25, null), actor));
        assertThrows(IllegalArgumentException.class, () -> service.update(tenant, jane.getId(), new EtiService.UpdateRequest(-1, null), actor));
        assertTrue(audits.isEmpty());
    }

    @Test
    void anUnknownEmployeeAndAnUnknownUserAreRefused() {
        assertThrows(ResourceNotFoundException.class, () -> service.update(tenant, UUID.randomUUID(), new EtiService.UpdateRequest(0, null), actor));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.update(tenant, jane.getId(), new EtiService.UpdateRequest(0, null), null));
    }
}
