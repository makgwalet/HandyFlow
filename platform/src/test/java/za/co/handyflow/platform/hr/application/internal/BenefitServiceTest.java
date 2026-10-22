package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.BenefitRules;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeBenefit;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeBenefitRepository;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.hr.dto.BenefitDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BenefitServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID actor = UUID.randomUUID();
    private final HrEmployeeBenefitRepository repo = mock(HrEmployeeBenefitRepository.class);
    private final HrEmployeeRepository employees = mock(HrEmployeeRepository.class);
    private final BenefitService service = new BenefitService(repo, employees);
    private final List<HrEmployeeBenefit> saved = new ArrayList<>();
    private HrEmployee emp;

    @BeforeEach
    void setUp() {
        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        when(employees.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(repo.save(any())).thenAnswer(i -> { HrEmployeeBenefit b = i.getArgument(0); if (!saved.contains(b)) saved.add(b); return b; });
        when(repo.forEmployee(any(), any())).thenAnswer(i -> new ArrayList<>(saved));
    }

    private AddRequest medical(String amount, String from) {
        return new AddRequest("EMPLOYER_MEDICAL", new BigDecimal(amount), LocalDate.parse(from), null, null, "Joined the scheme");
    }

    @Test
    void aBenefitIsSavedWithWhoAddedItAndWhy() {
        View v = service.add(tenant, emp.getId(), medical("2500", "2026-03-01"), actor);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCreatedBy()).isEqualTo(actor);
        assertThat(saved.get(0).getReason()).isEqualTo("Joined the scheme");
        assertThat(v.kindLabel()).isEqualTo("Employer medical scheme contribution");
    }

    @Test
    void aSecondOverlappingMedicalLineIsRefused() {
        service.add(tenant, emp.getId(), medical("2500", "2026-03-01"), actor);

        assertThatThrownBy(() -> service.add(tenant, emp.getId(), medical("2800", "2026-06-01"), actor)).hasMessageContaining("End it first");
        assertThat(saved).hasSize(1);
    }

    @Test
    void endingTheOldLineLetsTheNewAmountStart() {
        View first = service.add(tenant, emp.getId(), medical("2500", "2026-03-01"), actor);
        when(repo.findByTenantAndId(any(), eq(first.id()))).thenReturn(Optional.of(saved.get(0)));
        service.end(tenant, first.id(), new EndRequest(LocalDate.parse("2026-05-31")), actor);
        service.add(tenant, emp.getId(), medical("2800", "2026-06-01"), actor);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getEffectiveTo()).isEqualTo(LocalDate.parse("2026-05-31"));
    }

    @Test
    void nothingIsRecordedWithoutAKnownUserOrForAnUnknownEmployee() {
        assertThatThrownBy(() -> service.add(tenant, emp.getId(), medical("2500", "2026-03-01"), null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.add(tenant, UUID.randomUUID(), medical("2500", "2026-03-01"), actor)).isInstanceOf(ResourceNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void aRunReadsEveryonesBenefitsInOneGo() {
        HrEmployeeBenefit m = HrEmployeeBenefit.of(tenant.getValue(), emp.getId(), "EMPLOYER_MEDICAL", new BigDecimal("2500"), LocalDate.parse("2026-01-01"), null, null, "x", actor);
        HrEmployeeBenefit r = HrEmployeeBenefit.of(tenant.getValue(), emp.getId(), "EMPLOYER_RETIREMENT", new BigDecimal("1800"), LocalDate.parse("2026-01-01"), null, null, "x", actor);
        when(repo.activeOn(any(), eq(LocalDate.parse("2026-10-31")))).thenReturn(List.of(m, r));

        Map<UUID, BenefitRules.Monthly> out = service.forRun(tenant, LocalDate.parse("2026-10-31"));

        assertThat(out.get(emp.getId()).medical()).isEqualByComparingTo("2500");
        assertThat(out.get(emp.getId()).retirement()).isEqualByComparingTo("1800");
        assertThat(out.containsKey(UUID.randomUUID())).isFalse();
    }
}
