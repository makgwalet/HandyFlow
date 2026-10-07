package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.security.domain.repository.PatrolRoundRepository;
import za.co.handyflow.platform.security.dto.PatrolDtos.RoundDetail;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatrolOverviewServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private final PatrolRoundRepository rounds = mock(PatrolRoundRepository.class);
    private final PatrolOverviewService service = spy(new PatrolOverviewService(mock(JdbcTemplate.class), rounds));
    private final Instant from = Instant.parse("2026-10-01T00:00:00Z");
    private final UUID user = UUID.randomUUID();

    private PatrolRound round(TenantId tenant, boolean missed) {
        PatrolRound r = PatrolRound.create(tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, Instant.now(), Instant.now().plusSeconds(60), 3);
        if (missed) r.markMissed();
        when(rounds.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void listRejectsABadWindowOrStatus() {
        assertThatThrownBy(() -> service.list(TENANT, from, from, null, null)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.list(TENANT, from, from.plus(Duration.ofDays(32)), null, null)).hasMessageContaining("31 days");
        assertThatThrownBy(() -> service.list(TENANT, from, from.plus(Duration.ofDays(7)), null, "DONE")).hasMessageContaining("Unknown status");
    }

    @Test
    void aMissedRoundCanBeAcknowledgedWithANote() {
        PatrolRound r = round(TENANT, true);
        doReturn(new RoundDetail(null, "x", java.util.List.of())).when(service).detail(TENANT, r.getId());
        service.acknowledge(TENANT, r.getId(), user, "  Gate was locked, guard radioed in  ");
        assertThat(r.getAcknowledgedBy()).isEqualTo(user);
        assertThat(r.getAcknowledgementNote()).isEqualTo("Gate was locked, guard radioed in");
        assertThat(r.getStatus()).isEqualTo(PatrolRound.RoundStatus.MISSED); // the status is not changed
        verify(rounds).saveAndFlush(r);
    }

    @Test
    void onlyMissedOrPartialRoundsAndOnlyWithANote() {
        PatrolRound expected = round(TENANT, false);
        assertThatThrownBy(() -> service.acknowledge(TENANT, expected.getId(), user, "note")).hasMessageContaining("missed or partial");
        PatrolRound missed = round(TENANT, true);
        assertThatThrownBy(() -> service.acknowledge(TENANT, missed.getId(), user, "  ")).hasMessageContaining("note is required");
        verify(rounds, never()).saveAndFlush(any());
    }

    @Test
    void anotherTenantsRoundIsNotFound() {
        PatrolRound theirs = round(TenantId.generate(), true);
        assertThatThrownBy(() -> service.acknowledge(TENANT, theirs.getId(), user, "note")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.acknowledge(TENANT, UUID.randomUUID(), user, "note")).isInstanceOf(ResourceNotFoundException.class);
    }
}
