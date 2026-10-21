package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.EmployeeViewResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmployeeViewServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    HrEmployeeRepository repo;
    EmployeeAuditService audit;
    EmployeeViewService service;
    HrEmployee boss, member, outsider;
    final UUID bossLogin = UUID.randomUUID();

    private HrEmployee person(String first, String last) {
        HrEmployee e = HrEmployee.create(tenant, "EMP-" + first, first, last, LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        e.setIdNumber("8001015009087");
        e.setBankAccountNumber("62000011112222");
        e.setBankName("FNB");
        return e;
    }

    @BeforeEach
    void setUp() {
        repo = mock(HrEmployeeRepository.class);
        audit = mock(EmployeeAuditService.class);
        service = new EmployeeViewService(repo, audit);
        boss = person("Boss", "Mokoena");
        member = person("Member", "Dlamini");
        outsider = person("Other", "Naidoo");
        boss.setUserId(bossLogin);
        member.setManagerId(boss.getId());
        when(repo.findNotTerminated(any())).thenReturn(List.of(boss, member, outsider));
        when(repo.findByUserId(any(), eq(bossLogin))).thenReturn(Optional.of(boss));
        when(repo.findActiveById(any(), eq(boss.getId()))).thenReturn(Optional.of(boss));
        when(repo.findActiveById(any(), eq(member.getId()))).thenReturn(Optional.of(member));
        when(repo.findActiveById(any(), eq(outsider.getId()))).thenReturn(Optional.of(outsider));
    }

    @Test
    void readOnlyHrSeesWorkingDetailsOnly() {
        EmployeeViewResponse v = service.get(tenant, member.getId(), Set.of("HR_READ"), UUID.randomUUID());

        assertThat(v.fullName()).isEqualTo("Member Dlamini");
        assertThat(v.idNumber()).isNull();
        assertThat(v.grossSalary()).isNull();
        assertThat(v.bankAccountMasked()).isNull();
        assertThat(v.canSeeSensitive()).isFalse();
        assertThat(v.canSeeCompensation()).isFalse();
    }

    @Test
    void sensitivePermissionShowsPersonalDataButNotPay() {
        EmployeeViewResponse v = service.get(tenant, member.getId(), Set.of("HR_READ", "HR_SENSITIVE_READ"), UUID.randomUUID());

        assertThat(v.idNumber()).isEqualTo("8001015009087");
        assertThat(v.grossSalary()).isNull();
    }

    @Test
    void compensationPermissionShowsPayWithAMaskedAccount() {
        EmployeeViewResponse v = service.get(tenant, member.getId(), Set.of("HR_READ", "HR_COMPENSATION_READ"), UUID.randomUUID());

        assertThat(v.grossSalary()).isEqualByComparingTo("30000");
        assertThat(v.bankAccountMasked()).isEqualTo("••••2222");
        assertThat(v.idNumber()).isNull();
    }

    @Test
    void lookingAtPersonalDataAndPayIsRecorded() {
        UUID actor = UUID.randomUUID();
        service.get(tenant, member.getId(), Set.of("HR_READ", "HR_SENSITIVE_READ", "HR_COMPENSATION_READ"), actor);

        verify(audit).recordViewOnce(tenant, member.getId(), "VIEWED_PERSONAL_DATA", actor);
        verify(audit).recordViewOnce(tenant, member.getId(), "VIEWED_PAY", actor);
    }

    @Test
    void lookingAtOrdinaryDetailsIsNotRecorded() {
        service.get(tenant, member.getId(), Set.of("HR_READ"), UUID.randomUUID());

        verifyNoInteractions(audit);
    }

    @Test
    void managerSeesTheirOwnTeamWithoutPersonalDataOrPay() {
        EmployeeViewResponse v = service.get(tenant, member.getId(), Set.of("HR_TEAM_READ", "HR_SENSITIVE_READ", "HR_COMPENSATION_READ"), bossLogin);

        assertThat(v.fullName()).isEqualTo("Member Dlamini");
        assertThat(v.idNumber()).isNull();
        assertThat(v.grossSalary()).isNull();
    }

    @Test
    void managerCannotOpenSomeoneOutsideTheirTeam() {
        assertThatThrownBy(() -> service.get(tenant, outsider.getId(), Set.of("HR_TEAM_READ"), bossLogin))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerWithoutALinkedEmployeeSeesNobody() {
        var page = service.list(tenant, null, null, PageRequest.of(0, 50), Set.of("HR_TEAM_READ"), UUID.randomUUID());

        assertThat(page.getTotalElements()).isEqualTo(0L);
    }

    @Test
    void managerListHoldsTheirTeamOnly() {
        var page = service.list(tenant, null, null, PageRequest.of(0, 50), Set.of("HR_TEAM_READ"), bossLogin);

        List<String> names = page.getContent().stream().map(EmployeeViewResponse::fullName).toList();
        assertThat(names).contains("Boss Mokoena", "Member Dlamini");
        assertThat(names).doesNotContain("Other Naidoo");
    }

    @Test
    void wideHrPermissionOverridesTeamScope() {
        UUID actor = UUID.randomUUID();
        EmployeeViewResponse v = service.get(tenant, outsider.getId(), Set.of("HR_READ", "HR_TEAM_READ"), actor);

        assertThat(v.fullName()).isEqualTo("Other Naidoo");
    }
}
