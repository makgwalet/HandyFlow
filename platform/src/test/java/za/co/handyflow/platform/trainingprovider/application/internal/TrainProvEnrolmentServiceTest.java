package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.CoverageView;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvEnrolmentServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final LocalDate START = LocalDate.of(2027, 2, 1);

    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvLearnerApplicationRepository applications;
    @Mock TrainProvLearnerApplicationEventRepository applicationEvents;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerEnrolmentEventRepository enrolmentEvents;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvProgrammeOfferingRepository offerings;
    @Mock TrainProvDeliverySiteRepository sites;
    @Mock TrainProvClientRepository clients;
    @Mock TrainProvAccreditationService accreditation;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvEnrolmentService service;

    private TrainProvLearner learner() {
        return TrainProvLearner.create(TENANT, "LRN-00001", "Test", "Learner", "PASSPORT", "A1234567", null, null, null, null, null, null, null,
                false, null, null, USER, TODAY);
    }

    private TrainProvProgramme programme(String activity, boolean active) {
        String type = "ACCREDITED".equals(activity) ? "SKILLS_PROGRAMME" : "NON_ACCREDITED_COURSE";
        TrainProvProgramme p = TrainProvProgramme.create(TENANT, "P1", "TEST programme", type, activity, "CLASSROOM",
                "ACCREDITED".equals(activity) ? UUID.randomUUID() : null, null, null, USER);
        if (active) {
            p.activate();
        }
        return p;
    }

    private TrainProvDeliverySite site() {
        return TrainProvDeliverySite.create(TENANT, "TEST campus", "CAMPUS", null, null, null, null);
    }

    private TrainProvProgrammeVersion approvedVersion(TrainProvProgramme p) {
        TrainProvProgrammeVersion v = TrainProvProgrammeVersion.draft(TENANT, p.getId(), 1, USER);
        v.approve(UUID.randomUUID(), null);
        return v;
    }

    private void arrange(TrainProvLearner l, TrainProvProgramme p, TrainProvDeliverySite s, boolean offered, String outcome) {
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(versions.findApproved(TENANT, p.getId())).thenReturn(Optional.of(approvedVersion(p)));
        when(sites.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(offerings.liveExists(TENANT, p.getId(), s.getId())).thenReturn(offered);
        if (outcome != null) {
            when(accreditation.programmeCoverage(eq(TENANT), eq(p.getId()), any(), eq(s.getId()), eq(START)))
                    .thenReturn(new CoverageView(p.getId(), s.getId(), START, outcome, outcome.startsWith("COVERED"), "msg"));
        }
    }

    private EnrolRequest enrol(TrainProvLearner l, TrainProvProgramme p, TrainProvDeliverySite s, String override) {
        return new EnrolRequest(l.getId(), p.getId(), s.getId(), null, null, START, null, override);
    }

    @Test
    void enrolsIntoACoveredAccreditedProgrammeAndRecordsTheCoverAnswer() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, "COVERED");
        when(numbers.nextLearnerEnrolmentNumber(TENANT)).thenReturn("ENR-00001");
        EnrolmentView v = service.enrol(TENANT, enrol(l, p, s, null), USER, false, TODAY);
        assertThat(v.coverOutcome()).isEqualTo("COVERED");
        assertThat(v.coverCheckedOn()).isEqualTo(TODAY);
        assertThat(v.status()).isEqualTo("ENROLLED");
        verify(enrolmentEvents).save(any(TrainProvLearnerEnrolmentEvent.class));
    }

    @Test
    void refusesAnAccreditedEnrolmentWithNoCoverForAnOrdinaryUser() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, "SITE_NOT_COVERED");
        assertThatThrownBy(() -> service.enrol(TENANT, enrol(l, p, s, null), USER, false, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(enrolments, never()).save(any());
    }

    @Test
    void refusesAnOverrideFromSomeoneWhoIsNotAnAdministrator() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, "EXPIRED");
        assertThatThrownBy(() -> service.enrol(TENANT, enrol(l, p, s, "Approved by the board in writing"), USER, false, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only an administrator");
        verify(enrolments, never()).save(any());
    }

    @Test
    void anAdministratorCanEnrolWithoutCoverWhenTheyGiveAReason() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, "EXPIRED");
        when(numbers.nextLearnerEnrolmentNumber(TENANT)).thenReturn("ENR-00002");
        EnrolmentView v = service.enrol(TENANT, enrol(l, p, s, "Approved by the board in writing"), USER, true, TODAY);
        assertThat(v.coverOutcome()).isEqualTo("EXPIRED");
        assertThat(v.coverOverrideReason()).isEqualTo("Approved by the board in writing");
    }

    @Test
    void anUnaccreditedProgrammeNeedsNoCoverCheck() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("NON_ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, null);
        when(numbers.nextLearnerEnrolmentNumber(TENANT)).thenReturn("ENR-00003");
        EnrolmentView v = service.enrol(TENANT, enrol(l, p, s, null), USER, false, TODAY);
        assertThat(v.coverOutcome()).isNull();
        verify(accreditation, never()).programmeCoverage(any(), any(), any(), any(), any());
    }

    @Test
    void refusesWhenTheProgrammeIsNotOfferedAtTheSite() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("NON_ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, false, null);
        assertThatThrownBy(() -> service.enrol(TENANT, enrol(l, p, s, null), USER, true, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not offered");
    }

    @Test
    void refusesASecondLiveEnrolmentOnTheSameProgramme() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("NON_ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, null);
        when(enrolments.liveExists(TENANT, l.getId(), p.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.enrol(TENANT, enrol(l, p, s, null), USER, true, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("live enrolment");
        verify(enrolments, never()).save(any());
    }

    @Test
    void anAcceptedApplicationBecomesEnrolledWhenTheLearnerIsEnrolled() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("NON_ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, null);
        TrainProvLearnerApplication a = TrainProvLearnerApplication.create(TENANT, l.getId(), p.getId(), s.getId(), null, null, USER);
        a.moveTo("UNDER_REVIEW", null, null, null, null, USER);
        a.moveTo("ACCEPTED", null, true, "Certificate sighted and copied", null, USER);
        when(applications.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(numbers.nextLearnerEnrolmentNumber(TENANT)).thenReturn("ENR-00004");
        service.enrol(TENANT, new EnrolRequest(l.getId(), p.getId(), s.getId(), a.getId(), null, START, null, null), USER, false, TODAY);
        assertThat(a.getStatus()).isEqualTo("ENROLLED");
    }

    @Test
    void anApplicationFromAnotherLearnerIsRefused() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("NON_ACCREDITED", true);
        TrainProvDeliverySite s = site();
        arrange(l, p, s, true, null);
        TrainProvLearnerApplication a = TrainProvLearnerApplication.create(TENANT, UUID.randomUUID(), p.getId(), s.getId(), null, null, USER);
        when(applications.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.enrol(TENANT, new EnrolRequest(l.getId(), p.getId(), s.getId(), a.getId(), null, START, null, null), USER, true, TODAY))
                .isInstanceOf(HandyFlowException.class);
        verify(enrolments, never()).save(any());
    }

    @Test
    void onlyAnAdministratorCanAcceptSomeoneWhoDoesNotMeetEntry() {
        TrainProvLearnerApplication a = TrainProvLearnerApplication.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, null, null, USER);
        a.moveTo("UNDER_REVIEW", null, null, null, null, USER);
        when(applications.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        ApplicationTransitionRequest r = new ApplicationTransitionRequest("ACCEPTED", null, false, "Certificate sighted and copied", "Recognised through prior experience");
        assertThatThrownBy(() -> service.transitionApplication(TENANT, a.getId(), r, USER, false)).isInstanceOf(HandyFlowException.class);
        assertThat(a.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void anOpenApplicationBlocksAnotherForTheSameProgramme() {
        TrainProvLearner l = learner();
        TrainProvProgramme p = programme("NON_ACCREDITED", true);
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(applications.openExists(TENANT, l.getId(), p.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.apply(TENANT, new ApplicationRequest(l.getId(), p.getId(), null, null, null), USER)).isInstanceOf(HandyFlowException.class);
        verify(applications, never()).save(any());
    }

    @Test
    void movingAnEnrolmentAlongWritesHistory() {
        TrainProvLearnerEnrolment e = TrainProvLearnerEnrolment.create(TENANT, "ENR-00005", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), null, null, START, null, "NON_ACCREDITED", null, null, null, USER);
        when(enrolments.findForTenant(TENANT, e.getId())).thenReturn(Optional.of(e));
        service.transitionEnrolment(TENANT, e.getId(), new EnrolmentTransitionRequest("IN_PROGRESS", null), USER);
        assertThat(e.getStatus()).isEqualTo("IN_PROGRESS");
        verify(enrolmentEvents).save(any(TrainProvLearnerEnrolmentEvent.class));
    }

    @Test
    void withdrawingNeedsAReason() {
        TrainProvLearnerEnrolment e = TrainProvLearnerEnrolment.create(TENANT, "ENR-00006", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), null, null, START, null, "NON_ACCREDITED", null, null, null, USER);
        when(enrolments.findForTenant(TENANT, e.getId())).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.transitionEnrolment(TENANT, e.getId(), new EnrolmentTransitionRequest("WITHDRAWN", "short"), USER))
                .isInstanceOf(HandyFlowException.class);
        assertThat(e.getStatus()).isEqualTo("ENROLLED");
    }
}
