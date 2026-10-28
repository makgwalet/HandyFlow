package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.hr.domain.repository.*;
import za.co.handyflow.platform.hr.dto.*;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.PublicHolidayRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The leave ledger against an in-memory ledger, so postings and balances are checked together. */
class LeaveServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final LocalDate today = LocalDate.now();
    private final UUID approver = UUID.randomUUID();
    private final UUID employeeLogin = UUID.randomUUID();

    final List<HrLeaveLedger> ledger = new ArrayList<>();
    final Map<UUID, HrLeaveRequest> requests = new HashMap<>();
    HrLeaveLedgerRepository ledgerRepo;
    HrLeaveRequestRepository requestRepo;
    HrLeaveSettingsRepository settingsRepo;
    HrEmployeeRepository employeeRepo;
    HrPayrollSettingsRepository payrollSettings;
    PublicHolidayRepository holidays;
    EmployeeViewService viewService;
    LeaveService service;
    HrLeavePolicyRepository policyRepo;
    HrEmployee emp;

    @BeforeEach
    void setUp() {
        ledgerRepo = mock(HrLeaveLedgerRepository.class);
        requestRepo = mock(HrLeaveRequestRepository.class);
        settingsRepo = mock(HrLeaveSettingsRepository.class);
        policyRepo = mock(HrLeavePolicyRepository.class);
        when(policyRepo.findById(any())).thenReturn(Optional.empty());
        employeeRepo = mock(HrEmployeeRepository.class);
        payrollSettings = mock(HrPayrollSettingsRepository.class);
        holidays = mock(PublicHolidayRepository.class);
        viewService = mock(EmployeeViewService.class);
        service = new LeaveService(ledgerRepo, requestRepo, settingsRepo, employeeRepo, payrollSettings, holidays, viewService,
                mock(NotificationService.class), mock(TenantAdminRecipients.class), policyRepo);

        emp = HrEmployee.create(tenant, "EMP-1", "Jane", "Dlamini", today.minusYears(3), "PERMANENT", new BigDecimal("30000"), "MONTHLY");
        when(employeeRepo.findActiveById(any(), eq(emp.getId()))).thenReturn(Optional.of(emp));
        when(employeeRepo.findByUserId(any(), any())).thenReturn(Optional.empty());
        when(settingsRepo.findById(any())).thenReturn(Optional.of(HrLeaveSettings.startingOn(tenant.getValue(), LocalDate.of(today.getYear() - 1, 1, 1))));
        when(payrollSettings.findById(any())).thenReturn(Optional.empty());
        when(holidays.datesBetween(any(), any())).thenReturn(List.of());
        when(requestRepo.overlapping(any(), any(), any(), any())).thenAnswer(i -> requests.values().stream()
                .filter(r -> List.of("PENDING", "APPROVED").contains(r.getStatus()))
                .filter(r -> LeaveRules.overlaps(r.getStartDate(), r.getEndDate(), i.getArgument(2), i.getArgument(3))).toList());
        when(requestRepo.save(any())).thenAnswer(i -> { HrLeaveRequest r = i.getArgument(0); requests.put(r.getId(), r); return r; });
        when(requestRepo.findByTenantAndId(any(), any())).thenAnswer(i -> Optional.ofNullable(requests.get((UUID) i.getArgument(1))));

        when(ledgerRepo.save(any())).thenAnswer(i -> { HrLeaveLedger l = i.getArgument(0); ledger.add(l); return l; });
        when(ledgerRepo.window(any(), any(), any(), any(), any())).thenAnswer(i -> ledger.stream()
                .filter(l -> l.getLeaveType().equals(i.getArgument(2)))
                .filter(l -> !l.getEntryDate().isBefore(i.getArgument(3)) && !l.getEntryDate().isAfter(i.getArgument(4))).toList());
        when(ledgerRepo.between(any(), any(), any(), any())).thenAnswer(i -> ledger.stream()
                .filter(l -> !l.getEntryDate().isBefore(i.getArgument(2)) && !l.getEntryDate().isAfter(i.getArgument(3))).toList());
        when(ledgerRepo.forRequest(any(), any())).thenAnswer(i -> ledger.stream().filter(l -> i.getArgument(1).equals(l.getRequestId())).toList());
        when(ledgerRepo.countKey(any(), any(), any(), any(), any())).thenAnswer(i -> ledger.stream()
                .filter(l -> l.getLeaveType().equals(i.getArgument(2)) && l.getKind().equals(i.getArgument(3)) && i.getArgument(4).equals(l.getPeriodKey())).count());
        when(ledgerRepo.firstEntry(any(), any(), any())).thenAnswer(i -> ledger.stream()
                .filter(l -> l.getLeaveType().equals(i.getArgument(2))).map(HrLeaveLedger::getEntryDate).min(Comparator.naturalOrder()));
    }

    /** A weekday some days ahead, so a request is never in the past or on a weekend. */
    private LocalDate weekday(int daysAhead) {
        LocalDate d = today.plusDays(daysAhead);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }

    private SubmitLeaveRequest annual(LocalDate from, LocalDate to) { return new SubmitLeaveRequest("ANNUAL", from, to, "Holiday"); }

    private LeaveBalanceResponse balance(String type) {
        return service.balances(tenant, emp.getId(), today.getYear()).stream().filter(b -> b.leaveType().equals(type)).findFirst().orElseThrow();
    }

    private long count(String kind) { return ledger.stream().filter(l -> l.getKind().equals(kind)).count(); }

    // ── Accrual ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aBalanceExistsInANewYearWithoutAnyoneSeedingIt() {
        LeaveBalanceResponse annual = balance("ANNUAL");

        assertThat(annual.entitledDays().signum()).isEqualTo(1);
        assertThat(count("ACCRUAL") > 0).isTrue();
    }

    @Test
    void unusedAnnualLeaveCarriesIntoTheNewYear() {
        balance("ANNUAL");

        assertThat(ledger.stream().anyMatch(l -> l.getKind().equals("CARRY_FORWARD") && l.getPeriodKey().equals("carry:" + today.getYear()))).isTrue();
    }

    @Test
    void readingABalanceTwiceDoesNotPostTwice() {
        balance("ANNUAL");
        int after = ledger.size();
        balance("ANNUAL");

        assertThat(ledger.size()).isEqualTo(after);
    }

    @Test
    void sickLeaveIsThirtyForTheCycle() {
        assertThat(balance("SICK").entitledDays()).isEqualByComparingTo("30");
    }

    @Test
    void familyResponsibilityIsThreeAYear() {
        assertThat(balance("FAMILY_RESPONSIBILITY").entitledDays()).isEqualByComparingTo("3");
    }

    // ── Requesting ──────────────────────────────────────────────────────────────────────────────

    @Test
    void aRequestHoldsItsDaysUntilItIsDecided() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));

        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.daysRequested()).isEqualByComparingTo("1");
        LeaveBalanceResponse b = balance("ANNUAL");
        assertThat(b.pendingDays()).isEqualByComparingTo("1");
        assertThat(b.takenDays()).isEqualByComparingTo("0");
    }

    @Test
    void aHalfDayHoldsHalf() {
        LocalDate d = weekday(7);
        service.submit(tenant, emp.getId(), new SubmitLeaveRequest("ANNUAL", d, d, "Appointment", true, false));

        assertThat(balance("ANNUAL").pendingDays()).isEqualByComparingTo("0.5");
    }

    @Test
    void aRequestBiggerThanTheBalanceIsRefused() {
        LocalDate from = weekday(7);
        assertThatThrownBy(() -> service.submit(tenant, emp.getId(), annual(from, from.plusDays(300))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Not enough annual leave");
        assertThat(requests.isEmpty()).isTrue();
    }

    @Test
    void overlappingLeaveIsRefused() {
        LocalDate d = weekday(7);
        service.submit(tenant, emp.getId(), annual(d, d));

        assertThatThrownBy(() -> service.submit(tenant, emp.getId(), new SubmitLeaveRequest("SICK", d, d, "Flu")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("overlaps");
    }

    @Test
    void aWeekendOnlyRequestIsRefused() {
        LocalDate saturday = today.plusDays(1);
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) saturday = saturday.plusDays(1);
        LocalDate sat = saturday;

        assertThatThrownBy(() -> service.submit(tenant, emp.getId(), annual(sat, sat.plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no working day");
    }

    @Test
    void aPublicHolidayIsNotALeaveDay() {
        LocalDate mon = weekday(14);
        while (mon.getDayOfWeek() != DayOfWeek.MONDAY) mon = mon.plusDays(1);
        when(holidays.datesBetween(any(), any())).thenReturn(List.of(mon.plusDays(1)));

        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(mon, mon.plusDays(2)));

        assertThat(r.daysRequested()).isEqualByComparingTo("2");
    }

    @Test
    void annualLeaveCannotStartInAClosedYear() {
        LocalDate lastYear = LocalDate.of(today.getYear() - 1, 12, 29);
        assertThatThrownBy(() -> service.submit(tenant, emp.getId(), annual(lastYear, lastYear)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("closed");
    }

    @Test
    void aTerminatedEmployeeCannotRequestLeave() {
        emp.terminate(today.minusDays(1), "Resigned", approver);
        LocalDate d = weekday(7);
        assertThatThrownBy(() -> service.submit(tenant, emp.getId(), annual(d, d)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active");
    }

    @Test
    void sickLeaveOverTwoDaysIsFlaggedForACertificate() {
        LocalDate mon = weekday(7);
        while (mon.getDayOfWeek() != DayOfWeek.MONDAY) mon = mon.plusDays(1);

        LeaveRequestResponse r = service.submit(tenant, emp.getId(), new SubmitLeaveRequest("SICK", mon, mon.plusDays(3), "Flu"));

        assertThat(r.certificateExpected()).isTrue();
        assertThat(r.medicalCertificate()).isFalse();
    }

    @Test
    void unpaidLeaveHasNoBalanceToExceed() {
        LocalDate from = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), new SubmitLeaveRequest("UNPAID", from, from.plusDays(40), "Sabbatical"));

        assertThat(r.status()).isEqualTo("PENDING");
    }

    // ── Deciding ────────────────────────────────────────────────────────────────────────────────

    @Test
    void approvalTurnsHeldDaysIntoDaysTaken() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));

        service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE"));

        LeaveBalanceResponse b = balance("ANNUAL");
        assertThat(b.pendingDays()).isEqualByComparingTo("0");
        assertThat(b.takenDays()).isEqualByComparingTo("1");
    }

    @Test
    void approvingTwiceIsRefused() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));
        service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE"));

        assertThatThrownBy(() -> service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE"))).isInstanceOf(IllegalStateException.class);
        assertThat(balance("ANNUAL").takenDays()).isEqualByComparingTo("1");
    }

    @Test
    void nobodyApprovesTheirOwnLeave() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));
        emp.setUserId(employeeLogin);
        when(employeeRepo.findByUserId(any(), eq(employeeLogin))).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> service.approve(tenant, r.id(), employeeLogin, Set.of("LEAVE_APPROVE"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aTeamManagerDecidesOnlyForTheirTeam() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));
        when(viewService.teamOf(any(), eq(approver))).thenReturn(Set.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE", "HR_TEAM_READ")))
                .isInstanceOf(AccessDeniedException.class);

        when(viewService.teamOf(any(), eq(approver))).thenReturn(Set.of(emp.getId()));
        assertThat(service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE", "HR_TEAM_READ")).status()).isEqualTo("APPROVED");
    }

    @Test
    void rejectingNeedsAReasonAndReleasesTheDays() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));

        assertThatThrownBy(() -> service.reject(tenant, r.id(), approver, " ", Set.of("LEAVE_APPROVE"))).isInstanceOf(IllegalArgumentException.class);
        service.reject(tenant, r.id(), approver, "Busy period", Set.of("LEAVE_APPROVE"));

        assertThat(balance("ANNUAL").pendingDays()).isEqualByComparingTo("0");
    }

    @Test
    void anApprovedRequestCannotBeRejectedAfterwards() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));
        service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE"));

        assertThatThrownBy(() -> service.reject(tenant, r.id(), approver, "Changed my mind", Set.of("LEAVE_APPROVE")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(balance("ANNUAL").pendingDays()).isEqualByComparingTo("0");
    }

    // ── Cancelling ──────────────────────────────────────────────────────────────────────────────

    @Test
    void cancellingApprovedLeaveThatHasNotStartedGivesTheDaysBack() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));
        service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE"));

        service.cancel(tenant, r.id(), approver, null, Set.of("LEAVE_APPROVE"));

        LeaveBalanceResponse b = balance("ANNUAL");
        assertThat(b.takenDays()).isEqualByComparingTo("0");
        assertThat(requests.get(r.id()).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAWaitingRequestReleasesTheHold() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));

        service.cancel(tenant, r.id(), approver, null, Set.of("LEAVE_APPROVE"));

        assertThat(balance("ANNUAL").pendingDays()).isEqualByComparingTo("0");
    }

    @Test
    void leaveThatHasStartedNeedsAnAdjusterAndAReason() {
        LocalDate d = weekday(7);
        LeaveRequestResponse r = service.submit(tenant, emp.getId(), annual(d, d));
        service.approve(tenant, r.id(), approver, Set.of("LEAVE_APPROVE"));
        HrLeaveRequest started = requests.get(r.id());
        // Move the leave into the past to represent leave that has already begun.
        org.springframework.test.util.ReflectionTestUtils.setField(started, "startDate", today.minusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(started, "endDate", today.minusDays(1));

        assertThatThrownBy(() -> service.cancel(tenant, r.id(), approver, "Not taken", Set.of("LEAVE_APPROVE")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancel(tenant, r.id(), approver, " ", Set.of("LEAVE_ADJUST")))
                .isInstanceOf(IllegalArgumentException.class);
        service.cancel(tenant, r.id(), approver, "Worked instead", Set.of("LEAVE_ADJUST"));
        assertThat(count("REVERSAL")).isEqualTo(1L);
    }

    // ── Adjusting ───────────────────────────────────────────────────────────────────────────────

    @Test
    void hrCanAdjustABalanceWithAReason() {
        LeaveBalanceResponse before = balance("ANNUAL");

        service.adjust(tenant, emp.getId(), new LeaveAdjustmentRequest("ANNUAL", today, new BigDecimal("2"), "Leave owed from last year"), approver);

        assertThat(balance("ANNUAL").availableDays()).isEqualByComparingTo(before.availableDays().add(new BigDecimal("2")));
    }

    @Test
    void anAdjustmentNeedsAReasonAndADateThatHasCome() {
        assertThatThrownBy(() -> service.adjust(tenant, emp.getId(), new LeaveAdjustmentRequest("ANNUAL", today, BigDecimal.ONE, " "), approver))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjust(tenant, emp.getId(), new LeaveAdjustmentRequest("ANNUAL", today.plusDays(2), BigDecimal.ONE, "Why"), approver))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.adjust(tenant, emp.getId(), new LeaveAdjustmentRequest("ANNUAL", today, BigDecimal.ZERO, "Why"), approver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Carry-over policy ───────────────────────────────────────────────────────────────────────

    private void capOf(String days, int fromYear) {
        HrLeavePolicy policy = HrLeavePolicy.none(tenant.getValue());
        policy.set(new BigDecimal(days), fromYear, "Leave policy 2026, signed by staff", approver);
        when(policyRepo.findById(any())).thenReturn(Optional.of(policy));
    }

    @Test
    void withoutAPolicyEverythingIsCarriedAsBefore() {
        balance("ANNUAL");

        assertThat(count("EXPIRY")).isZero();
        HrLeaveLedger carry = ledger.stream().filter(l -> l.getKind().equals("CARRY_FORWARD")).findFirst().orElseThrow();
        assertThat(carry.getDays().compareTo(new BigDecimal("15"))).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aCapForfeitsTheExcessWhenTheYearRollsOverAndCarriesOnlyTheCap() {
        capOf("5", today.getYear());
        balance("ANNUAL");

        HrLeaveLedger lost = ledger.stream().filter(l -> l.getKind().equals("EXPIRY")).findFirst().orElseThrow();
        assertThat(lost.getEntryDate()).isEqualTo(LocalDate.of(today.getYear() - 1, 12, 31));
        assertThat(lost.getDays().signum()).isEqualTo(-1);
        assertThat(lost.getNote()).contains("5-day limit");
        HrLeaveLedger carry = ledger.stream().filter(l -> l.getKind().equals("CARRY_FORWARD")).findFirst().orElseThrow();
        assertThat(carry.getDays().compareTo(new BigDecimal("5"))).isZero();
    }

    @Test
    void aCapThatStartsLaterDoesNotTouchTheRolloverThatHasHappened() {
        capOf("5", today.getYear() + 1);
        balance("ANNUAL");

        assertThat(count("EXPIRY")).isZero();
    }

    @Test
    void forfeitingIsPostedOnceEvenIfTheBalanceIsReadAgain() {
        capOf("5", today.getYear());
        balance("ANNUAL");
        long first = count("EXPIRY");
        balance("ANNUAL");

        assertThat(first).isEqualTo(1);
        assertThat(count("EXPIRY")).isEqualTo(1);
    }

    @Test
    void forfeitedDaysShowInLastYearsBalance() {
        capOf("5", today.getYear());
        balance("ANNUAL");
        LeaveBalanceResponse lastYear = service.balances(tenant, emp.getId(), today.getYear() - 1).stream()
                .filter(b -> b.leaveType().equals("ANNUAL")).findFirst().orElseThrow();

        assertThat(lastYear.availableDays().compareTo(new BigDecimal("5"))).isLessThanOrEqualTo(0);
    }

    // ── Carried leave that expires ──────────────────────────────────────────────────────────────

    private void expiryOf(int months, int fromYear) {
        HrLeavePolicy policy = HrLeavePolicy.none(tenant.getValue());
        policy.set(null, months, fromYear, "Leave policy 2026, signed by staff", approver);
        when(policyRepo.findById(any())).thenReturn(Optional.of(policy));
    }

    @Test
    void carriedLeaveIsKeptUntilItsExpiryDate() {
        expiryOf(24, today.getYear());
        balance("ANNUAL");

        assertThat(count("EXPIRY")).isZero();
    }

    @Test
    void carriedLeaveNotUsedByTheExpiryDateIsTakenOffOnceAndDatedThatDay() {
        org.junit.jupiter.api.Assumptions.assumeTrue(today.getMonthValue() > 6);
        expiryOf(6, today.getYear());
        balance("ANNUAL");
        balance("ANNUAL");

        assertThat(count("EXPIRY")).isEqualTo(1);
        HrLeaveLedger lost = ledger.stream().filter(l -> l.getKind().equals("EXPIRY")).findFirst().orElseThrow();
        assertThat(lost.getEntryDate()).isEqualTo(LocalDate.of(today.getYear(), 6, 30));
        assertThat(lost.getDays().signum()).isEqualTo(-1);
        assertThat(lost.getNote()).contains("6 months");
    }

    @Test
    void anExpiryThatStartsNextYearDoesNotTouchLeaveAlreadyCarried() {
        expiryOf(6, today.getYear() + 1);
        balance("ANNUAL");

        assertThat(count("EXPIRY")).isZero();
    }

    @Test
    void aPolicyWithOnlyAnExpiryIsSavedAndShownBack() {
        var saved = service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(null, 12, today.getYear() + 1, "Policy 1"), approver);

        assertThat(saved.carryExpiryMonths()).isEqualTo(12);
        assertThatThrownBy(() -> service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(null, 3, today.getYear() + 1, "Policy 1"), approver))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePolicyCannotBeSavedWithoutTheAgreementOrForThisYear() {
        assertThatThrownBy(() -> service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(new BigDecimal("5"), today.getYear() + 1, " "), approver))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("written agreement");
        assertThatThrownBy(() -> service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(new BigDecimal("5"), today.getYear(), "Policy 1"), approver))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("next year");
        assertThatThrownBy(() -> service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(new BigDecimal("5"), today.getYear() + 1, "Policy 1"), null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(policyRepo, never()).save(any());
    }

    @Test
    void aValidPolicyIsSavedAndCanBeRemoved() {
        service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(new BigDecimal("5"), LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")).getYear() + 1, "Policy 1"), approver);
        verify(policyRepo).save(argThat(p -> p.getCarryOverCapDays().compareTo(new BigDecimal("5")) == 0 && "Policy 1".equals(p.getAgreementRef())));
        service.updatePolicy(tenant, new LeavePolicyDtos.PolicyRequest(null, null, null), approver);
        verify(policyRepo, times(2)).save(any());
    }

    @Test
    void theAtRiskListIsEmptyWhenNoLimitAppliesYet() {
        assertThat(service.atRisk(tenant).employees()).isEmpty();
    }
}
