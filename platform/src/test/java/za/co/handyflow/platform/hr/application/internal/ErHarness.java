package za.co.handyflow.platform.hr.application.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.hr.domain.model.DisciplineRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The employee relations services wired to mock repositories, and cases in the states the tests need. */
final class ErHarness {

    static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    static final UUID HR_USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final Set<String> MANAGE = Set.of("HR_ER_READ", "HR_ER_MANAGE");
    static final Set<String> DECIDE = Set.of("HR_ER_READ", "HR_ER_DECIDE");
    static final Set<String> NOTHING = Set.of();

    final HrErCaseRepository cases = mock(HrErCaseRepository.class);
    final HrErChargeRepository charges = mock(HrErChargeRepository.class);
    final HrErStepRepository steps = mock(HrErStepRepository.class);
    final HrErEventRepository events = mock(HrErEventRepository.class);
    final HrEmployeeRepository employees = mock(HrEmployeeRepository.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final HrErPolicyRepository policyRepo = mock(HrErPolicyRepository.class);
    final ErCaseViews views = mock(ErCaseViews.class);

    final ErPolicyService policy = new ErPolicyService(policyRepo);
    final ErCaseSupport support = new ErCaseSupport(cases, steps, events, employees, jdbc);
    final ErCaseService service = new ErCaseService(cases, charges, steps, support, views, policy);
    final DisciplineService discipline = new DisciplineService(cases, charges, support, views, policy);
    final GrievanceService grievance = new GrievanceService(cases, support, views);
    final IncapacityService incapacity = new IncapacityService(cases, steps, support, views, policy);

    ErHarness() {
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(7);
    }

    /** Today in South Africa, which is the day the services use. */
    static LocalDate today() { return LocalDate.now(DisciplineRules.ZONE); }

    /** An employee who exists in this company. */
    UUID employee() {
        HrEmployee e = mock(HrEmployee.class);
        UUID id = UUID.randomUUID();
        lenient().when(e.getId()).thenReturn(id);
        lenient().when(e.getFullName()).thenReturn("Test Person");
        lenient().when(e.getEmployeeNumber()).thenReturn("E-" + id.toString().substring(0, 4));
        lenient().when(employees.findActiveById(TENANT, id)).thenReturn(Optional.of(e));
        return id;
    }

    /** Makes this person the one the signed-in user is. */
    void signedInAs(UUID user, UUID employeeId) {
        HrEmployee e = employees.findActiveById(TENANT, employeeId).orElseThrow();
        lenient().when(employees.findByUserId(TENANT, user)).thenReturn(Optional.of(e));
    }

    HrErCase known(HrErCase c) {
        lenient().when(cases.findByTenantAndId(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    HrErCase newCase(String type, String category, UUID employeeId) {
        LocalDate today = today();
        return known(HrErCase.open(TENANT.getValue(), "ER-2026-0001", type, category, employeeId, "A matter", "What happened",
                today.minusDays(10), today.minusDays(9), false, HR_USER, HR_USER));
    }

    HrErCase discipline(UUID employeeId) { return newCase("DISCIPLINARY", "LATENESS", employeeId); }
}
