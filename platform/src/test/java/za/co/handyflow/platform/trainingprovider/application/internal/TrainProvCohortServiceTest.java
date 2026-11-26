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
import za.co.handyflow.platform.trainingprovider.dto.CohortDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvCohortServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHECKER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final UUID PROGRAMME = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID SITE = UUID.randomUUID();

    @Mock TrainProvVenueRepository venues;
    @Mock TrainProvCohortRepository cohorts;
    @Mock TrainProvCohortMemberRepository members;
    @Mock TrainProvCohortFacilitatorRepository facilitators;
    @Mock TrainProvCohortEventRepository events;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvDeliverySiteRepository sites;
    @Mock TrainProvProgrammeOfferingRepository offerings;
    @Mock TrainProvAccreditationService accreditation;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvPractitionerRepository practitioners;
    @Mock TrainProvPractitionerAppointmentRepository appointments;
    @Mock TrainProvSitePermissionRepository sitePermissions;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvCohortService service;

    private TrainProvCohort cohort() {
        return TrainProvCohort.create(TENANT, "COH-00001", "Feb intake", PROGRAMME, VERSION, SITE, TODAY.plusDays(30), null, 2, null, USER);
    }

    private TrainProvLearnerEnrolment enrolment(UUID version, UUID site) {
        return TrainProvLearnerEnrolment.create(TENANT, "ENR-00001", UUID.randomUUID(), PROGRAMME, version, null, site, null, null,
                TODAY.plusDays(30), null, "NON_ACCREDITED", null, null, null, USER);
    }

    @Test
    void aCohortIsOpenedOnTheApprovedVersionOfAnActiveProgramme() {
        TrainProvProgramme p = mock(TrainProvProgramme.class);
        when(p.getId()).thenReturn(PROGRAMME);
        when(p.getStatus()).thenReturn("ACTIVE");
        TrainProvProgrammeVersion v = mock(TrainProvProgrammeVersion.class);
        when(v.getId()).thenReturn(VERSION);
        when(v.getVersionNo()).thenReturn(3);
        TrainProvDeliverySite s = mock(TrainProvDeliverySite.class);
        when(s.getId()).thenReturn(SITE);
        when(s.getStatus()).thenReturn("ACTIVE");
        when(programmes.findForTenant(TENANT, PROGRAMME)).thenReturn(Optional.of(p));
        when(versions.findApproved(TENANT, PROGRAMME)).thenReturn(Optional.of(v));
        when(sites.findForTenant(TENANT, SITE)).thenReturn(Optional.of(s));
        when(numbers.nextCohortNumber(TENANT)).thenReturn("COH-00001");
        stubOffering(true);
        CohortView view = service.create(TENANT, new CohortRequest("Feb intake", PROGRAMME, SITE, TODAY.plusDays(30), null, 20, null), USER);
        assertThat(view.cohortNo()).isEqualTo("COH-00001");
        assertThat(view.status()).isEqualTo("PLANNED");
        verify(cohorts).save(any(TrainProvCohort.class));
        verify(events).save(any(TrainProvCohortEvent.class));
    }

    @Test
    void aProgrammeWithoutAnApprovedVersionCannotOpenACohort() {
        TrainProvProgramme p = mock(TrainProvProgramme.class);
        when(p.getId()).thenReturn(PROGRAMME);
        when(p.getStatus()).thenReturn("ACTIVE");
        when(programmes.findForTenant(TENANT, PROGRAMME)).thenReturn(Optional.of(p));
        when(versions.findApproved(TENANT, PROGRAMME)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(TENANT, new CohortRequest("Feb intake", PROGRAMME, SITE, TODAY, null, null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("approved version");
        verify(cohorts, never()).save(any());
    }

    @Test
    void aMatchingEnrolmentCanJoin() {
        TrainProvCohort c = cohort();
        TrainProvLearnerEnrolment e = enrolment(VERSION, SITE);
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(enrolments.findForTenant(TENANT, e.getId())).thenReturn(Optional.of(e));
        service.addMember(TENANT, c.getId(), new AddMember(e.getId()), USER, TODAY);
        verify(members).save(any(TrainProvCohortMember.class));
        verify(events).save(any(TrainProvCohortEvent.class));
    }

    @Test
    void anEnrolmentOnAnotherVersionOrSiteIsRefused() {
        TrainProvCohort c = cohort();
        TrainProvLearnerEnrolment otherVersion = enrolment(UUID.randomUUID(), SITE);
        TrainProvLearnerEnrolment otherSite = enrolment(VERSION, UUID.randomUUID());
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(enrolments.findForTenant(TENANT, otherVersion.getId())).thenReturn(Optional.of(otherVersion));
        when(enrolments.findForTenant(TENANT, otherSite.getId())).thenReturn(Optional.of(otherSite));
        assertThatThrownBy(() -> service.addMember(TENANT, c.getId(), new AddMember(otherVersion.getId()), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> service.addMember(TENANT, c.getId(), new AddMember(otherSite.getId()), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("site");
        verify(members, never()).save(any());
    }

    @Test
    void aFullCohortAndAnEnrolmentAlreadyInACohortAreRefused() {
        TrainProvCohort c = cohort();
        TrainProvLearnerEnrolment e = enrolment(VERSION, SITE);
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(enrolments.findForTenant(TENANT, e.getId())).thenReturn(Optional.of(e));
        when(members.countActive(TENANT, c.getId())).thenReturn(2L);
        assertThatThrownBy(() -> service.addMember(TENANT, c.getId(), new AddMember(e.getId()), USER, TODAY)).hasMessageContaining("full");
        when(members.countActive(TENANT, c.getId())).thenReturn(0L);
        when(members.liveForEnrolment(TENANT, e.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.addMember(TENANT, c.getId(), new AddMember(e.getId()), USER, TODAY)).hasMessageContaining("already in a cohort");
    }

    @Test
    void capacityCannotDropBelowTheLearnersAlreadyIn() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(members.countActive(TENANT, c.getId())).thenReturn(2L);
        assertThatThrownBy(() -> service.update(TENANT, c.getId(), new CohortUpdate("Feb intake", TODAY.plusDays(30), null, 1, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has 2");
    }

    @Test
    void cancellingNeedsAReasonAndAFinishedCohortCannotRestart() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        stubDeliverable();
        assertThatThrownBy(() -> service.transition(TENANT, c.getId(), new CohortTransition("CANCELLED", "short"), USER)).isInstanceOf(HandyFlowException.class);
        service.transition(TENANT, c.getId(), new CohortTransition("RUNNING", null), USER);
        service.transition(TENANT, c.getId(), new CohortTransition("COMPLETED", null), USER);
        assertThatThrownBy(() -> service.transition(TENANT, c.getId(), new CohortTransition("RUNNING", null), USER)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void removingALearnerNeedsAReason() {
        TrainProvCohortMember m = TrainProvCohortMember.create(TENANT, UUID.randomUUID(), UUID.randomUUID(), USER);
        when(members.findForTenant(TENANT, m.getId())).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.removeMember(TENANT, m.getId(), "short", USER, TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(m.getStatus()).isEqualTo("ACTIVE");
        service.removeMember(TENANT, m.getId(), "Moved to the next intake", USER, TODAY);
        assertThat(m.getStatus()).isEqualTo("REMOVED");
    }

    private TrainProvPractitioner person() {
        return TrainProvPractitioner.create(TENANT, "PRC-00001", "Test", "Person", null, null, "EMPLOYEE", null, null, USER);
    }

    private TrainProvPractitionerAppointment facilitatorAppointment(TrainProvPractitioner p, boolean checked) {
        TrainProvPractitionerAppointment a = TrainProvPractitionerAppointment.create(TENANT, p.getId(), "FACILITATOR", PROGRAMME, null, null, null, null, null, USER);
        if (checked) {
            a.verify("CV and certificate sighted", TODAY, CHECKER, TODAY);
        }
        return a;
    }

    @Test
    void aCheckedInForceFacilitatorCanBeAssigned() {
        TrainProvCohort c = cohort();
        TrainProvPractitioner p = person();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(practitioners.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "FACILITATOR")).thenReturn(List.of(facilitatorAppointment(p, true)));
        service.assignFacilitator(TENANT, c.getId(), new AssignFacilitator(p.getId()), USER, TODAY);
        verify(facilitators).save(any(TrainProvCohortFacilitator.class));
    }

    @Test
    void anUncheckedFacilitatorOrOneWithoutAnAppointmentIsRefused() {
        TrainProvCohort c = cohort();
        TrainProvPractitioner p = person();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(practitioners.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "FACILITATOR")).thenReturn(List.of(facilitatorAppointment(p, false)));
        assertThatThrownBy(() -> service.assignFacilitator(TENANT, c.getId(), new AssignFacilitator(p.getId()), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no record");
        when(appointments.findLiveForProgramme(TENANT, PROGRAMME, "FACILITATOR")).thenReturn(List.of());
        assertThatThrownBy(() -> service.assignFacilitator(TENANT, c.getId(), new AssignFacilitator(p.getId()), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no facilitator appointment");
        verify(facilitators, never()).save(any());
    }

    @Test
    void aVenueNameCannotBeUsedTwiceAtASite() {
        TrainProvDeliverySite s = mock(TrainProvDeliverySite.class);
        when(s.getId()).thenReturn(SITE);
        when(sites.findForTenant(TENANT, SITE)).thenReturn(Optional.of(s));
        when(venues.nameTaken(TENANT, SITE, "Room 1")).thenReturn(true);
        assertThatThrownBy(() -> service.createVenue(TENANT, new VenueRequest(SITE, "Room 1", 20, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has a venue");
    }

    private TrainProvProgramme stubProgramme(String activity) {
        TrainProvProgramme p = mock(TrainProvProgramme.class);
        lenient().when(p.getId()).thenReturn(PROGRAMME);
        lenient().when(p.getStatus()).thenReturn("ACTIVE");
        lenient().when(p.getTrainingActivity()).thenReturn(activity);
        lenient().when(programmes.findForTenant(TENANT, PROGRAMME)).thenReturn(Optional.of(p));
        return p;
    }

    private void stubSite() {
        TrainProvDeliverySite s = mock(TrainProvDeliverySite.class);
        lenient().when(s.getId()).thenReturn(SITE);
        lenient().when(s.getStatus()).thenReturn("ACTIVE");
        lenient().when(sites.findForTenant(TENANT, SITE)).thenReturn(Optional.of(s));
    }

    private void stubOffering(boolean present) {
        TrainProvProgrammeOffering o = mock(TrainProvProgrammeOffering.class);
        lenient().when(o.getOfferedFrom()).thenReturn(TODAY.minusDays(90));
        lenient().when(offerings.findLive(TENANT, PROGRAMME, SITE)).thenReturn(present ? Optional.of(o) : Optional.empty());
    }

    private void stubDeliverable() {
        stubProgramme("NON_ACCREDITED");
        stubSite();
        stubOffering(true);
    }

    private void stubCover(LocalDate day, boolean covered, String outcome, String message) {
        when(accreditation.programmeCoverage(eq(TENANT), eq(PROGRAMME), any(), eq(SITE), eq(day)))
                .thenReturn(new CoverageView(PROGRAMME, SITE, day, outcome, covered, message));
    }

    private CohortRequest request(LocalDate start, LocalDate end) {
        return new CohortRequest("Feb intake", PROGRAMME, SITE, start, end, 20, null);
    }

    private void stubOpenable(String activity) {
        TrainProvProgramme p = stubProgramme(activity);
        TrainProvProgrammeVersion v = mock(TrainProvProgrammeVersion.class);
        lenient().when(v.getId()).thenReturn(VERSION);
        lenient().when(versions.findApproved(TENANT, PROGRAMME)).thenReturn(Optional.of(v));
        stubSite();
    }

    @Test
    void aCohortCannotBeOpenedWhereTheProgrammeIsNotOffered() {
        stubOpenable("NON_ACCREDITED");
        stubOffering(false);
        assertThatThrownBy(() -> service.create(TENANT, request(TODAY.plusDays(30), null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not offered at that site");
        verify(cohorts, never()).save(any());
    }

    @Test
    void anAccreditedCohortNeedsAnApprovalInForceOnItsFirstAndLastDay() {
        stubOpenable("ACCREDITED");
        stubOffering(true);
        LocalDate start = TODAY.plusDays(30);
        LocalDate end = TODAY.plusDays(120);
        stubCover(start, true, "COVERED", "Covered by a current approval");
        stubCover(end, false, "EXPIRED", "The approval has expired");
        assertThatThrownBy(() -> service.create(TENANT, request(start, end), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("cannot run to " + end).hasMessageContaining("expired");
        verify(cohorts, never()).save(any());
    }

    @Test
    void aSubmittedAccreditationDoesNotLetAnAccreditedCohortOpen() {
        stubOpenable("ACCREDITED");
        stubOffering(true);
        LocalDate start = TODAY.plusDays(30);
        stubCover(start, false, "NOT_APPROVED", "The accreditation is submitted. It is not an approval");
        assertThatThrownBy(() -> service.create(TENANT, request(start, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not an approval");
        verify(cohorts, never()).save(any());
    }

    @Test
    void aConditionalApprovalOpensTheCohortAndTheNoteIsKeptInItsHistory() {
        stubOpenable("ACCREDITED");
        stubOffering(true);
        when(numbers.nextCohortNumber(TENANT)).thenReturn("COH-00002");
        LocalDate start = TODAY.plusDays(30);
        stubCover(start, true, "COVERED_WITH_CONDITIONS", "Covered, but the approval has conditions that must be met");
        service.create(TENANT, request(start, null), USER);
        org.mockito.ArgumentCaptor<TrainProvCohortEvent> ev = org.mockito.ArgumentCaptor.forClass(TrainProvCohortEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getNote()).contains("conditions");
    }

    @Test
    void movingTheDatesOfAPlannedCohortIsCheckedAgainAndLeavingThemAloneIsNot() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        stubProgramme("NON_ACCREDITED");
        stubSite();
        stubOffering(false);
        assertThatThrownBy(() -> service.update(TENANT, c.getId(), new CohortUpdate("Feb intake", TODAY.plusDays(45), null, 5, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not offered");
        service.update(TENANT, c.getId(), new CohortUpdate("Renamed", TODAY.plusDays(30), null, 5, null), USER);
        verify(cohorts).save(c);
    }

    @Test
    void aPlannedCohortCannotStartOnceTheOfferingIsWithdrawn() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        stubProgramme("NON_ACCREDITED");
        stubSite();
        stubOffering(false);
        assertThatThrownBy(() -> service.transition(TENANT, c.getId(), new CohortTransition("RUNNING", null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not offered");
        assertThat(c.getStatus()).isEqualTo("PLANNED");
    }
}
