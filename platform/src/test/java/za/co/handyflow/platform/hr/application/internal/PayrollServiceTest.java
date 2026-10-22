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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private HrPayRunEventRepository eventRepo;
    private OvertimeService overtimeService;
    private BenefitService benefitService;
    private za.co.handyflow.platform.shared.PublicHolidayRepository holidayRepo;
    private HrLeaveRequestRepository leaveRepo;
    private LeaveService leaveService;
    private PayrollService service;
    private final UUID maker = UUID.randomUUID();

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
        eventRepo = mock(HrPayRunEventRepository.class);
        overtimeService = mock(OvertimeService.class);
        benefitService = mock(BenefitService.class);
        holidayRepo = mock(za.co.handyflow.platform.shared.PublicHolidayRepository.class);
        leaveRepo = mock(HrLeaveRequestRepository.class);
        leaveService = mock(LeaveService.class);
        when(holidayRepo.datesBetween(any(), any())).thenReturn(List.of());
        when(leaveRepo.approvedUnpaidIn(any(), any(), any())).thenReturn(List.of());
        when(employeeRepo.terminatedForPay(any(), any(), any(), any())).thenReturn(List.of());
        when(benefitService.forRun(any(), any())).thenReturn(Map.of());
        when(overtimeService.takeFor(any(), any(), any())).thenReturn(Map.of());
        when(overtimeService.waitingCount(any(), any())).thenReturn(0);
        service = new PayrollService(payRunRepo, payslipRepo, emp201Repo, employeeRepo, new PayrollEngine(), numberGen,
                rulesService, medicalSchemeService, eventRepo, mock(EmployeeChangeService.class), overtimeService,
                holidayRepo, leaveRepo, leaveService, benefitService);
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
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID(), maker)).hasMessageContaining("no approved statutory rules");
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

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        // 30 003 x 12 = 360 036; tax 74 001.36 - 17 820 = 56 181.36 / 12 = 4 681.78, with no medical credit
        assertThat(r.totalPaye()).isEqualByComparingTo("4681.78");
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void anEmployerMedicalContributionRaisesTheTaxButNotThePay() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));
        when(benefitService.forRun(any(), any())).thenReturn(Map.of(emp.getId(), new BenefitRules.Monthly(d("3000"), BigDecimal.ZERO, BigDecimal.ZERO)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        // 360 036 + 36 000 = 396 036; tax 79 998 + 31% x 12 936 = 84 008.16 - 17 820 = 66 188.16 / 12 = 5 515.68
        assertThat(r.totalPaye()).isEqualByComparingTo("5515.68");
        assertThat(r.totalGross()).isEqualByComparingTo("30003.00");
        assertThat(r.warnings()).anyMatch(w -> w.contains("taxable benefits of 3000"));
        HrPayslip slip = savedSlip().getValue();
        assertThat(slip.getEmployerMedical()).isEqualByComparingTo("3000");
        assertThat(slip.getEmployerRetirement()).isEqualByComparingTo("0");
    }

    @Test
    void aCreditThatRestedOnAGuessIsReportedAsAWarning() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(1, true)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        assertThat(r.totalPaye()).isEqualByComparingTo("4305.78");   // 4 681.78 less R376
        assertThat(r.warnings().size()).isEqualTo(1);
        assertThat(r.warnings().get(0)).contains("EMP-1").contains("no medical scheme membership");
    }

    @Test
    void approvedOvertimeIsPaidTaxedAndTakenByTheRun() {
        HrPayRun run = draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));
        HrOvertimeClaim claim = HrOvertimeClaim.of(tenant.getValue(), emp.getId(), LocalDate.of(2026, 10, 12), "OVERTIME", 600,
                d("1.5"), d("100.0000"), d("1000.00"), maker);
        when(overtimeService.takeFor(any(), any(), eq(LocalDate.of(2026, 10, 31)))).thenReturn(Map.of(emp.getId(), List.of(claim)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        org.mockito.ArgumentCaptor<HrPayslip> slip = org.mockito.ArgumentCaptor.forClass(HrPayslip.class);
        verify(payslipRepo).save(slip.capture());
        assertThat(slip.getValue().getGrossSalary()).isEqualByComparingTo("30003.00");
        assertThat(slip.getValue().getOvertimeAmount()).isEqualByComparingTo("1000.00");
        assertThat(slip.getValue().getTotalEarnings()).isEqualByComparingTo("31003.00");
        // 31 003 x 12 = 372 036; tax 77 121.36 - 17 820 = 59 301.36 / 12 = 4 941.78
        assertThat(r.totalPaye()).isEqualByComparingTo("4941.78");
        assertThat(r.totalGross()).isEqualByComparingTo("31003.00");
        assertThat(r.warnings().stream().anyMatch(w -> w.contains("Overtime for 1 employee"))).isTrue();
        verify(overtimeService).markTaken(eq(List.of(claim)), eq(run.getId()));
    }

    @Test
    void overtimeStillWaitingForApprovalIsReportedAndNotPaid() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));
        when(overtimeService.waitingCount(any(), any())).thenReturn(2);

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        assertThat(r.totalGross()).isEqualByComparingTo("30003.00");
        assertThat(r.warnings().stream().anyMatch(w -> w.contains("2 overtime claim(s)"))).isTrue();
    }

    @Test
    void aRunWithTheWrongLabelIsCalculatedUnderTheRightYearAndSaysSo() {
        draftRun(LocalDate.of(2026, 10, 31), 2026);   // labelled the way the old code labelled October 2026
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        assertThat(r.warnings().get(0)).contains("labelled tax year 2026").contains("2027");
        verify(rulesService).rulesFor(2027);
    }

    @Test
    void onlyDraftRunsCanBeProcessed() {
        HrPayRun run = draftRun(LocalDate.of(2026, 10, 31), 2027);
        run.markProcessing();
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID(), maker)).hasMessageContaining("can only be calculated");
        verifyNoInteractions(rulesService);
    }

    // ── Pay for the period ─────────────────────────────────────────────────────

    private HrEmployee employeeFrom(String salary, LocalDate start) {
        HrEmployee e = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", start, "PERMANENT", d(salary), "MONTHLY");
        e.setDateOfBirth(LocalDate.of(1990, 6, 1));
        return e;
    }

    private org.mockito.ArgumentCaptor<HrPayslip> savedSlip() {
        org.mockito.ArgumentCaptor<HrPayslip> slip = org.mockito.ArgumentCaptor.forClass(HrPayslip.class);
        verify(payslipRepo).save(slip.capture());
        return slip;
    }

    @Test
    void aJoinerIsPaidFromTheirStartDate() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);      // October 2026 has 22 working days
        HrEmployee emp = employeeFrom("22000.00", LocalDate.of(2026, 10, 15));   // 12 of them are after the 15th
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        HrPayslip slip = savedSlip().getValue();
        assertThat(slip.getGrossSalary()).isEqualByComparingTo("12000.00");
        assertThat(slip.getProrationAmount()).isEqualByComparingTo("10000.00");
        assertThat(slip.getDaysPaid()).isEqualByComparingTo("12");
        assertThat(slip.getDaysInPeriod()).isEqualTo(22);
        assertThat(r.totalGross()).isEqualByComparingTo("12000.00");
        assertThat(r.warnings().stream().anyMatch(w -> w.contains("12 of 22 working days"))).isTrue();
    }

    @Test
    void approvedUnpaidLeaveIsDeductedAtTheDailyRateOfThePeriod() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employeeFrom("22000.00", LocalDate.of(2024, 1, 1));
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));
        HrLeaveRequest leave = mock(HrLeaveRequest.class);
        when(leave.getEmployeeId()).thenReturn(emp.getId());
        when(leave.getStartDate()).thenReturn(LocalDate.of(2026, 10, 12));
        when(leave.getEndDate()).thenReturn(LocalDate.of(2026, 10, 16));
        when(leaveRepo.approvedUnpaidIn(any(), any(), any())).thenReturn(List.of(leave));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        HrPayslip slip = savedSlip().getValue();
        assertThat(slip.getUnpaidLeaveDays()).isEqualByComparingTo("5");
        assertThat(slip.getUnpaidLeaveAmount()).isEqualByComparingTo("5000.00");
        assertThat(slip.getGrossSalary()).isEqualByComparingTo("17000.00");
        assertThat(r.warnings().stream().anyMatch(w -> w.contains("5 day(s) of unpaid leave deducted"))).isTrue();
    }

    @Test
    void aLeaversLastPayIsProratedAndIncludesTheirAnnualLeave() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employeeFrom("22000.00", LocalDate.of(2024, 1, 1));
        emp.terminate(LocalDate.of(2026, 10, 15));        // 11 working days to the 15th inclusive: 1,2,5-9,12-15
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of());
        when(employeeRepo.terminatedForPay(any(), any(), any(), any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));
        when(leaveService.annualLeaveOwing(any(), eq(emp), eq(LocalDate.of(2026, 10, 15)))).thenReturn(d("10.50"));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        HrPayslip last = savedSlip().getValue();
        assertThat(last.isFinalPay()).isTrue();
        // 22 000 / 22 = 1 000 a day, 11 days employed: 11 000. Daily rate for leave: 22 000 x 12 / 260 = 1 015.38; x 10.5 = 10 661.49
        assertThat(last.getGrossSalary()).isEqualByComparingTo("11000.00");
        assertThat(last.getLeavePayoutDays()).isEqualByComparingTo("10.50");
        assertThat(last.getLeavePayoutAmount()).isEqualByComparingTo("10661.49");
        assertThat(last.getTotalEarnings()).isEqualByComparingTo("21661.49");
        assertThat(r.warnings().stream().anyMatch(w -> w.contains("last pay") && w.contains("10.50 day(s) of annual leave"))).isTrue();
    }

    @Test
    void aLeaverWhoseEndDateIsStillAheadIsPaidInFullAndGetsNoPayout() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employeeFrom("22000.00", LocalDate.of(2024, 1, 1));
        emp.terminate(LocalDate.of(2026, 11, 20));
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of());
        when(employeeRepo.terminatedForPay(any(), any(), any(), any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));

        service.processPayRun(tenant, UUID.randomUUID(), maker);

        HrPayslip slip = savedSlip().getValue();
        assertThat(slip.getGrossSalary()).isEqualByComparingTo("22000.00");
        assertThat(slip.isFinalPay()).isFalse();
        verifyNoInteractions(leaveService);
    }

    @Test
    void anEmployeeWhoStartsAfterThePeriodIsNotPaid() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee later = employeeFrom("22000.00", LocalDate.of(2026, 11, 3));
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(later));
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID(), maker)).hasMessageContaining("No active employees");
        verify(payslipRepo, never()).save(any());
    }

    // ── Workflow and what employees see ─────────────────────────────────────────

    @Test
    void aCalculatedRunWaitsForApprovalAndRemembersWhoCalculatedIt() {
        HrPayRun run = draftRun(LocalDate.of(2026, 10, 31), 2027);
        HrEmployee emp = employee("30003.00");
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        when(employeeRepo.findAllActiveList(any())).thenReturn(List.of(emp));
        when(medicalSchemeService.membersOn(any(), any(), any())).thenReturn(Map.of(emp.getId(), new MedicalSchemeRules.Members(0, false)));

        PayRunResponse r = service.processPayRun(tenant, UUID.randomUUID(), maker);

        assertThat(r.status()).isEqualTo("PREPARED");
        assertThat(r.preparedBy()).isEqualTo(maker);
        assertThat(r.approvedBy()).isNull();
        assertThat(run.getStatus()).isEqualTo("PREPARED");
        verify(eventRepo).save(argThat((HrPayRunEvent e) -> e.getAction().equals("CALCULATED") && maker.equals(e.getActorId())
                && e.getFromStatus().equals("DRAFT") && e.getToStatus().equals("PREPARED")));
    }

    @Test
    void anApprovedRunCannotBeRecalculatedAndItsPayslipsStay() {
        HrPayRun run = draftRun(LocalDate.of(2026, 10, 31), 2027);
        run.prepared(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1, maker);
        run.approve(UUID.randomUUID());
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID(), maker)).hasMessageContaining("can only be calculated");
        verify(payslipRepo, never()).deleteAll(any());
        verifyNoInteractions(rulesService);
    }

    @Test
    void aFinalisedRunIsLocked() {
        HrPayRun run = draftRun(LocalDate.of(2026, 10, 31), 2027);
        run.prepared(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1, maker);
        run.approve(UUID.randomUUID());
        run.finalise(UUID.randomUUID());
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID(), maker)).hasMessageContaining("locked");
    }

    @Test
    void aRunWhoseEmp201WasSubmittedIsNotRecalculated() {
        draftRun(LocalDate.of(2026, 10, 31), 2027);
        when(rulesService.rulesFor(2027)).thenReturn(rules2027());
        HrEmp201 submitted = mock(HrEmp201.class);
        when(submitted.getStatus()).thenReturn("SUBMITTED");
        when(emp201Repo.forPayRun(any(), any())).thenReturn(List.of(submitted));
        assertThatThrownBy(() -> service.processPayRun(tenant, UUID.randomUUID(), maker)).hasMessageContaining("already been submitted");
        verify(payslipRepo, never()).save(any());
        verify(emp201Repo, never()).deleteAll(any());
    }

    @Test
    void anEmployeeSeesOnlyPayslipsOfIssuedRuns() {
        UUID empId = UUID.randomUUID();
        HrEmployee emp = employee("30003.00");
        when(employeeRepo.findActiveById(any(), eq(empId))).thenReturn(Optional.of(emp));
        HrPayRun prepared = HrPayRun.create(tenant, "PR00001", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 25), 2027);
        prepared.prepared(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1, maker);
        HrPayRun finalised = HrPayRun.create(tenant, "PR00002", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 25), 2027);
        finalised.prepared(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1, maker);
        finalised.approve(UUID.randomUUID());
        finalised.finalise(UUID.randomUUID());
        HrPayslip hidden = mock(HrPayslip.class), shown = mock(HrPayslip.class);
        when(hidden.getPayRunId()).thenReturn(prepared.getId());
        when(shown.getPayRunId()).thenReturn(finalised.getId());
        when(payslipRepo.findByEmployee(empId)).thenReturn(List.of(hidden, shown));
        when(payRunRepo.findAllById(any())).thenReturn(List.of(prepared, finalised));
        when(payRunRepo.findById(any())).thenReturn(Optional.of(finalised));

        assertThat(service.getEmployeePayslips(tenant, empId, true).size()).isEqualTo(1);
        assertThat(service.getEmployeePayslips(tenant, empId, false).size()).isEqualTo(2);
    }
}
