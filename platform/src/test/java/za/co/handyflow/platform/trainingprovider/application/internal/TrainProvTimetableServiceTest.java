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
import za.co.handyflow.platform.trainingprovider.dto.TimetableDtos.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvTimetableServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHECKER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final LocalDate DAY = LocalDate.of(2027, 2, 10);
    private static final UUID PROGRAMME = UUID.randomUUID();
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID SITE = UUID.randomUUID();
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime ELEVEN = LocalTime.of(11, 0);

    @Mock TrainProvTimetableSlotRepository slots;
    @Mock TrainProvCohortRepository cohorts;
    @Mock TrainProvCohortMemberRepository members;
    @Mock TrainProvCohortFacilitatorRepository facilitators;
    @Mock TrainProvCohortEventRepository events;
    @Mock TrainProvVenueRepository venues;
    @Mock TrainProvProgrammeModuleRepository modules;
    @Mock TrainProvPractitionerRepository practitioners;
    @Mock TrainProvPractitionerAppointmentRepository appointments;
    @Mock TrainProvTimetableOverrideRepository overrides;
    @Mock TrainProvLearnerRepository learners;
    @InjectMocks TrainProvTimetableService service;

    private TrainProvCohort cohort() {
        return TrainProvCohort.create(TENANT, "COH-00001", "Feb intake", PROGRAMME, VERSION, SITE, LocalDate.of(2027, 2, 1), null, 20, null, USER);
    }

    private SlotRequest request(UUID venue, UUID person) {
        return new SlotRequest("Module 1", DAY, NINE, ELEVEN, null, venue, person);
    }

    private TrainProvVenue venue(UUID site, String status) {
        TrainProvVenue v = mock(TrainProvVenue.class);
        when(v.getId()).thenReturn(UUID.randomUUID());
        when(v.getStatus()).thenReturn(status);
        when(v.getSiteId()).thenReturn(site);
        when(v.getCapacity()).thenReturn(30);
        return v;
    }

    private TrainProvTimetableSlot existing(UUID cohortId, UUID venue, UUID person, LocalTime start, LocalTime end) {
        return TrainProvTimetableSlot.create(TENANT, cohortId, "Other class", DAY, start, end, null, venue, person, USER);
    }

    @Test
    void aSlotWithNoVenueOrFacilitatorCanBeAdded() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        SlotView v = service.add(TENANT, c.getId(), request(null, null), USER);
        assertThat(v.status()).isEqualTo("SCHEDULED");
        verify(slots).save(any(TrainProvTimetableSlot.class));
        verify(events).save(any(TrainProvCohortEvent.class));
    }

    @Test
    void aSlotOutsideTheCohortDatesOrOnAFinishedCohortIsRefused() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), new SlotRequest("Module 1", LocalDate.of(2027, 1, 1), NINE, ELEVEN, null, null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("before the cohort starts");
        c.moveTo("RUNNING", null);
        c.moveTo("COMPLETED", null);
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(null, null), USER)).isInstanceOf(HandyFlowException.class);
        verify(slots, never()).save(any());
    }

    @Test
    void aVenueAtAnotherSiteOrInactiveIsRefused() {
        TrainProvCohort c = cohort();
        TrainProvVenue elsewhere = venue(UUID.randomUUID(), "ACTIVE");
        TrainProvVenue inactive = venue(SITE, "INACTIVE");
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, elsewhere.getId())).thenReturn(Optional.of(elsewhere));
        when(venues.findForTenant(TENANT, inactive.getId())).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(elsewhere.getId(), null), USER)).hasMessageContaining("different site");
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(inactive.getId(), null), USER)).hasMessageContaining("inactive");
    }

    @Test
    void aVenueAlreadyBookedAtThatTimeIsRefusedAndAFreeOneIsAccepted() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of(existing(UUID.randomUUID(), v.getId(), null, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(v.getId(), null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already booked");
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of());
        service.add(TENANT, c.getId(), request(v.getId(), null), USER);
        verify(slots, times(1)).save(any(TrainProvTimetableSlot.class));
    }

    private TrainProvPractitioner person() {
        return TrainProvPractitioner.create(TENANT, "PRC-00001", "Test", "Person", null, null, "EMPLOYEE", null, null, USER);
    }

    private TrainProvPractitionerAppointment appointment(TrainProvPractitioner p, LocalDate validTo) {
        TrainProvPractitionerAppointment a = TrainProvPractitionerAppointment.create(TENANT, p.getId(), "FACILITATOR", PROGRAMME, null, null, null, null, validTo, USER);
        a.verify("CV and certificate sighted", TODAY, CHECKER, TODAY);
        return a;
    }

    @Test
    void aFacilitatorMustBeOnTheCohort() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(facilitators.findLive(TENANT, c.getId(), UUID.fromString("00000000-0000-0000-0000-0000000000f1"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(null, UUID.fromString("00000000-0000-0000-0000-0000000000f1")), USER))
                .hasMessageContaining("not a facilitator on this cohort");
    }

    @Test
    void theFacilitatorMustBeInForceOnTheSlotDateNotJustToday() {
        TrainProvCohort c = cohort();
        TrainProvPractitioner p = person();
        TrainProvPractitionerAppointment a = appointment(p, LocalDate.of(2027, 1, 31));
        TrainProvCohortFacilitator f = TrainProvCohortFacilitator.create(TENANT, c.getId(), p.getId(), a.getId(), USER);
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(facilitators.findLive(TENANT, c.getId(), p.getId())).thenReturn(Optional.of(f));
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(null, p.getId()), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("2027-02-10");
        verify(slots, never()).save(any());
    }

    @Test
    void aFacilitatorAlreadyTeachingAtThatTimeIsRefused() {
        TrainProvCohort c = cohort();
        TrainProvPractitioner p = person();
        TrainProvPractitionerAppointment a = appointment(p, null);
        TrainProvCohortFacilitator f = TrainProvCohortFacilitator.create(TENANT, c.getId(), p.getId(), a.getId(), USER);
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(facilitators.findLive(TENANT, c.getId(), p.getId())).thenReturn(Optional.of(f));
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        when(slots.liveForPractitionerBetween(eq(TENANT), eq(p.getId()), any(), any())).thenReturn(List.of(existing(UUID.randomUUID(), null, p.getId(), LocalTime.of(8, 0), LocalTime.of(10, 0))));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(null, p.getId()), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already teaching");
    }

    @Test
    void theCohortCannotBeInTwoPlacesAtOnce() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(slots.liveForCohortBetween(eq(TENANT), eq(c.getId()), any(), any())).thenReturn(List.of(existing(c.getId(), null, null, LocalTime.of(10, 0), LocalTime.of(12, 0))));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has");
    }

    @Test
    void changingASlotDoesNotClashWithItself() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        TrainProvTimetableSlot s = existing(c.getId(), v.getId(), null, NINE, ELEVEN);
        when(slots.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of(s));
        when(slots.liveForCohortBetween(eq(TENANT), eq(c.getId()), any(), any())).thenReturn(List.of(s));
        service.change(TENANT, s.getId(), new SlotRequest("Module 1 revised", DAY, NINE, LocalTime.of(12, 0), null, v.getId(), null), USER);
        assertThat(s.getTitle()).isEqualTo("Module 1 revised");
        assertThat(s.getEndTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    void cancellingNeedsAReasonAndACancelledSlotCannotBeChanged() {
        TrainProvCohort c = cohort();
        TrainProvTimetableSlot s = existing(c.getId(), null, null, NINE, ELEVEN);
        when(slots.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        assertThatThrownBy(() -> service.cancel(TENANT, s.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(s.getStatus()).isEqualTo("SCHEDULED");
        service.cancel(TENANT, s.getId(), "Facilitator is ill", USER);
        assertThat(s.getStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service.change(TENANT, s.getId(), request(null, null), USER)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void theWindowNeedsSensibleDates() {
        assertThatThrownBy(() -> service.window(TENANT, DAY, DAY.minusDays(1), null, null, null)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.window(TENANT, DAY, DAY.plusDays(400), null, null, null)).isInstanceOf(HandyFlowException.class);
    }

    private TrainProvTimetableSlot venueClash(TrainProvVenue v) {
        return existing(UUID.randomUUID(), v.getId(), null, LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    private SlotRequest overriding(UUID venue, String reason) {
        return new SlotRequest("Module 1", DAY, NINE, ELEVEN, null, venue, null, null, reason);
    }

    @Test
    void aClashIsRefusedWithoutAReasonEvenForAnAdministrator() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of(venueClash(v)));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), overriding(v.getId(), "short"), USER, true))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("written reason");
        verify(overrides, never()).save(any());
    }

    @Test
    void aClashWithAReasonIsRefusedForSomeoneWhoCannotApprove() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of(venueClash(v)));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), overriding(v.getId(), "The room is large enough for both groups"), USER, false))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only an administrator")
                .satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN));
        verify(overrides, never()).save(any());
    }

    @Test
    void anAdministratorCanBookOverAClashWithAReasonAndItIsRecorded() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        TrainProvTimetableSlot other = venueClash(v);
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of(other));
        service.add(TENANT, c.getId(), overriding(v.getId(), "The room is large enough for both groups"), USER, true);
        org.mockito.ArgumentCaptor<TrainProvTimetableOverride> o = org.mockito.ArgumentCaptor.forClass(TrainProvTimetableOverride.class);
        verify(overrides).save(o.capture());
        assertThat(o.getValue().getResourceType()).isEqualTo("VENUE");
        assertThat(o.getValue().getClashingSlotId()).isEqualTo(other.getId());
        assertThat(o.getValue().getApprovedBy()).isEqualTo(USER);
        assertThat(o.getValue().getReason()).contains("large enough");
        verify(slots).save(any(TrainProvTimetableSlot.class));
    }

    @Test
    void aLearnerInTwoCohortsAtOnceIsAClash() {
        TrainProvCohort c = cohort();
        UUID learnerId = UUID.randomUUID();
        TrainProvTimetableSlot other = existing(UUID.randomUUID(), null, null, LocalTime.of(10, 0), LocalTime.of(12, 0));
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(slots.activeLearnerIds(TENANT, c.getId())).thenReturn(List.of(learnerId));
        when(slots.otherSlotsOfLearners(eq(TENANT), eq(c.getId()), any(), any(), any())).thenReturn(java.util.Collections.singletonList(new Object[]{other, learnerId}));
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), request(null, null), USER, true))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("is also in");
        verify(slots, never()).save(any());
    }

    @Test
    void theCheckListsClashesWithoutBookingAnything() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(slots.liveForVenueBetween(eq(TENANT), eq(v.getId()), any(), any())).thenReturn(List.of(venueClash(v)));
        ClashCheck check = service.check(TENANT, c.getId(), request(v.getId(), null), null);
        assertThat(check.clear()).isFalse();
        assertThat(check.clashes()).hasSize(1);
        assertThat(check.clashes().get(0).resourceType()).isEqualTo("VENUE");
        verify(slots, never()).save(any());
        verify(overrides, never()).save(any());
    }

    @Test
    void theSameClockTimeInAnotherZoneIsADifferentMoment() {
        TrainProvCohort c = cohort();
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        SlotView v = service.add(TENANT, c.getId(), new SlotRequest("Module 1", DAY, NINE, ELEVEN, null, null, null, "UTC", null), USER);
        assertThat(v.timeZone()).isEqualTo("UTC");
        assertThat(v.startsAt()).isEqualTo(DAY.atTime(NINE).atZone(java.time.ZoneId.of("UTC")).toInstant());
        assertThatThrownBy(() -> service.add(TENANT, c.getId(), new SlotRequest("Module 1", DAY, NINE, ELEVEN, null, null, null, "Mars/Olympus", null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("time zone");
    }

    @Test
    void everyCheckTakesTheBookingLocksFirst() {
        TrainProvCohort c = cohort();
        TrainProvVenue v = venue(SITE, "ACTIVE");
        when(cohorts.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        when(venues.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        service.add(TENANT, c.getId(), request(v.getId(), null), USER);
        verify(slots, times(2)).lock(any());
    }
}
