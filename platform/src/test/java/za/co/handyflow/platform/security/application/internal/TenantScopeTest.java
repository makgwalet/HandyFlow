package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.model.Dispatch;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.GuardEnrollRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Security audit 1.3 and the cross-tenant reads: a supervisor of tenant A who knows an id that belongs to tenant B gets "not found" and nothing changes.
 * NOT RUN by the author (no Maven in the authoring sandbox): run with `mvn -q test -Dtest=TenantScopeTest`.
 */
class TenantScopeTest {

    private static final TenantId A = TenantId.generate(), B = TenantId.generate();
    private final UUID guardOfB = UUID.randomUUID(), supervisor = UUID.randomUUID();

    // ── GuardAuthService ──────────────────────────────────────────────────────

    private final GuardRepository guards = mock(GuardRepository.class);
    private final GuardTokenRepository tokens = mock(GuardTokenRepository.class);
    private final SecurityDeviceRepository devices = mock(SecurityDeviceRepository.class);
    private final DeviceActivationCodeRepository codes = mock(DeviceActivationCodeRepository.class);
    private final GuardAuthService auth = new GuardAuthService(guards, tokens, devices, codes);

    @Test @DisplayName("enrol: another tenant's guard is not found, no PIN is set, no sessions are revoked")
    void enrolForeignGuard() {
        when(guards.findByIdAndTenantForAuth(guardOfB, A)).thenReturn(Optional.empty());
        GuardEnrollRequest req = mock(GuardEnrollRequest.class);
        assertThatThrownBy(() -> auth.enroll(A, guardOfB, req, supervisor)).isInstanceOf(ResourceNotFoundException.class);
        verify(guards, never()).findByIdForAuth(any());
        verify(guards, never()).save(any());
        verifyNoInteractions(tokens);
    }

    @Test @DisplayName("device replacement: another tenant's guard gets no code")
    void replacementForeignGuard() {
        when(guards.findByIdAndTenantForAuth(guardOfB, A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.initiateDeviceReplacement(A, guardOfB, supervisor)).isInstanceOf(ResourceNotFoundException.class);
        verify(guards, never()).findByIdForAuth(any());
        verifyNoInteractions(codes);
    }

    @Test @DisplayName("revoke tokens: another tenant's guard is not found and nothing is revoked; own guard is revoked")
    void revokeScope() {
        when(guards.findByIdAndTenantForAuth(guardOfB, A)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.revokeAllTokens(A, guardOfB, "x")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(tokens);

        UUID mine = UUID.randomUUID();
        when(guards.findByIdAndTenantForAuth(mine, A)).thenReturn(Optional.of(mock(Guard.class)));
        when(tokens.revokeAllForGuard(eq(mine), any(), anyString())).thenReturn(3);
        assertThat(auth.revokeAllTokens(A, mine, "lost phone")).isEqualTo(3);
    }

    // ── ControlRoomService ────────────────────────────────────────────────────

    private final AlarmEventRepository events = mock(AlarmEventRepository.class);
    private final DispatchRepository dispatches = mock(DispatchRepository.class);
    private final ControlRoomService controlRoom = new ControlRoomService(events, dispatches, guards, mock(IncidentRepository.class));

    @Test @DisplayName("dispatch history: an alarm event of another tenant is not found and its dispatches are not read")
    void dispatchesForeignEvent() {
        UUID event = UUID.randomUUID();
        when(events.findByTenantAndId(A, event)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controlRoom.getDispatchesForEvent(A, event)).isInstanceOf(ResourceNotFoundException.class);
        verify(dispatches, never()).findByAlarmEvent(any());
        verify(dispatches, never()).findByTenantAndAlarmEvent(any(), any());
    }

    @Test @DisplayName("dispatch history: own event returns the tenant-scoped list")
    void dispatchesOwnEvent() {
        UUID event = UUID.randomUUID();
        Dispatch d = mock(Dispatch.class);
        when(events.findByTenantAndId(A, event)).thenReturn(Optional.of(mock(AlarmEvent.class)));
        when(dispatches.findByTenantAndAlarmEvent(A, event)).thenReturn(List.of(d));
        assertThat(controlRoom.getDispatchesForEvent(A, event)).containsExactly(d);
    }

    // ── PatrolRoundService ────────────────────────────────────────────────────

    @Test @DisplayName("patrol rounds by shift: the supervisor path asks only for this tenant's rounds")
    void roundsAreTenantScoped() {
        PatrolRoundRepository rounds = mock(PatrolRoundRepository.class);
        PatrolRoundService svc = new PatrolRoundService(mock(PatrolRouteRepository.class), rounds, mock(CheckpointLogRepository.class), mock(SiteRepository.class), mock(CheckpointRepository.class));
        UUID shift = UUID.randomUUID();
        when(rounds.findByTenantAndShift(B, shift)).thenReturn(List.of(mock(PatrolRound.class)));
        when(rounds.findByTenantAndShift(A, shift)).thenReturn(List.of());
        assertThat(svc.getRoundsForShift(A, shift)).isEmpty();
        assertThat(svc.getRoundsForShift(B, shift)).hasSize(1);
        verify(rounds, never()).findByShift(any());
    }
}
