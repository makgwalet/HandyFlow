package za.co.handyflow.platform.hr.application;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.hr.EmployeePay;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.internal.HrService;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Other modules reach employees only through the facade, which hands out the narrow views. */
class HrFacadeTest {

    private final HrService hr = mock(HrService.class);
    private final HrFacade facade = new HrFacade(hr);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID id = UUID.randomUUID();

    private EmployeeResponse full() {
        return new EmployeeResponse(id, "EMP-1", "Thandi", "Mokoena", "Thandi Mokoena", "8001015009087", "1234567890", LocalDate.of(1980, 1, 1), "F", "African",
                "t@example.com", "0821234567", "PERMANENT", "Clerk", "Admin", LocalDate.of(2020, 1, 1), null, "ACTIVE", new BigDecimal("24000.00"), "MONTHLY",
                null, null, null, null, null, Instant.now());
    }

    @Test
    void findingAnEmployeeGivesTheSummaryOrNothing() {
        when(hr.getEmployee(tenant, id)).thenReturn(full());
        Optional<EmployeeSummary> found = facade.findEmployeeById(tenant, id);
        assertThat(found).isPresent();
        assertThat(found.get().fullName()).isEqualTo("Thandi Mokoena");
        UUID other = UUID.randomUUID();
        when(hr.getEmployee(tenant, other)).thenThrow(new ResourceNotFoundException("Employee", other.toString()));
        assertThat(facade.findEmployeeById(tenant, other)).isEmpty();
        assertThat(facade.employeeExists(tenant, other)).isFalse();
    }

    @Test
    void payIsAVeryNarrowSeparateCall() {
        when(hr.getEmployee(tenant, id)).thenReturn(full());
        EmployeePay pay = facade.findEmployeePay(tenant, id).orElseThrow();
        assertThat(pay.grossSalary()).isEqualByComparingTo("24000.00");
        assertThat(pay.payFrequency()).isEqualTo("MONTHLY");
        UUID other = UUID.randomUUID();
        when(hr.getEmployee(tenant, other)).thenThrow(new ResourceNotFoundException("Employee", other.toString()));
        assertThat(facade.findEmployeePay(tenant, other)).isEmpty();
    }

    @Test
    void searchGivesSummaries() {
        when(hr.getEmployees(eq(tenant), any(), eq("thandi"), any())).thenReturn(new PageImpl<>(List.of(full())));
        List<EmployeeSummary> found = facade.searchEmployees(tenant, "thandi", 20);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).employeeNumber()).isEqualTo("EMP-1");
    }
}
