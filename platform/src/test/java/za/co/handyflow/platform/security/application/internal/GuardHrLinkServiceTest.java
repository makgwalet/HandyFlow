package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Linking a guard to an HR employee record. */
class GuardHrLinkServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private final GuardRepository guards = mock(GuardRepository.class);
    private final HrFacade hr = mock(HrFacade.class);
    private final GuardHrLinkService service = new GuardHrLinkService(guards, hr);

    private Guard guard(String first) {
        Guard g = Guard.create(TENANT, first, "Botha", "P" + first, "id", "082", "C", null, null, null);
        when(guards.findActiveById(TENANT, g.getId())).thenReturn(Optional.of(g));
        return g;
    }

    private EmployeeResponse employee(UUID id) {
        EmployeeResponse e = mock(EmployeeResponse.class);
        when(e.id()).thenReturn(id);
        when(e.employeeNumber()).thenReturn("EMP-0007");
        when(e.fullName()).thenReturn("Gerhard Botha");
        when(e.jobTitle()).thenReturn("Security officer");
        when(e.status()).thenReturn("ACTIVE");
        return e;
    }

    @Test @DisplayName("An unlinked guard reports no link")
    void notLinked() {
        Guard g = guard("A");
        assertThat(service.get(TENANT, g.getId()).linked()).isFalse();
    }

    @Test @DisplayName("Linking stores the employee id and returns HR's details")
    void links() {
        Guard g = guard("A");
        UUID emp = UUID.randomUUID();
        when(hr.findEmployeeById(TENANT, emp)).thenReturn(Optional.of(employee(emp)));
        when(guards.findByEmployee(TENANT, emp)).thenReturn(Optional.empty());
        var link = service.link(TENANT, g.getId(), emp);
        assertThat(link.linked()).isTrue();
        assertThat(link.employeeNumber()).isEqualTo("EMP-0007");
        assertThat(g.getEmployeeId()).isEqualTo(emp);
        verify(guards).save(g);
    }

    @Test @DisplayName("An employee already linked to another guard is refused; re-linking the same guard is fine")
    void oneToOne() {
        Guard a = guard("A"), b = guard("B");
        UUID emp = UUID.randomUUID();
        when(hr.findEmployeeById(TENANT, emp)).thenReturn(Optional.of(employee(emp)));
        when(guards.findByEmployee(TENANT, emp)).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.link(TENANT, b.getId(), emp)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already linked");
        assertThat(service.link(TENANT, a.getId(), emp).linked()).isTrue();
    }

    @Test @DisplayName("An unknown employee is not found and nothing is saved")
    void unknownEmployee() {
        Guard g = guard("A");
        UUID emp = UUID.randomUUID();
        when(hr.findEmployeeById(TENANT, emp)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.link(TENANT, g.getId(), emp)).isInstanceOf(ResourceNotFoundException.class);
        verify(guards, never()).save(any());
    }

    @Test @DisplayName("A link whose employee has gone is reported as missing, and unlinking clears it")
    void missingAndUnlink() {
        Guard g = guard("A");
        UUID emp = UUID.randomUUID();
        g.linkEmployee(emp);
        when(hr.findEmployeeById(TENANT, emp)).thenReturn(Optional.empty());
        var link = service.get(TENANT, g.getId());
        assertThat(link.linked()).isTrue();
        assertThat(link.employeeMissing()).isTrue();
        assertThat(service.unlink(TENANT, g.getId()).linked()).isFalse();
        assertThat(g.getEmployeeId()).isNull();
    }

    @Test @DisplayName("Searching needs at least two characters")
    void search() {
        assertThat(service.search(TENANT, " a ")).isEmpty();
        verify(hr, never()).searchEmployees(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        when(hr.searchEmployees(TENANT, "bot", 20)).thenReturn(List.of());
        assertThat(service.search(TENANT, "bot")).isEmpty();
    }
}
