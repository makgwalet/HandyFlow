package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrMedicalSchemeMembership;
import za.co.handyflow.platform.hr.domain.model.MedicalSchemeRules;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.domain.repository.HrMedicalSchemeMembershipRepository;
import za.co.handyflow.platform.hr.dto.MedicalSchemeRequest;
import za.co.handyflow.platform.hr.dto.MedicalSchemeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MedicalSchemeServiceTest {

    private HrMedicalSchemeMembershipRepository memberships;
    private HrEmployeeRepository employees;
    private MedicalSchemeService service;
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private HrEmployee jane;

    @BeforeEach
    void setUp() {
        memberships = mock(HrMedicalSchemeMembershipRepository.class);
        employees = mock(HrEmployeeRepository.class);
        service = new MedicalSchemeService(memberships, employees);
        jane = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        when(employees.findActiveById(any(), any())).thenReturn(Optional.of(jane));
        when(memberships.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private MedicalSchemeRequest req(String from, int dependants) {
        return new MedicalSchemeRequest("Discovery", "M123", dependants, LocalDate.parse(from), null);
    }

    @Test
    void theFirstMembershipIsRecordedAndNothingIsClosed() {
        when(memberships.forEmployee(any(), any())).thenReturn(List.of());
        MedicalSchemeResponse r = service.add(tenant, jane.getId(), req("2026-03-01", 2), UUID.randomUUID());
        assertThat(r.peopleCovered()).isEqualTo(3);
        assertThat(r.effectiveTo()).isNull();
        verify(memberships, times(1)).save(any());
    }

    @Test
    void aNewMembershipClosesTheOpenOneTheDayBefore() {
        HrMedicalSchemeMembership open = HrMedicalSchemeMembership.create(tenant, jane.getId(), "Discovery", null, 1,
                LocalDate.of(2026, 3, 1), null, null);
        when(memberships.forEmployee(any(), any())).thenReturn(List.of(open));
        service.add(tenant, jane.getId(), req("2026-09-01", 3), UUID.randomUUID());
        assertThat(open.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 8, 31));
        verify(memberships, times(2)).save(any());
    }

    @Test
    void anOverlapIsRefusedAndNothingIsSaved() {
        HrMedicalSchemeMembership closed = HrMedicalSchemeMembership.create(tenant, jane.getId(), "Discovery", null, 1,
                LocalDate.of(2026, 3, 1), null, null);
        closed.closeOn(LocalDate.of(2026, 8, 31));
        when(memberships.forEmployee(any(), any())).thenReturn(List.of(closed));
        assertThatThrownBy(() -> service.add(tenant, jane.getId(), req("2026-08-15", 1), null)).hasMessageContaining("overlaps");
        verify(memberships, never()).save(any());
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        when(employees.findActiveById(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(tenant, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.add(tenant, UUID.randomUUID(), req("2026-03-01", 0), null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void membersOnUsesTheRecordAndFallsBackToTheContribution() {
        HrEmployee paying = HrEmployee.create(tenant, "EMP-2", "Sam", "Nkosi", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
        paying.setMedicalAidContribution(new BigDecimal("900.00"));
        HrMedicalSchemeMembership m = HrMedicalSchemeMembership.create(tenant, jane.getId(), "Discovery", null, 2,
                LocalDate.of(2026, 3, 1), null, null);
        when(memberships.forEmployees(any(), any())).thenReturn(List.of(m));

        Map<UUID, MedicalSchemeRules.Members> out = service.membersOn(tenant, List.of(jane, paying), LocalDate.of(2026, 10, 31));

        assertThat(out.get(jane.getId())).isEqualTo(new MedicalSchemeRules.Members(3, false));
        assertThat(out.get(paying.getId())).isEqualTo(new MedicalSchemeRules.Members(1, true));
    }

    @Test
    void noStaffMeansNoQuery() {
        assertThat(service.membersOn(tenant, List.of(), LocalDate.of(2026, 10, 31))).isEmpty();
        verify(memberships, never()).forEmployees(any(), any());
    }
}
