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
import za.co.handyflow.platform.trainingprovider.dto.LearnerPortalDtos.EmployerLearners;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvPortalReadServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final TenantId TENANT = TenantId.of(TENANT_ID);
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CLIENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Mock private TrainProvLearnerPortalGrantRepository learnerGrants;
    @Mock private TrainProvPortalAccessGrantRepository clientGrants;
    @Mock private TrainProvLearnerRepository learners;
    @Mock private TrainProvLearnerEnrolmentRepository enrolments;
    @Mock private TrainProvProgrammeRepository programmes;
    @Mock private TrainProvCohortRepository cohorts;
    @Mock private TrainProvCohortMemberRepository members;
    @Mock private TrainProvTimetableSlotRepository slots;
    @Mock private TrainProvAttendanceRepository attendance;
    @Mock private TrainProvCompletionRepository completions;
    @InjectMocks private TrainProvPortalReadService service;

    @Test
    void aLoginWithNoLearnerGrantSeesNothing() {
        when(learnerGrants.acceptedForUser(USER)).thenReturn(List.of());
        assertThatThrownBy(() -> service.learner(USER, java.time.LocalDate.of(2027, 3, 10))).isInstanceOf(HandyFlowException.class).hasMessageContaining("No active learner access");
        verifyNoInteractions(learners, enrolments);
    }

    @Test
    void aLoginWithNoClientGrantSeesNothing() {
        when(clientGrants.findAcceptedByPortalUser(USER)).thenReturn(List.of());
        assertThatThrownBy(() -> service.employer(USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("No active client access");
        verifyNoInteractions(enrolments);
    }

    @Test
    void anEmployerSeesConsentedLearnersAndOnlyCountsTheRest() {
        TrainProvPortalAccessGrant grant = mock(TrainProvPortalAccessGrant.class);
        when(grant.getTenantId()).thenReturn(TENANT_ID);
        when(grant.getClientId()).thenReturn(CLIENT);
        when(clientGrants.findAcceptedByPortalUser(USER)).thenReturn(List.of(grant));

        TrainProvLearner yes = mock(TrainProvLearner.class);
        UUID yesId = UUID.randomUUID();
        when(yes.getId()).thenReturn(yesId);
        when(yes.isConsentGiven()).thenReturn(true);
        when(yes.getMergedIntoId()).thenReturn(null);
        when(yes.getLearnerNo()).thenReturn("LRN-00001");
        when(yes.getFirstNames()).thenReturn("Thandi");
        when(yes.getSurname()).thenReturn("Nkosi");
        TrainProvLearner no = mock(TrainProvLearner.class);
        UUID noId = UUID.randomUUID();
        when(no.getId()).thenReturn(noId);
        when(no.isConsentGiven()).thenReturn(false);

        TrainProvLearnerEnrolment e1 = mock(TrainProvLearnerEnrolment.class);
        when(e1.getLearnerId()).thenReturn(yesId);
        when(e1.getId()).thenReturn(UUID.randomUUID());
        when(e1.getProgrammeId()).thenReturn(UUID.randomUUID());
        when(e1.getEnrolmentNo()).thenReturn("ENR-00001");
        when(e1.getStatus()).thenReturn("IN_PROGRESS");
        TrainProvLearnerEnrolment e2 = mock(TrainProvLearnerEnrolment.class);
        when(e2.getLearnerId()).thenReturn(noId);
        when(enrolments.forClient(TENANT, CLIENT)).thenReturn(List.of(e1, e2));
        when(learners.findForTenant(TENANT, yesId)).thenReturn(Optional.of(yes));
        when(learners.findForTenant(TENANT, noId)).thenReturn(Optional.of(no));
        when(members.findForLearner(TENANT, yesId)).thenReturn(List.of());
        when(programmes.findForTenant(any(), any())).thenReturn(Optional.empty());
        when(completions.findLiveForEnrolment(any(), any())).thenReturn(Optional.empty());

        EmployerLearners out = service.employer(USER);

        assertThat(out.rows()).hasSize(1);
        assertThat(out.rows().get(0).fullName()).isEqualTo("Thandi Nkosi");
        assertThat(out.rows().get(0).enrolment().outcome()).isEqualTo("No outcome recorded yet");
        assertThat(out.hiddenForConsent()).isEqualTo(1);
        verify(enrolments).forClient(TENANT, CLIENT);
        verify(enrolments, never()).search(any(), any(), any(), any());
    }
}
