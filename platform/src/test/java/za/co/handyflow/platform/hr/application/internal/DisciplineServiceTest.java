package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.model.HrErCharge;
import za.co.handyflow.platform.hr.dto.ErDtos.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.hr.application.internal.ErHarness.*;

class DisciplineServiceTest {

    private final ErHarness h = new ErHarness();

    private InformalRequest informal(String sanction) {
        return new InformalRequest(sanction, "He said the taxi was late", "Third late arrival this month", null, null);
    }

    // ── Without a hearing ─────────────────────────────────────────────────────────────────────────

    @Test
    void aVerbalWarningTakesTheCompanysDefaultValidity() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        h.discipline.informal(TENANT, c.getId(), informal("VERBAL_WARNING"), HR_USER, DECIDE);
        assertThat(c.getStatus()).isEqualTo("DECIDED");
        assertThat(c.getOutcome()).isEqualTo("VERBAL_WARNING");
        assertThat(c.getWarningMonths()).isEqualTo(3);
        assertThat(c.getWarningValidUntil()).isEqualTo(today().plusMonths(3));
        assertThat(c.getEmployeeResponse()).contains("taxi");
    }

    @Test
    void theChairCanChooseALongerValidity() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        h.discipline.informal(TENANT, c.getId(), new InformalRequest("WRITTEN_WARNING", "ok", "Repeat", 9, null), HR_USER, DECIDE);
        assertThat(c.getWarningMonths()).isEqualTo(9);
    }

    @Test
    void aFinalWarningNeedsAHearing() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.discipline.informal(TENANT, c.getId(), informal("FINAL_WRITTEN_WARNING"), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hearing");
        assertThat(c.getStatus()).isEqualTo("REPORTED");
    }

    @Test
    void aSanctionNeedsTheEmployeesResponseFirst() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.discipline.informal(TENANT, c.getId(), new InformalRequest("VERBAL_WARNING", " ", "r", null, null), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("what the employee said");
    }

    @Test
    void decidingNeedsThePermissionToDecide() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.discipline.informal(TENANT, c.getId(), informal("VERBAL_WARNING"), HR_USER, MANAGE)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anInformalSanctionCannotBeGivenAfterANoticeOfHearing() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.discipline(emp);
        c.issueNotice(Instant.now(), today().plusDays(5), LocalTime.of(10, 0), "Room", chair, null);
        assertThatThrownBy(() -> h.discipline.informal(TENANT, c.getId(), informal("VERBAL_WARNING"), HR_USER, DECIDE)).isInstanceOf(IllegalStateException.class);
    }

    // ── After a hearing ───────────────────────────────────────────────────────────────────────────

    private HrErCase heard(UUID emp, UUID chair) {
        HrErCase c = h.discipline(emp);
        c.issueNotice(Instant.now(), today(), LocalTime.of(10, 0), "Room", chair, null);
        c.holdHearing(today(), true, null, "Both sides heard");
        return c;
    }

    private HrErCharge charge(HrErCase c, int seq) {
        HrErCharge ch = HrErCharge.of(TENANT.getValue(), c.getId(), seq, "Charge " + seq);
        return ch;
    }

    private DecisionRequest decision(String sanction, Integer months, ChargeFinding... findings) {
        return new DecisionRequest(List.of(findings), sanction, months, "The employee was late without good reason", null);
    }

    @Test
    void everyChargeNeedsAFindingBeforeASanction() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = heard(emp, chair);
        HrErCharge a = charge(c, 1), b = charge(c, 2);
        when(h.charges.forCase(c.getId())).thenReturn(List.of(a, b));
        assertThatThrownBy(() -> h.discipline.decide(TENANT, c.getId(), decision("VERBAL_WARNING", 3, new ChargeFinding(a.getId(), "GUILTY", null)), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("every charge");
        assertThat(c.getStatus()).isEqualTo("HEARING_HELD");
    }

    @Test
    void aGuiltyFindingWithAWarningRecordsFindingsSanctionAndValidity() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = heard(emp, chair);
        HrErCharge a = charge(c, 1), b = charge(c, 2);
        when(h.charges.forCase(c.getId())).thenReturn(List.of(a, b));
        h.discipline.decide(TENANT, c.getId(), decision("WRITTEN_WARNING", null,
                new ChargeFinding(a.getId(), "GUILTY", "Admitted"), new ChargeFinding(b.getId(), "NOT_GUILTY", null)), HR_USER, DECIDE);
        assertThat(c.getStatus()).isEqualTo("DECIDED");
        assertThat(c.getOutcome()).isEqualTo("WRITTEN_WARNING");
        assertThat(c.getWarningMonths()).isEqualTo(6);
        assertThat(a.getFinding()).isEqualTo("GUILTY");
        assertThat(a.getFindingNote()).isEqualTo("Admitted");
        assertThat(b.getFinding()).isEqualTo("NOT_GUILTY");
    }

    @Test
    void notGuiltyOnEveryChargeMeansNoSanctionWhateverWasAsked() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = heard(emp, chair);
        HrErCharge a = charge(c, 1);
        when(h.charges.forCase(c.getId())).thenReturn(List.of(a));
        h.discipline.decide(TENANT, c.getId(), decision("DISMISSAL", null, new ChargeFinding(a.getId(), "NOT_GUILTY", null)), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("NOT_GUILTY");
        assertThat(c.getWarningMonths()).isNull();
    }

    @Test
    void aDismissalNeedsAGuiltyFindingAndCarriesNoValidity() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = heard(emp, chair);
        HrErCharge a = charge(c, 1);
        when(h.charges.forCase(c.getId())).thenReturn(List.of(a));
        h.discipline.decide(TENANT, c.getId(), decision("DISMISSAL", null, new ChargeFinding(a.getId(), "GUILTY", null)), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("DISMISSAL");
        assertThat(c.getWarningValidUntil()).isNull();
    }

    @Test
    void aDecisionCannotBeMadeBeforeTheHearingWasHeld() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.discipline(emp);
        c.issueNotice(Instant.now(), today().plusDays(5), LocalTime.of(10, 0), "Room", chair, null);
        assertThatThrownBy(() -> h.discipline.decide(TENANT, c.getId(), decision("VERBAL_WARNING", 3), HR_USER, DECIDE)).isInstanceOf(IllegalStateException.class);
    }

    // ── Appeals ───────────────────────────────────────────────────────────────────────────────────

    private HrErCase appealed(UUID emp, UUID chair, String sanction, Integer months) {
        HrErCase c = heard(emp, chair);
        c.decide(sanction, today().minusDays(3), "Reasons", HR_USER, months, null);
        c.communicate(today().minusDays(2), "DISMISSAL".equals(sanction));
        c.lodgeAppeal(today().minusDays(1), "Too harsh");
        return c;
    }

    @Test
    void theChairOfTheHearingCannotDecideTheAppeal() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = appealed(emp, chair, "FINAL_WRITTEN_WARNING", 12);
        assertThatThrownBy(() -> h.discipline.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(chair, null, "CONFIRMED", null, null, "Fair"), HR_USER, DECIDE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("other than the chair");
        assertThat(c.getStatus()).isEqualTo("APPEALED");
    }

    @Test
    void anAppealThatConfirmsClosesTheCaseAndKeepsTheSanction() {
        UUID emp = h.employee(), chair = h.employee(), other = h.employee();
        HrErCase c = appealed(emp, chair, "FINAL_WRITTEN_WARNING", 12);
        h.discipline.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "CONFIRMED", null, null, "The sanction was fair"), HR_USER, DECIDE);
        assertThat(c.getStatus()).isEqualTo("CLOSED");
        assertThat(c.getOutcome()).isEqualTo("FINAL_WRITTEN_WARNING");
        assertThat(c.getAppealOutcome()).isEqualTo("CONFIRMED");
    }

    @Test
    void anAppealThatLowersADismissalReplacesItWithAWarning() {
        UUID emp = h.employee(), chair = h.employee(), other = h.employee();
        HrErCase c = appealed(emp, chair, "DISMISSAL", null);
        h.discipline.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "VARIED", "FINAL_WRITTEN_WARNING", null, "Dismissal was too harsh"),
                HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("FINAL_WRITTEN_WARNING");
        assertThat(c.getWarningMonths()).isEqualTo(12);
        assertThat(c.getWarningValidUntil()).isEqualTo(today().minusDays(3).plusMonths(12));
        assertThat(c.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void anAppealCannotRaiseASanction() {
        UUID emp = h.employee(), chair = h.employee(), other = h.employee();
        HrErCase c = appealed(emp, chair, "VERBAL_WARNING", 3);
        assertThatThrownBy(() -> h.discipline.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "VARIED", "FINAL_WRITTEN_WARNING", 12, "x"),
                HR_USER, DECIDE)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lower");
        assertThat(c.getOutcome()).isEqualTo("VERBAL_WARNING");
    }

    @Test
    void anOverturnedAppealClearsTheSanction() {
        UUID emp = h.employee(), chair = h.employee(), other = h.employee();
        HrErCase c = appealed(emp, chair, "DISMISSAL", null);
        h.discipline.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "OVERTURNED", null, null, "The procedure was unfair"), HR_USER, DECIDE);
        assertThat(c.getOutcome()).isEqualTo("NOT_GUILTY");
        assertThat(c.getAppealOutcome()).isEqualTo("OVERTURNED");
        assertThat(c.getWarningValidUntil()).isNull();
    }

    @Test
    void thereIsNothingToDecideUntilAnAppealIsLodged() {
        UUID emp = h.employee(), chair = h.employee(), other = h.employee();
        HrErCase c = heard(emp, chair);
        c.decide("VERBAL_WARNING", today().minusDays(1), "r", HR_USER, 3, null);
        assertThatThrownBy(() -> h.discipline.decideAppeal(TENANT, c.getId(), new AppealDecisionRequest(other, null, "CONFIRMED", null, null, "x"), HR_USER, DECIDE))
                .isInstanceOf(IllegalStateException.class);
    }
}
