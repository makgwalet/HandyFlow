package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.repository.ShiftRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShiftServiceRangeTest {

    private static final TenantId TENANT = TenantId.generate();
    private final ShiftRepository repo = mock(ShiftRepository.class);
    private final ShiftService service = new ShiftService(repo, null, null, null, null, null, null);
    private final Instant from = Instant.parse("2026-10-05T00:00:00Z");

    @Test
    void returnsTheShiftsInTheWindow() {
        when(repo.findByTenantInRange(any(), any(), any())).thenReturn(List.of());
        assertThat(service.getRange(TENANT, from, from.plus(Duration.ofDays(7)))).isEmpty();
        verify(repo).findByTenantInRange(TENANT, from, from.plus(Duration.ofDays(7)));
    }

    @Test
    void rejectsAnEmptyOrBackwardsWindow() {
        assertThatThrownBy(() -> service.getRange(TENANT, from, from)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.getRange(TENANT, from, from.minusSeconds(1))).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.getRange(TENANT, null, from)).isInstanceOf(HandyFlowException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void rejectsAWindowLongerThan35Days() {
        assertThat(service.getRange(TENANT, from, from.plus(Duration.ofDays(35)))).isEmpty();
        assertThatThrownBy(() -> service.getRange(TENANT, from, from.plus(Duration.ofDays(36))))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("35 days");
    }
}
