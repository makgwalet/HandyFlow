package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.co.handyflow.platform.shared.*;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerPortalGrant;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerPortalGrantRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvLearnerPortalServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID BY = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID LEARNER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    @Mock private TrainProvLearnerPortalGrantRepository grants;
    @Mock private TrainProvLearnerRepository learners;
    @Mock private PortalUserRepository portalUsers;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PortalJwtService jwt;
    @Mock private EmailService emailService;
    @InjectMocks private TrainProvLearnerPortalService service;

    private TrainProvLearner learner(String status) {
        TrainProvLearner l = mock(TrainProvLearner.class);
        when(l.getStatus()).thenReturn(status);
        lenient().when(l.getFirstNames()).thenReturn("Thandi");
        lenient().when(l.getSurname()).thenReturn("Nkosi");
        return l;
    }

    @Test
    void invitingNeedsAWrittenReason() {
        when(learners.findForTenant(TENANT, LEARNER)).thenReturn(Optional.of(learner("ACTIVE")));
        when(grants.liveExists(TENANT, LEARNER)).thenReturn(false);
        assertThatThrownBy(() -> service.invite(TENANT, LEARNER, "t@example.co.za", "short", BY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("at least 10");
        verify(grants, never()).save(any());
    }

    @Test
    void onlyOneLiveLinkPerLearner() {
        when(learners.findForTenant(TENANT, LEARNER)).thenReturn(Optional.of(learner("ACTIVE")));
        when(grants.liveExists(TENANT, LEARNER)).thenReturn(true);
        assertThatThrownBy(() -> service.invite(TENANT, LEARNER, "t@example.co.za", "Learner asked to see her timetable", BY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already has portal access");
    }

    @Test
    void anArchivedLearnerCannotBeInvited() {
        when(learners.findForTenant(TENANT, LEARNER)).thenReturn(Optional.of(learner("ARCHIVED")));
        when(grants.liveExists(TENANT, LEARNER)).thenReturn(false);
        assertThatThrownBy(() -> service.invite(TENANT, LEARNER, "t@example.co.za", "Learner asked to see her timetable", BY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("active learner");
    }

    @Test
    void anInviteIsSavedAndEmailed() {
        when(learners.findForTenant(TENANT, LEARNER)).thenReturn(Optional.of(learner("ACTIVE")));
        when(grants.liveExists(TENANT, LEARNER)).thenReturn(false);
        when(grants.save(any(TrainProvLearnerPortalGrant.class))).thenAnswer(i -> i.getArgument(0));
        TrainProvLearnerPortalGrant g = service.invite(TENANT, LEARNER, " T@Example.co.za ", "Learner asked to see her timetable", BY);
        assertThat(g.getInviteEmail()).isEqualTo("t@example.co.za");
        assertThat(g.getStatus()).isEqualTo("PENDING");
        verify(emailService).send(eq("t@example.co.za"), any(), any());
    }

    @Test
    void endingNeedsAReasonAndKeepsTheRow() {
        TrainProvLearnerPortalGrant g = TrainProvLearnerPortalGrant.invite(TENANT, LEARNER, "t@example.co.za", "tok", Instant.now().plusSeconds(600), "Learner asked to see her timetable", BY, "ACTIVE");
        when(grants.findForTenant(TENANT, g.getId())).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.end(TENANT, g.getId(), "short", BY)).isInstanceOf(HandyFlowException.class);
        when(grants.save(g)).thenReturn(g);
        service.end(TENANT, g.getId(), "Learner left the programme", BY);
        assertThat(g.getStatus()).isEqualTo("ENDED");
        assertThat(g.getEndedReason()).isEqualTo("Learner left the programme");
        verify(grants, never()).delete(any());
    }

    @Test
    void anExpiredInviteCannotRegister() {
        TrainProvLearnerPortalGrant g = TrainProvLearnerPortalGrant.invite(TENANT, LEARNER, "t@example.co.za", "tok", Instant.now().minusSeconds(5), "Learner asked to see her timetable", BY, "ACTIVE");
        when(grants.findByInviteToken("tok")).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.register("tok", "password123", "Thandi")).isInstanceOf(HandyFlowException.class).hasMessageContaining("expired");
        verify(portalUsers, never()).save(any());
    }

    @Test
    void aLoginWithoutLearnerAccessIsRefused() {
        PortalUser u = mock(PortalUser.class);
        when(u.isActive()).thenReturn(true);
        when(u.getPasswordHash()).thenReturn("h");
        when(u.getId()).thenReturn(UUID.randomUUID());
        when(portalUsers.findByEmail("t@example.co.za")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("pw", "h")).thenReturn(true);
        when(grants.acceptedForUser(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.login("t@example.co.za", "pw")).isInstanceOf(HandyFlowException.class).hasMessageContaining("learner access");
        verify(jwt, never()).generateToken(any(), any());
    }
}
