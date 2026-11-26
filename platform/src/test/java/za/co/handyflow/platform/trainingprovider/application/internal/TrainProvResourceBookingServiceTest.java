package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.ResourceBookingDtos.BookRequest;
import za.co.handyflow.platform.trainingprovider.dto.ResourceBookingDtos.BookingView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvResourceBookingServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock private TrainProvSlotResourceRepository bookings;
    @Mock private TrainProvTimetableSlotRepository slots;
    @Mock private TrainProvCohortRepository cohorts;
    @Mock private TrainProvCohortEventRepository events;
    @Mock private TrainProvSiteResourceRepository resources;
    @Mock private TrainProvVenueRepository venues;
    @InjectMocks private TrainProvResourceBookingService service;

    private final UUID siteId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();
    private final UUID cohortId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private TrainProvSiteResource resource;

    @BeforeEach
    void setUp() {
        TrainProvTimetableSlot slot = mock(TrainProvTimetableSlot.class);
        lenient().when(slot.getId()).thenReturn(slotId);
        lenient().when(slot.getCohortId()).thenReturn(cohortId);
        lenient().when(slot.getStatus()).thenReturn("SCHEDULED");
        lenient().when(slot.getTitle()).thenReturn("Practical 1");
        lenient().when(slot.getSlotDate()).thenReturn(LocalDate.of(2027, 3, 10));
        lenient().when(slot.getStartsAt()).thenReturn(Instant.parse("2027-03-10T06:00:00Z"));
        lenient().when(slot.getEndsAt()).thenReturn(Instant.parse("2027-03-10T09:00:00Z"));
        lenient().when(slots.findForTenant(TENANT, slotId)).thenReturn(Optional.of(slot));
        TrainProvCohort cohort = mock(TrainProvCohort.class);
        lenient().when(cohort.getStatus()).thenReturn("PLANNED");
        lenient().when(cohort.getSiteId()).thenReturn(siteId);
        lenient().when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(cohort));
        resource = mock(TrainProvSiteResource.class);
        lenient().when(resource.getId()).thenReturn(resourceId);
        lenient().when(resource.getName()).thenReturn("Welding rig");
        lenient().when(resource.getStatus()).thenReturn("IN_SERVICE");
        lenient().when(resource.getSiteId()).thenReturn(siteId);
        lenient().when(resource.getQuantity()).thenReturn(1);
        lenient().when(resource.isShareable()).thenReturn(false);
        lenient().when(resources.findForTenant(TENANT, resourceId)).thenReturn(Optional.of(resource));
        lenient().when(bookings.forSlot(TENANT, slotId)).thenReturn(List.of());
        lenient().when(bookings.save(any(TrainProvSlotResource.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(bookings.overlapping(any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    private void alreadyBookedElsewhere(int quantity) {
        TrainProvSlotResource other = mock(TrainProvSlotResource.class);
        when(other.getQuantity()).thenReturn(quantity);
        when(bookings.overlapping(any(), any(), any(), any(), any())).thenReturn(List.<Object[]>of(new Object[]{other, mock(TrainProvTimetableSlot.class)}));
    }

    @Test
    void booksAFreeResource() {
        BookingView v = service.book(TENANT, slotId, new BookRequest(resourceId, null, null), USER, false);
        assertThat(v.quantity()).isEqualTo(1);
        assertThat(v.status()).isEqualTo("BOOKED");
        assertThat(v.overrideReason()).isNull();
        verify(events).save(any(TrainProvCohortEvent.class));
    }

    @Test
    void refusesAClashWithoutApproval() {
        alreadyBookedElsewhere(1);
        assertThatThrownBy(() -> service.book(TENANT, slotId, new BookRequest(resourceId, 1, null), USER, true))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already booked at that time");
        verify(bookings, never()).save(any(TrainProvSlotResource.class));
    }

    @Test
    void onlyAnAdministratorCanApproveAClash() {
        alreadyBookedElsewhere(1);
        assertThatThrownBy(() -> service.book(TENANT, slotId, new BookRequest(resourceId, 1, "Rig is split between two groups"), USER, false))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only an administrator");
        verify(bookings, never()).save(any(TrainProvSlotResource.class));
    }

    @Test
    void anAdministratorApprovalIsKeptOnTheBooking() {
        alreadyBookedElsewhere(1);
        BookingView v = service.book(TENANT, slotId, new BookRequest(resourceId, 1, "Rig is split between two groups"), USER, true);
        assertThat(v.overrideReason()).isEqualTo("Rig is split between two groups");
        assertThat(v.clashNote()).contains("already booked at that time");
    }

    @Test
    void aShareableResourceIsNeverACLash() {
        when(resource.isShareable()).thenReturn(true);
        BookingView v = service.book(TENANT, slotId, new BookRequest(resourceId, 1, null), USER, false);
        assertThat(v.overrideReason()).isNull();
    }

    @Test
    void refusesAResourceFromAnotherSite() {
        when(resource.getSiteId()).thenReturn(UUID.randomUUID());
        assertThatThrownBy(() -> service.book(TENANT, slotId, new BookRequest(resourceId, 1, null), USER, true))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("different site");
    }

    @Test
    void releaseNeedsAReasonAndNothingIsDeleted() {
        TrainProvSlotResource b = TrainProvSlotResource.book(TENANT, slotId, resourceId, 1, null, null, USER);
        when(bookings.findForTenant(TENANT, b.getId())).thenReturn(Optional.of(b));
        assertThatThrownBy(() -> service.release(TENANT, b.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        BookingView v = service.release(TENANT, b.getId(), "The practical was moved", USER);
        assertThat(v.status()).isEqualTo("RELEASED");
        verify(bookings, never()).delete(any(TrainProvSlotResource.class));
    }
}
