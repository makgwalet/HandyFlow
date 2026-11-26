package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeNeedsDtos.CohortSlotNeeds;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvSlotNeedsServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock private TrainProvCohortRepository cohorts;
    @Mock private TrainProvCohortMemberRepository members;
    @Mock private TrainProvProgrammeResourceNeedRepository needs;
    @Mock private TrainProvTimetableSlotRepository slots;
    @Mock private TrainProvSlotResourceRepository bookings;
    @Mock private TrainProvSiteResourceRepository resources;
    @InjectMocks private TrainProvSlotNeedsService service;

    private final UUID cohortId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();

    private TrainProvSiteResource resource(UUID id, String type, String name) {
        TrainProvSiteResource r = mock(TrainProvSiteResource.class);
        when(r.getId()).thenReturn(id);
        lenient().when(r.getResourceType()).thenReturn(type);
        lenient().when(r.getName()).thenReturn(name);
        return r;
    }

    private TrainProvTimetableSlot slot(UUID id, LocalDate date, String status) {
        TrainProvTimetableSlot s = mock(TrainProvTimetableSlot.class);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getSlotDate()).thenReturn(date);
        lenient().when(s.getStatus()).thenReturn(status);
        return s;
    }

    private TrainProvSlotResource booking(UUID resourceId, int qty, String status) {
        TrainProvSlotResource b = mock(TrainProvSlotResource.class);
        lenient().when(b.getResourceId()).thenReturn(resourceId);
        lenient().when(b.getQuantity()).thenReturn(qty);
        lenient().when(b.getStatus()).thenReturn(status);
        return b;
    }

    @Test
    void flagsASessionWhereWhatIsListedAsNeededIsNotBooked() {
        TrainProvCohort c = mock(TrainProvCohort.class);
        when(c.getId()).thenReturn(cohortId);
        when(c.getProgrammeVersionId()).thenReturn(versionId);
        when(c.getSiteId()).thenReturn(siteId);
        when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(c));
        when(members.countActive(TENANT, cohortId)).thenReturn(10L);
        TrainProvProgrammeResourceNeed need = mock(TrainProvProgrammeResourceNeed.class);
        UUID needId = UUID.randomUUID();
        when(need.getId()).thenReturn(needId);
        when(need.getDescription()).thenReturn("Practice mannequins");
        when(need.getResourceType()).thenReturn("EQUIPMENT");
        when(need.getMatchText()).thenReturn("mannequin");
        when(need.getQuantity()).thenReturn(1);
        when(need.getPerLearners()).thenReturn(5);
        when(needs.live(TENANT, versionId)).thenReturn(List.of(need));
        UUID mannequin = UUID.randomUUID();
        UUID projector = UUID.randomUUID();
        when(resources.search(TENANT, siteId, null, null)).thenReturn(List.of(resource(mannequin, "EQUIPMENT", "Adult mannequin"), resource(projector, "EQUIPMENT", "Projector")));
        UUID covered = UUID.randomUUID();
        UUID bare = UUID.randomUUID();
        UUID wrongKind = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        UUID past = UUID.randomUUID();
        when(slots.findForCohort(TENANT, cohortId)).thenReturn(List.of(
                slot(covered, TODAY, "SCHEDULED"), slot(bare, TODAY.plusDays(1), "SCHEDULED"), slot(wrongKind, TODAY.plusDays(2), "SCHEDULED"),
                slot(cancelled, TODAY.plusDays(3), "CANCELLED"), slot(past, TODAY.minusDays(1), "SCHEDULED")));
        when(bookings.forSlot(TENANT, covered)).thenReturn(List.of(booking(mannequin, 2, "BOOKED")));
        when(bookings.forSlot(TENANT, bare)).thenReturn(List.of(booking(mannequin, 2, "RELEASED")));
        when(bookings.forSlot(TENANT, wrongKind)).thenReturn(List.of(booking(projector, 3, "BOOKED")));

        CohortSlotNeeds out = service.forCohort(TENANT, cohortId, TODAY);

        assertThat(out.slots()).hasSize(3);
        assertThat(out.slots().get(0).slotId()).isEqualTo(covered);
        assertThat(out.slots().get(0).notCovered()).isZero();
        assertThat(out.slots().get(0).rows().get(0).required()).isEqualTo(2);
        assertThat(out.slots().get(1).notCovered()).isEqualTo(1);
        assertThat(out.slots().get(1).rows().get(0).status()).isEqualTo("NONE_HELD");
        assertThat(out.slots().get(2).rows().get(0).booked()).isZero();
        assertThat(out.note()).contains("prompt to check");
        verify(bookings, never()).forSlot(TENANT, cancelled);
        verify(bookings, never()).forSlot(TENANT, past);
    }

    @Test
    void aProgrammeWithNoListedNeedsHasNothingToFlag() {
        TrainProvCohort c = mock(TrainProvCohort.class);
        when(c.getId()).thenReturn(cohortId);
        when(c.getProgrammeVersionId()).thenReturn(versionId);
        when(c.getSiteId()).thenReturn(siteId);
        when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(c));
        when(members.countActive(TENANT, cohortId)).thenReturn(4L);
        when(needs.live(TENANT, versionId)).thenReturn(List.of());
        when(resources.search(TENANT, siteId, null, null)).thenReturn(List.of());
        assertThat(service.forCohort(TENANT, cohortId, TODAY).slots()).isEmpty();
        verifyNoInteractions(slots, bookings);
    }
}
