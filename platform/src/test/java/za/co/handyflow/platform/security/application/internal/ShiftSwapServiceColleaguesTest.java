package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.Shift;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.security.domain.repository.ShiftSwapRepository;
import za.co.handyflow.platform.security.dto.CreateSwapRequest;
import za.co.handyflow.platform.security.dto.SwapColleagueView;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The swap picker shows only people the guard shares a roster with, names only, and never the guard themselves. */
class ShiftSwapServiceColleaguesTest {

    private static final TenantId TENANT = TenantId.generate();
    private final ShiftSwapRepository swaps = mock(ShiftSwapRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final GuardRepository guards = mock(GuardRepository.class);
    private final ShiftSwapService service = new ShiftSwapService(swaps, shifts, guards);

    private final UUID me = UUID.randomUUID();
    private final UUID site = UUID.randomUUID();

    private Shift shift(UUID guardId) {
        Shift s = mock(Shift.class);
        when(s.getGuardId()).thenReturn(guardId);
        when(s.getSiteId()).thenReturn(site);
        return s;
    }

    private Guard guard(UUID id, String name, boolean schedulable) {
        Guard g = mock(Guard.class);
        when(g.getId()).thenReturn(id);
        when(g.getFullName()).thenReturn(name);
        when(g.isSchedulable()).thenReturn(schedulable);
        when(guards.findActiveById(TENANT, id)).thenReturn(Optional.of(g));
        return g;
    }

    @Test
    void listsOnlySchedulableGuardsFromMySitesSortedAndNotMe() {
        UUID zed = UUID.randomUUID(), amy = UUID.randomUUID(), suspended = UUID.randomUUID();
        when(shifts.findByGuardInRange(any(), any(), any(), any())).thenReturn(List.of(shift(me)));
        when(shifts.findBySiteInRange(any(), any(), any(), any()))
                .thenReturn(List.of(shift(me), shift(zed), shift(amy), shift(amy), shift(suspended)));
        guard(zed, "Zed Zulu", true);
        guard(amy, "amy Adams", true);
        guard(suspended, "Sam Suspended", false);

        List<SwapColleagueView> out = service.colleaguesFor(TENANT, me);

        assertThat(out).extracting(SwapColleagueView::name).containsExactly("amy Adams", "Zed Zulu");
    }

    @Test
    void aGuardWithNoRosterHasNoColleagues() {
        when(shifts.findByGuardInRange(any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.colleaguesFor(TENANT, me)).isEmpty();
    }

    @Test
    void cannotAskYourselfToTakeYourOwnShift() {
        UUID shiftId = UUID.randomUUID();
        Shift mine = shift(me);
        when(mine.getStatus()).thenReturn(za.co.handyflow.platform.security.domain.model.ShiftStatus.SCHEDULED);
        when(shifts.findByTenantAndId(TENANT, shiftId)).thenReturn(Optional.of(mine));
        when(swaps.findOpenByShift(shiftId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createSwapRequest(TENANT, me, new CreateSwapRequest(shiftId, me, null)))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void cannotAskAColleagueWhoCannotTakeShifts() {
        UUID shiftId = UUID.randomUUID(), other = UUID.randomUUID();
        Shift mine = shift(me);
        when(mine.getStatus()).thenReturn(za.co.handyflow.platform.security.domain.model.ShiftStatus.SCHEDULED);
        when(shifts.findByTenantAndId(TENANT, shiftId)).thenReturn(Optional.of(mine));
        when(swaps.findOpenByShift(shiftId)).thenReturn(List.of());
        guard(other, "Out Of Action", false);

        assertThatThrownBy(() -> service.createSwapRequest(TENANT, me, new CreateSwapRequest(shiftId, other, null)))
                .isInstanceOf(HandyFlowException.class);
    }
}
