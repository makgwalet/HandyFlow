package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrEmployee;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.dto.ErDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.hr.application.internal.ErHarness.*;

class ErFollowUpServiceTest {

    private final ErHarness h = new ErHarness();
    private final EmployeeAuditService audit = mock(EmployeeAuditService.class);
    private final ErCaseViews views = mock(ErCaseViews.class);
    private final ErTerminationService termination = new ErTerminationService(h.support, h.employees, audit, views, org.mockito.Mockito.mock(TerminationFollowUp.class));

    private HrEmployee realEmployee() {
        HrEmployee e = HrEmployee.create(TENANT, "EMP-9", "Sipho", "Nkosi", LocalDate.of(2024, 1, 1), "PERMANENT", new BigDecimal("20000"), "MONTHLY");
        when(h.employees.findActiveById(TENANT, e.getId())).thenReturn(java.util.Optional.of(e));
        return e;
    }

    private HrErCase dismissed(HrEmployee e, boolean told) {
        HrErCase c = h.newCase("DISCIPLINARY", "THEFT", e.getId());
        c.decide("DISMISSAL", today().minusDays(3), "Found guilty", HR_USER, null, "Denied it");
        if (told) c.communicate(today().minusDays(2), true);
        return c;
    }

    // ── Ending employment ─────────────────────────────────────────────────────────────────────────

    @Test
    void aCommunicatedDismissalEndsEmploymentAndKeepsWhoDidIt() {
        HrEmployee e = realEmployee();
        HrErCase c = dismissed(e, true);

        termination.endEmployment(TENANT, c.getId(), new EndEmploymentRequest(today().minusDays(1)), HR_USER, DECIDE);

        assertThat(e.getStatus()).isEqualTo("TERMINATED");
        assertThat(e.getEndDate()).isEqualTo(today().minusDays(1));
        assertThat(e.getTerminationReason()).contains(c.getCaseNumber());
        assertThat(e.getTerminatedBy()).isEqualTo(HR_USER);
        verify(h.employees).save(e);
        verify(audit).record(eq(TENANT), eq(e.getId()), eq("TERMINATED"), any(), eq(HR_USER));
        verify(h.events).save(any());
    }

    @Test
    void ItNeedsThePermissionToDecide() {
        HrEmployee e = realEmployee();
        HrErCase c = dismissed(e, true);
        assertThatThrownBy(() -> termination.endEmployment(TENANT, c.getId(), new EndEmploymentRequest(today()), HR_USER, MANAGE))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void ItIsRefusedBeforeTheEmployeeWasToldOrWhenAnAppealIsWaiting() {
        HrEmployee e = realEmployee();
        HrErCase notTold = dismissed(e, false);
        assertThatThrownBy(() -> termination.endEmployment(TENANT, notTold.getId(), new EndEmploymentRequest(today()), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Tell the employee");
        HrErCase appealed = dismissed(e, true);
        appealed.lodgeAppeal(today().minusDays(1), "Unfair");
        assertThatThrownBy(() -> termination.endEmployment(TENANT, appealed.getId(), new EndEmploymentRequest(today()), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appeal");
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
        verify(h.employees, never()).save(any());
    }

    @Test
    void aWarningDoesNotEndEmploymentAndALastDayInTheFutureIsRefused() {
        HrEmployee e = realEmployee();
        HrErCase warning = h.newCase("DISCIPLINARY", "LATENESS", e.getId());
        warning.decide("FINAL_WRITTEN_WARNING", today().minusDays(3), "ok", HR_USER, 12, "sorry");
        warning.communicate(today().minusDays(2), false);
        assertThatThrownBy(() -> termination.endEmployment(TENANT, warning.getId(), new EndEmploymentRequest(today()), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dismissal");
        HrErCase c = dismissed(e, true);
        assertThatThrownBy(() -> termination.endEmployment(TENANT, c.getId(), new EndEmploymentRequest(today().plusDays(5)), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void ItCannotBeDoneTwice() {
        HrEmployee e = realEmployee();
        HrErCase c = dismissed(e, true);
        termination.endEmployment(TENANT, c.getId(), new EndEmploymentRequest(today().minusDays(1)), HR_USER, DECIDE);
        assertThatThrownBy(() -> termination.endEmployment(TENANT, c.getId(), new EndEmploymentRequest(today().minusDays(1)), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already ended");
    }

    // ── Validity of an old warning ────────────────────────────────────────────────────────────────

    @Test
    void anOldWarningGetsItsValidityFromTheDecisionDate() {
        UUID emp = h.employee();
        HrErCase c = h.newCase("DISCIPLINARY", "LATENESS", emp);
        c.decide("WRITTEN_WARNING", today().minusMonths(2), "old", HR_USER, null, null);

        h.service.setWarningValidity(TENANT, c.getId(), new ValidityRequest(6), HR_USER, DECIDE);

        assertThat(c.getWarningMonths()).isEqualTo(6);
        assertThat(c.getWarningValidUntil()).isEqualTo(today().minusMonths(2).plusMonths(6));
        verify(h.events).save(any());
    }

    @Test
    void validityNeedsThePermissionToDecideAndCannotBeChangedAfterwards() {
        UUID emp = h.employee();
        HrErCase c = h.newCase("DISCIPLINARY", "LATENESS", emp);
        c.decide("VERBAL_WARNING", today().minusMonths(1), "old", HR_USER, null, null);
        assertThatThrownBy(() -> h.service.setWarningValidity(TENANT, c.getId(), new ValidityRequest(3), HR_USER, MANAGE))
                .isInstanceOf(AccessDeniedException.class);
        h.service.setWarningValidity(TENANT, c.getId(), new ValidityRequest(3), HR_USER, DECIDE);
        assertThatThrownBy(() -> h.service.setWarningValidity(TENANT, c.getId(), new ValidityRequest(12), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already has");
        assertThat(c.getWarningMonths()).isEqualTo(3);
    }

    // ── Public holidays in the grievance deadline ─────────────────────────────────────────────────

    @Test
    void theGrievanceResponseTimeSkipsPublicHolidays() {
        za.co.handyflow.platform.shared.PublicHolidayRepository holidays = mock(za.co.handyflow.platform.shared.PublicHolidayRepository.class);
        when(holidays.datesBetween(any(), any())).thenReturn(java.util.List.of(LocalDate.of(2026, 10, 12)));
        ErCaseViews real = new ErCaseViews(null, null, null, null, null, null, holidays);
        LocalDate friday = LocalDate.of(2026, 10, 9);

        // five working days after Friday 9 October: Tuesday 13th to Monday 19th, because Monday 12th is a holiday
        assertThat(real.responseDue(friday, za.co.handyflow.platform.hr.domain.model.ErPolicy.defaults())).isEqualTo(LocalDate.of(2026, 10, 19));
        when(holidays.datesBetween(any(), any())).thenReturn(java.util.List.of());
        assertThat(real.responseDue(friday, za.co.handyflow.platform.hr.domain.model.ErPolicy.defaults())).isEqualTo(LocalDate.of(2026, 10, 16));
    }
}
