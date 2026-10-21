package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.HrPayslip;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.PayrollImpactResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Worked from the 2027 rules: 30 003 a month is 4 681.78 PAYE with no medical credit, UIF 177.12, and no SDL
 * for a single-employee payroll (360 036 a year is below R500 000).
 */
class PayrollImpactServiceTest {

    private HrPayRunRepository payRuns;
    private HrPayslipRepository payslips;
    private HrEmployeeRepository employees;
    private HrEmp201Repository emp201s;
    private HrMedicalSchemeMembershipRepository memberships;
    private StatutoryRulesService rulesService;
    private PayrollImpactService service;
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private static final LocalDate PAY_DATE = LocalDate.of(2026, 10, 31);

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        payRuns = mock(HrPayRunRepository.class);
        payslips = mock(HrPayslipRepository.class);
        employees = mock(HrEmployeeRepository.class);
        emp201s = mock(HrEmp201Repository.class);
        memberships = mock(HrMedicalSchemeMembershipRepository.class);
        rulesService = mock(StatutoryRulesService.class);
        service = new PayrollImpactService(payRuns, payslips, employees, emp201s, memberships, rulesService);
        when(rulesService.find(2027)).thenReturn(Optional.of(PayrollServiceTest.rules2027()));
        when(memberships.forEmployees(any(), any())).thenReturn(List.of());
        when(emp201s.forPayRun(any(), any())).thenReturn(List.of());
    }

    private HrPayRun run(int labelledYear) {
        HrPayRun run = HrPayRun.create(tenant, "PR00001", LocalDate.of(2026, 10, 1), PAY_DATE, PAY_DATE, labelledYear);
        run.markProcessing();
        run.complete(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1);
        when(payRuns.findAllByTenant(any(), any())).thenReturn(new PageImpl<>(List.of(run)));
        when(payRuns.findByTenantAndId(any(), any())).thenReturn(Optional.of(run));
        return run;
    }

    private HrEmployee employee(String medicalAid) {
        HrEmployee e = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", d("30003.00"), "MONTHLY");
        e.setDateOfBirth(LocalDate.of(1990, 6, 1));
        e.setMedicalAidContribution(d(medicalAid));
        when(employees.findAllById(any())).thenReturn(List.of(e));
        return e;
    }

    private HrPayslip slip(HrPayRun run, HrEmployee emp, String medicalAid, String recordedPaye) {
        HrPayslip s = HrPayslip.create(tenant, run.getId(), emp.getId(), d("30003.00"), BigDecimal.ZERO, d(medicalAid), BigDecimal.ZERO);
        s.applyCalculations(d(recordedPaye), d("177.12"), d("177.12"), BigDecimal.ZERO, d("360036"), d("74001.36"), d("17820"), 2026);
        when(payslips.findByPayRun(run.getId())).thenReturn(List.of(s));
        return s;
    }

    @Test
    void aPayslipThatMatchesTheRulesIsNoDifference() {
        HrPayRun run = run(2027); HrEmployee emp = employee("0"); slip(run, emp, "0", "4681.78");
        PayrollImpactResponse.Run r = service.report(tenant, null).runs().get(0);
        assertThat(r.verdict()).isEqualTo("NO_DIFFERENCE");
        assertThat(r.noDifference()).isEqualTo(1);
        assertThat(r.lines()).isNull();   // lines only when one run is asked for
    }

    @Test
    void aCreditGivenToSomeoneWithNoMedicalAidIsAConfirmedDifference() {
        HrPayRun run = run(2026); HrEmployee emp = employee("0"); slip(run, emp, "0", "4305.78");   // the old code took R376 off for everyone
        PayrollImpactResponse.Run r = service.report(tenant, run.getId()).runs().get(0);
        assertThat(r.verdict()).isEqualTo("CONFIRMED_DIFFERENCE");
        assertThat(r.payeDifference()).isEqualByComparingTo("376.00");
        assertThat(r.correctTaxYear()).isEqualTo(2027);
        assertThat(r.recordedTaxYear()).isEqualTo(2026);
        PayrollImpactResponse.Line l = r.lines().get(0);
        assertThat(l.recalculatedPaye()).isEqualByComparingTo("4681.78");
        assertThat(l.reasons().stream().anyMatch(s -> s.contains("labelled tax year 2026"))).isTrue();
    }

    @Test
    void aDifferenceThatDependsOnAGuessedMembershipIsOnlyPotential() {
        HrPayRun run = run(2027); HrEmployee emp = employee("1200.00"); slip(run, emp, "1200.00", "4000.00");
        PayrollImpactResponse.Run r = service.report(tenant, run.getId()).runs().get(0);
        assertThat(r.verdict()).isEqualTo("POTENTIAL_DIFFERENCE");
        assertThat(r.lines().get(0).medicalAssumed()).isTrue();
        assertThat(r.lines().get(0).medicalMembers()).isEqualTo(1);
    }

    @Test
    void missingRulesNeedReviewAndNothingIsRecalculated() {
        HrPayRun run = run(2027); HrEmployee emp = employee("0"); slip(run, emp, "0", "4681.78");
        when(rulesService.find(2027)).thenReturn(Optional.empty());
        PayrollImpactResponse.Run r = service.report(tenant, run.getId()).runs().get(0);
        assertThat(r.verdict()).isEqualTo("REVIEW_REQUIRED");
        assertThat(r.lines().get(0).recalculatedPaye()).isNull();
    }

    @Test
    void aMissingEmployeeNeedsReview() {
        HrPayRun run = run(2027); HrEmployee emp = employee("0"); slip(run, emp, "0", "4681.78");
        when(employees.findAllById(any())).thenReturn(List.of());
        assertThat(service.report(tenant, run.getId()).runs().get(0).verdict()).isEqualTo("REVIEW_REQUIRED");
    }

    @Test
    void aDraftWithNoPayslipsIsLeftOut() {
        HrPayRun run = run(2027);
        when(payslips.findByPayRun(run.getId())).thenReturn(List.of());
        assertThat(service.report(tenant, null).runs()).isEmpty();
    }

    @Test
    void aProcessedRunSaysWhatMayHaveToHappenAndThatNothingWasChanged() {
        HrPayRun run = run(2026); HrEmployee emp = employee("0"); slip(run, emp, "0", "4305.78");
        PayrollImpactResponse resp = service.report(tenant, null);
        assertThat(resp.runsWithDifferences()).isEqualTo(1);
        assertThat(resp.runs().get(0).action()).contains("correction through an approved adjustment").contains("Nothing has been changed");
        assertThat(resp.notes().get(0)).contains("Nothing was changed");
        verify(payslips, never()).save(any());
        verify(payRuns, never()).save(any());
    }

    @Test
    void anUnknownRunIsNotFound() {
        when(payRuns.findByTenantAndId(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report(tenant, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }
}
