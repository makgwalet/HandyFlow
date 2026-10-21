package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.hr.domain.model.HrErCharge;
import za.co.handyflow.platform.hr.domain.model.HrErStep;
import za.co.handyflow.platform.hr.dto.ErDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.hr.application.internal.ErHarness.*;

class ErCaseServiceTest {

    private final ErHarness h = new ErHarness();

    private static OpenRequest open(UUID emp, String type, String category) {
        return new OpenRequest(emp, type, category, "Repeated lateness", "Late on three days", today().minusDays(3), null, null, null);
    }

    // ── Opening ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void openingACaseNeedsThePermissionToManage() {
        UUID emp = h.employee();
        assertThatThrownBy(() -> h.service.open(TENANT, open(emp, "DISCIPLINARY", "LATENESS"), HR_USER, DECIDE)).isInstanceOf(AccessDeniedException.class);
        verify(h.cases, never()).save(any());
    }

    @Test
    void aCaseIsOpenedWithItsNumberAndTheOpenerAsOwner() {
        UUID emp = h.employee();
        h.service.open(TENANT, open(emp, "DISCIPLINARY", "LATENESS"), HR_USER, MANAGE);
        ArgumentCaptor<HrErCase> saved = ArgumentCaptor.forClass(HrErCase.class);
        verify(h.cases).save(saved.capture());
        assertThat(saved.getValue().getCaseNumber().endsWith("-0007")).isTrue();
        assertThat(saved.getValue().getStatus()).isEqualTo("REPORTED");
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo(HR_USER);
        assertThat(saved.getValue().isConfidential()).isFalse();
        verify(h.events).save(any());
    }

    @Test
    void aCategoryThatDoesNotBelongToTheTypeIsRefused() {
        UUID emp = h.employee();
        assertThatThrownBy(() -> h.service.open(TENANT, open(emp, "GRIEVANCE", "THEFT"), HR_USER, MANAGE)).isInstanceOf(IllegalArgumentException.class);
        verify(h.cases, never()).save(any());
    }

    @Test
    void anIllHealthCaseIsConfidentialAndGetsItsSteps() {
        UUID emp = h.employee();
        h.service.open(TENANT, open(emp, "INCAPACITY", "ILL_HEALTH"), HR_USER, MANAGE);
        ArgumentCaptor<HrErCase> saved = ArgumentCaptor.forClass(HrErCase.class);
        verify(h.cases).save(saved.capture());
        assertThat(saved.getValue().isConfidential()).isTrue();
        verify(h.steps, times(3)).save(any(HrErStep.class));
    }

    @Test
    void theEmployeeCannotBringTheChargesAgainstThemselves() {
        UUID emp = h.employee();
        OpenRequest req = new OpenRequest(emp, "DISCIPLINARY", "LATENESS", "t", "d", null, emp, null, null);
        assertThatThrownBy(() -> h.service.open(TENANT, req, HR_USER, MANAGE)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("themselves");
    }

    // ── Who may see and work a case ───────────────────────────────────────────────────────────────

    @Test
    void nobodyWorksACaseAboutThemselves() {
        UUID emp = h.employee();
        h.signedInAs(HR_USER, emp);
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.service.investigate(TENANT, c.getId(), HR_USER, MANAGE)).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("about yourself");
        assertThat(c.getStatus()).isEqualTo("REPORTED");
    }

    @Test
    void aConfidentialCaseLooksLikeItDoesNotExistToSomeoneWithoutSensitiveAccess() {
        UUID emp = h.employee();
        HrErCase c = h.newCase("INCAPACITY", "ILL_HEALTH", emp);
        c.setConfidential(true);
        c.setOwner(UUID.randomUUID());
        assertThatThrownBy(() -> h.service.get(TENANT, c.getId(), MANAGE, HR_USER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theListLeavesOutConfidentialCasesTheViewerMayNotSee() {
        UUID emp = h.employee();
        HrErCase open = h.discipline(emp);
        HrErCase secret = h.newCase("INCAPACITY", "ILL_HEALTH", emp);
        secret.setConfidential(true);
        secret.setOwner(UUID.randomUUID());
        when(h.cases.forTenant(TENANT)).thenReturn(List.of(open, secret));
        h.service.list(TENANT, null, null, MANAGE, HR_USER);
        ArgumentCaptor<List> shown = ArgumentCaptor.forClass(List.class);
        verify(h.views).rows(any(), shown.capture(), any());
        assertThat(shown.getValue()).hasSize(1);
    }

    @Test
    void listingNeedsSomeEmployeeRelationsPermission() {
        assertThatThrownBy(() -> h.service.list(TENANT, null, null, NOTHING, HR_USER)).isInstanceOf(AccessDeniedException.class);
    }

    // ── Notice and hearing ────────────────────────────────────────────────────────────────────────

    private NoticeRequest notice(UUID chair, int daysAhead, LocalTime at, List<String> charges) {
        return new NoticeRequest(today().plusDays(daysAhead), at, "Boardroom", chair, null, charges);
    }

    @Test
    void aNoticeGivesTheChargesTheTimeAndTheVenueAndMovesTheCaseOn() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.discipline(emp);
        c.moveTo("INVESTIGATING");
        h.service.issueNotice(TENANT, c.getId(), notice(chair, 5, LocalTime.of(10, 0), List.of("Late on 3 days", "Left early")), HR_USER, MANAGE);
        assertThat(c.getStatus()).isEqualTo("NOTICE_ISSUED");
        assertThat(c.getChairId()).isEqualTo(chair);
        assertThat(c.getHearingVenue()).isEqualTo("Boardroom");
        assertThat(c.getNoticeIssuedAt()).isNotNull();
        verify(h.charges, times(2)).save(any(HrErCharge.class));
    }

    @Test
    void aNoticeWithTooLittleTimeIsRefused() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.discipline(emp);
        c.moveTo("INVESTIGATING");
        assertThatThrownBy(() -> h.service.issueNotice(TENANT, c.getId(), notice(chair, 0, LocalTime.of(23, 59), List.of("Late")), HR_USER, MANAGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getStatus()).isEqualTo("INVESTIGATING");
    }

    @Test
    void theChairCannotBeTheEmployee() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        c.moveTo("INVESTIGATING");
        assertThatThrownBy(() -> h.service.issueNotice(TENANT, c.getId(), notice(emp, 5, LocalTime.of(10, 0), List.of("Late")), HR_USER, MANAGE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("their own hearing");
    }

    @Test
    void aGrievanceHasNoHearing() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.newCase("GRIEVANCE", "PAY_AND_BENEFITS", emp);
        c.moveTo("INVESTIGATING");
        assertThatThrownBy(() -> h.service.issueNotice(TENANT, c.getId(), notice(chair, 5, LocalTime.of(10, 0), List.of()), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aNoticeCannotBeChangedOnceAFindingHasBeenGiven() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.discipline(emp);
        c.issueNotice(java.time.Instant.now(), today().plusDays(5), LocalTime.of(10, 0), "Room", chair, null);
        HrErCharge charge = HrErCharge.of(TENANT.getValue(), c.getId(), 1, "Late");
        charge.find("GUILTY", null);
        when(h.charges.forCase(c.getId())).thenReturn(List.of(charge));
        assertThatThrownBy(() -> h.service.issueNotice(TENANT, c.getId(), notice(chair, 6, LocalTime.of(10, 0), List.of("Late")), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("finding");
    }

    @Test
    void aHearingIsRecordedWithWhoCameAndWhatWasSaid() {
        UUID emp = h.employee(), chair = h.employee();
        HrErCase c = h.discipline(emp);
        c.issueNotice(java.time.Instant.now(), today().plusDays(5), LocalTime.of(10, 0), "Room", chair, null);
        h.service.recordHearing(TENANT, c.getId(), new HearingRequest(today(), true, "Shop steward", "Both sides heard"), HR_USER, MANAGE);
        assertThat(c.getStatus()).isEqualTo("HEARING_HELD");
        assertThat(c.getRepresentative()).isEqualTo("Shop steward");
        assertThat(c.getEmployeeAttended()).isTrue();
    }

    @Test
    void aHearingCannotBeRecordedBeforeTheCaseHasANotice() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.service.recordHearing(TENANT, c.getId(), new HearingRequest(today(), true, null, "x"), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── After the decision ────────────────────────────────────────────────────────────────────────

    private HrErCase decided(UUID emp, String outcome, int decidedDaysAgo) {
        HrErCase c = h.discipline(emp);
        c.decide(outcome, today().minusDays(decidedDaysAgo), "Reasons", HR_USER, "VERBAL_WARNING".equals(outcome) ? 3 : null, null);
        return c;
    }

    @Test
    void aDismissalCannotBeCommunicatedWithoutTheCcmaRightsBeingExplained() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "DISMISSAL", 1);
        assertThatThrownBy(() -> h.service.communicate(TENANT, c.getId(), new CommunicateRequest(today(), false), HR_USER, MANAGE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CCMA");
        h.service.communicate(TENANT, c.getId(), new CommunicateRequest(today(), true), HR_USER, MANAGE);
        assertThat(c.getCommunicatedOn()).isEqualTo(today());
        assertThat(c.getDismissalRightsExplainedOn()).isEqualTo(today());
    }

    @Test
    void theOutcomeCanOnlyBeCommunicatedOnce() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 1);
        c.communicate(today(), false);
        assertThatThrownBy(() -> h.service.communicate(TENANT, c.getId(), new CommunicateRequest(today(), false), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already told");
    }

    @Test
    void aCaseCannotBeClosedWhileTheEmployeeCanStillAppeal() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 2);
        c.communicate(today().minusDays(1), false);
        assertThatThrownBy(() -> h.service.close(TENANT, c.getId(), new ReasonRequest("Done"), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("can appeal until");
        assertThat(c.getStatus()).isEqualTo("DECIDED");
    }

    @Test
    void aCaseClosesOnceTheAppealPeriodHasPassed() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 40);
        c.communicate(today().minusDays(30), false);
        h.service.close(TENANT, c.getId(), new ReasonRequest("Appeal period over"), HR_USER, MANAGE);
        assertThat(c.getStatus()).isEqualTo("CLOSED");
        assertThat(c.getClosedReason()).isEqualTo("Appeal period over");
    }

    @Test
    void aCaseCannotBeClosedBeforeTheEmployeeWasToldTheOutcome() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 40);
        assertThatThrownBy(() -> h.service.close(TENANT, c.getId(), new ReasonRequest("Done"), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Tell the employee");
    }

    @Test
    void closingAMatterWithNoCaseToAnswerNeedsSomeoneWhoMayDecide() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.service.close(TENANT, c.getId(), new ReasonRequest("No evidence"), HR_USER, MANAGE)).isInstanceOf(AccessDeniedException.class);
        h.service.close(TENANT, c.getId(), new ReasonRequest("No evidence"), HR_USER, java.util.Set.of("HR_ER_MANAGE", "HR_ER_DECIDE"));
        assertThat(c.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void aCaseCanBeWithdrawnBeforeADecisionButNotAfter() {
        UUID emp = h.employee();
        HrErCase open = h.discipline(emp);
        h.service.withdraw(TENANT, open.getId(), new ReasonRequest("Raised in error"), HR_USER, MANAGE);
        assertThat(open.getStatus()).isEqualTo("WITHDRAWN");
        HrErCase done = decided(emp, "VERBAL_WARNING", 1);
        assertThatThrownBy(() -> h.service.withdraw(TENANT, done.getId(), new ReasonRequest("x"), HR_USER, MANAGE)).isInstanceOf(IllegalStateException.class);
    }

    // ── Appeals ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void anAppealMovesTheCaseToAppealedAndIsRecorded() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 3);
        c.communicate(today().minusDays(2), false);
        h.service.lodgeAppeal(TENANT, c.getId(), new AppealRequest(today(), "The warning was too harsh", false), HR_USER, MANAGE);
        assertThat(c.getStatus()).isEqualTo("APPEALED");
        assertThat(c.getAppealGrounds()).isEqualTo("The warning was too harsh");
    }

    @Test
    void aLateAppealIsRefusedUnlessSomeoneWhoMayDecideAcceptsIt() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 30);
        c.communicate(today().minusDays(20), false);
        assertThatThrownBy(() -> h.service.lodgeAppeal(TENANT, c.getId(), new AppealRequest(today(), "Grounds", false), HR_USER, MANAGE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appeal period");
        assertThatThrownBy(() -> h.service.lodgeAppeal(TENANT, c.getId(), new AppealRequest(today(), "Grounds", true), HR_USER, MANAGE))
                .isInstanceOf(AccessDeniedException.class);
        h.service.lodgeAppeal(TENANT, c.getId(), new AppealRequest(today(), "Grounds", true), HR_USER,
                java.util.Set.of("HR_ER_MANAGE", "HR_ER_DECIDE"));
        assertThat(c.getStatus()).isEqualTo("APPEALED");
    }

    @Test
    void anAppealCannotBeLodgedTwice() {
        UUID emp = h.employee();
        HrErCase c = decided(emp, "VERBAL_WARNING", 3);
        c.communicate(today().minusDays(2), false);
        c.lodgeAppeal(today(), "First");
        assertThatThrownBy(() -> h.service.lodgeAppeal(TENANT, c.getId(), new AppealRequest(today(), "Second", false), HR_USER, MANAGE))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── The employee's side ───────────────────────────────────────────────────────────────────────

    @Test
    void anEmployeeLodgesTheirOwnGrievanceAndHrAssignsIt() {
        UUID emp = h.employee();
        UUID me = UUID.randomUUID();
        h.signedInAs(me, emp);
        h.service.lodgeGrievance(TENANT, new LodgeGrievanceRequest("PAY_AND_BENEFITS", "My overtime was not paid", today().minusDays(2), null), me);
        ArgumentCaptor<HrErCase> saved = ArgumentCaptor.forClass(HrErCase.class);
        verify(h.cases).save(saved.capture());
        assertThat(saved.getValue().getCaseType()).isEqualTo("GRIEVANCE");
        assertThat(saved.getValue().getEmployeeId()).isEqualTo(emp);
        assertThat(saved.getValue().getOwnerUserId()).isNull();
        assertThat(saved.getValue().getOpenedBy()).isEqualTo(me);
    }

    @Test
    void aLoginNotLinkedToAnEmployeeCannotLodgeAGrievance() {
        assertThatThrownBy(() -> h.service.lodgeGrievance(TENANT, new LodgeGrievanceRequest("PAY_AND_BENEFITS", "x", null, null), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not linked");
    }

    @Test
    void anEmployeeDoesNotSeeAMatterBeforeTheyHaveBeenGivenNotice() {
        UUID emp = h.employee();
        UUID me = UUID.randomUUID();
        h.signedInAs(me, emp);
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.service.mineOne(TENANT, c.getId(), me)).isInstanceOf(ResourceNotFoundException.class);
        when(h.cases.forEmployee(TENANT, emp)).thenReturn(List.of(c));
        assertThat(h.service.mine(TENANT, me)).isEmpty();
    }

    @Test
    void anEmployeeCannotOpenSomeoneElsesCase() {
        UUID emp = h.employee(), other = h.employee();
        UUID me = UUID.randomUUID();
        h.signedInAs(me, other);
        HrErCase c = h.newCase("GRIEVANCE", "PAY_AND_BENEFITS", emp);
        assertThatThrownBy(() -> h.service.mineOne(TENANT, c.getId(), me)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void acknowledgingNeedsAnOutcomeAndRecordsReceiptNotAgreement() {
        UUID emp = h.employee();
        UUID me = UUID.randomUUID();
        h.signedInAs(me, emp);
        HrErCase c = h.newCase("GRIEVANCE", "PAY_AND_BENEFITS", emp);
        assertThatThrownBy(() -> h.service.acknowledge(TENANT, c.getId(), me)).isInstanceOf(IllegalStateException.class);
        c.decide("UPHELD", today().minusDays(1), "Will be paid", HR_USER, null, null);
        c.communicate(today(), false);
        h.service.acknowledge(TENANT, c.getId(), me);
        assertThat(c.isAcknowledged()).isTrue();
        assertThat(c.getStatus()).isEqualTo("DECIDED");
    }

    @Test
    void anEmployeeAppealsTheirOwnOutcomeInTime() {
        UUID emp = h.employee();
        UUID me = UUID.randomUUID();
        h.signedInAs(me, emp);
        HrErCase c = h.newCase("GRIEVANCE", "PAY_AND_BENEFITS", emp);
        c.decide("NOT_UPHELD", today().minusDays(3), "No basis", HR_USER, null, null);
        c.communicate(today().minusDays(2), false);
        h.service.appealOwn(TENANT, c.getId(), new AppealRequest(null, "I disagree because", null), me);
        assertThat(c.getStatus()).isEqualTo("APPEALED");
    }

    @Test
    void anEmployeeCannotAppealOutOfTime() {
        UUID emp = h.employee();
        UUID me = UUID.randomUUID();
        h.signedInAs(me, emp);
        HrErCase c = h.newCase("GRIEVANCE", "PAY_AND_BENEFITS", emp);
        c.decide("NOT_UPHELD", today().minusDays(40), "No basis", HR_USER, null, null);
        c.communicate(today().minusDays(30), false);
        assertThatThrownBy(() -> h.service.appealOwn(TENANT, c.getId(), new AppealRequest(null, "Too late", null), me))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("appeal period");
    }

    // ── Suspension, confidentiality, warnings ─────────────────────────────────────────────────────

    @Test
    void aPrecautionarySuspensionIsRecordedWithoutTouchingTheEmployeeOrTheirPay() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        h.service.suspend(TENANT, c.getId(), new SuspendRequest(today(), null, "Risk of interfering with witnesses"), HR_USER, MANAGE);
        assertThat(c.getSuspendedFrom()).isEqualTo(today());
        verify(h.employees, never()).save(any());
    }

    @Test
    void suspensionNeedsAReason() {
        UUID emp = h.employee();
        HrErCase c = h.discipline(emp);
        assertThatThrownBy(() -> h.service.suspend(TENANT, c.getId(), new SuspendRequest(today(), null, " "), HR_USER, MANAGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(c.getSuspendedFrom()).isNull();
    }

    @Test
    void aCaseThatIsAlwaysConfidentialCannotBeMadeOpen() {
        UUID emp = h.employee();
        HrErCase c = h.newCase("INCAPACITY", "ILL_HEALTH", emp);
        c.setConfidential(true);
        assertThatThrownBy(() -> h.service.setConfidential(TENANT, c.getId(), new FlagRequest(false), HR_USER, java.util.Set.of("HR_ER_MANAGE", "HR_SENSITIVE_READ")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(c.isConfidential()).isTrue();
    }

    @Test
    void warningsListOnlyTheSanctionsThatWereGiven() {
        UUID emp = h.employee();
        HrErCase warned = decided(emp, "VERBAL_WARNING", 5);
        HrErCase dismissed = decided(emp, "DISMISSAL", 4);
        HrErCase open = h.discipline(emp);
        when(h.cases.forEmployee(TENANT, emp)).thenReturn(List.of(open, dismissed, warned));
        var out = h.service.warnings(TENANT, emp, MANAGE, HR_USER);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).sanction()).isEqualTo("VERBAL_WARNING");
    }

    @Test
    void aCaseOpenedFromSecurityKeepsItsTitleAndNeedsNoPermissionCheckHere() {
        UUID emp = h.employee();
        HrErCase c = h.service.openDisciplinary(TENANT, emp, "Absenteeism", "Substantiated complaint SEC-1", today().minusDays(4), HR_USER);
        assertThat(c.getTitle()).isEqualTo("Absenteeism");
        assertThat(c.getCaseType()).isEqualTo("DISCIPLINARY");
        assertThat(c.getStatus()).isEqualTo("REPORTED");
        verify(h.cases).save(c);
    }

    @Test
    void aLongReferralIsCutToFitTheColumns() {
        UUID emp = h.employee();
        HrErCase c = h.service.openDisciplinary(TENANT, emp, "T".repeat(300), "D".repeat(5000), null, HR_USER);
        assertThat(c.getTitle()).hasSize(200);
        assertThat(c.getDescription()).hasSize(4000);
    }
}
