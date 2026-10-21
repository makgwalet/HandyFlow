package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.CreatePayRunRequest;
import za.co.handyflow.platform.hr.dto.PayRunResponse;
import za.co.handyflow.platform.shared.StatutoryYearRules;
import za.co.handyflow.platform.shared.StatutoryYearRules.Bracket;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Pay run creation and processing: the tax year comes from the pay date, missing rules stop the run, nothing is guessed. */
class PayrollServiceTest {

    private HrPayRunRepository payRunRepo;
    private HrPayslipRepository payslipRepo;
    private HrEmp201Repository emp201Repo;
    private HrEmployeeRepository employeeRepo;
    private PayRunNumberGenerator numberGen;
    private StatutoryRulesService rulesService;
    private MedicalSchemeService medicalSchemeService;
    private PayrollService service;

    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    static StatutoryYearRules rules2027() {
        return new StatutoryYearRules(2027, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28),
                List.of(new Bracket(d("0"), d("245100"), d("0"), d("18")),
                        new Bracket(d("245100"), d("383100"), d("44118"), d("26")),
                        new Bracket(d("383100"), null, d("79998"), d("31"))),
                d("17820"), d("9765"), d("3249"), d("376"), d("376"), d("254"),
                d("17712"), d("0.01"), d("0.01"), d("0.01"), d("500000"), d("430000"), d("0.275"), d("0.8"));
    }

    @BeforeEach
    void setUp() {
        payRunRepo = mock(HrPayRunRepository.class);
        payslipRepo = mock(HrPayslipRepository.class);
        emp201Repo = mock(HrEmp201Repository.class);
        employeeRepo = mock(HrEmployeeRepository.class);
        numberGen = mock(PayRunNumberGenerator.class);
        rulesService = mock(StatutoryRulesService.class);
        medicalSchemeService = mock(MedicalSchemeService.class);
        service = new PayrollService(payRunRepo, payslipRepo, emp201Repo, employeeRepo, new PayrollEngine(), numberGen,
                rulesService, medicalSchemeService);
        when(numberGen.next(any(), any())).thenReturn("PR00001");
        when(payRunRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payslipRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payslipRepo.findByPayRun(any())).thenReturn(List.of());
        when(payslipRepo.sumYtdGross(any(), anyInt(), any())).thenReturn(BigDecimal.ZERO);
        when(payslipRepo.sumYtdPaye(any(), anyInt(), any())).thenReturn(BigDecimal.ZERO);
        when(payslipRepo.sumYtdUif(any(), anyInt(), any())).thenReturn(BigDecimal.ZERO);
    }

    private HrEmployee employee(String salary) {
        HrEmployee e = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", LocalDate.of(2024, 1, 1), "PERMANENT", d(salary), "MONTHLY");
        e.setDateOfBirth(LocalDate.of(1990, 6, 1));
        return e;
    }

    private HrPayRun draftRun(LocalDate payDate, int labelledYear) {
        HrPayRun run = HrPayRun.create(tenant, "PR00001", payDate.withDayOfMonth(1), payDate, payDate, labelledYear);
        when(payRunRepo.findByTenantAndId(any(), any())).thenReturn(Optional.of(run));
        return run;
    }

    // ── Creating a run ─────────────────────────────────────────────────────────

    @Test
    void taxYearComesFromThePayDateAndIsNamedByTheYearItEnds() {
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        PayRunResponse r = service.createPayRun(tenant, new CreatePayRunRequest(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), null));
        assertThat(r.taxYear()).isEqualTo(2027);
        verify(rulesService).rulesFor(2027);
    }

    @Test
    void januaryAndFebruaryBelongToTheYearThatEndsInThem() {
        when(rulesService.rulesFor(2026)).thenReturn(rules2027());
        PayRunResponse r = service.createPayRun(tenant, new CreatePayRunRequest(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31), null));
        assertThat(r.taxYear()).isEqualTo(2026);
    }

    @Test
    void aRunIsRefusedUpFrontWhenItsTaxYearHasNoApprovedRules() {
        when(rulesService.rulesFor(2027)).thenThrow(new IllegalStateException("There are no approved statutory rules for tax year 2027"));
        assertThatThrownBy(() -> service.createPayRun(tenant, new CreatePayRunRequest(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), null)))
                .hasMessageContaining("no approved statutory rules");
        verify(payRunRepo, never()).save(any());
    }

    @Test
    void refusesAPeriodThatEndsBeforeItStartsAndAPayDateBeforeThePeriod() {
        assertThatThrownBy(() -> service.createPayRun(tenant, new CreatePayRunRequest(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null)))
                .hasMessageContaining("cannot end before");
        assertThatThrownBy(() -> service.createPayRun(tenant, new CreatePayRunRequest(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 9, 30), null)))
                .hasMessageContaining("pay date");
        verify(payRunRepo, never()).save(any());
    }

    // ── Processing a run ───────────────────────────────────────────────────────

    @Test
    void processingStopsBeforeTouchingAnythingWhenTheRulesAreMissing() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        when(rulesService.rulesFor(2027)).thenThrow(new IllegalStateException("There are no approved statutory rules for tax year 2027"));
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID())).hasMessageContaining("no approved statutory rules");
        verify(payslipRepo, never()).deleteAll(any());
        verify(payslipRepo, never()).save(any());
        verify(payRunRepo, never()).save(any());
    }

    @Test
    void processingCalculatesWithThePayDatesRulesAndNoMedicalCreditWithoutMembers() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID());

        // 30 003 x 12 = 360 036; tax 74 001.36 - 17 820 = 56 181.36 / 12 = 4 681.78, with no medical credit
        assertThat(r.totalPaye()).isEqualByComparingTo("4681.78");
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void aCreditThatRestedOnAGuessIsReportedAsAWarning() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(1, true)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID());

        assertThat(r.totalPaye()).isEqualByComparingTo("4305.78");   // 4 681.78 less R376
        assertThat(r.warnings().size()).isEqualTo(1);
        assertThat(r.warnings().get(0)).contains("EMP-1").contains("no medical scheme membership");
    }

    @Test
    void aRunWithTheWrongLabelIsCalculatedUnderTheRightYearAndSaysSo() {
        draftRun(LocalDate.of(2026, 10, 31), 2026);   // labelled the way the old code labelled October 2026
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID());

        assertThat(r.warnings().get(0)).contains("labelled tax year 2026").contains("2027");
        verify(rulesService).rulesFor(2027);
    }

    @Test
    void onlyDraftRunsCanBeProcessed() {
        HrPayRun run = draftRun(LocalDate.of(2026, 10, 31), 2027);
        run.markProcessing();
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID())).hasMessageContaining("Only DRAFT");
        verifyNoInteractions(rulesService);
    }
}
