package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.Site;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.Row;
import za.co.handyflow.platform.security.dto.CheckpointAdminDtos.UpdateRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
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

class CheckpointAdminServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private final CheckpointRepository repo = mock(CheckpointRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CheckpointAdminService service = spy(new CheckpointAdminService(jdbc, repo));
    private final Site site = mock(Site.class);

    private Checkpoint checkpoint(TenantId tenant, String name) {
        UUID siteId = UUID.randomUUID();
        when(site.getId()).thenReturn(siteId);
        Checkpoint c = Checkpoint.create(tenant, site, name, null, 0);
        when(repo.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private void stubList(Checkpoint c) {
        doReturn(List.of(new Row(c.getId(), UUID.randomUUID(), "Site", c.getName(), null, c.isActive(), false, false, false, 0, null, 0)))
                .when(service).list(any(), any(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void editsTheDetailsAndClearsBlankIdentifiers() {
        Checkpoint c = checkpoint(TENANT, "Gate"); stubList(c);
        service.update(TENANT, c.getId(), new UpdateRequest("  North Gate ", " ", "04A1B2", "  ", true));
        assertThat(c.getName()).isEqualTo("North Gate");
        assertThat(c.getDescription()).isNull();
        assertThat(c.getNfcTagUid()).isEqualTo("04A1B2");
        assertThat(c.getBleBeaconId()).isNull();
        verify(repo).saveAndFlush(c);
    }

    @Test
    void leavingTheIdentifiersOutKeepsThemAndABlankOneRemovesIt() {
        Checkpoint c = checkpoint(TENANT, "Gate"); stubList(c);
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, "04A1B2", "BEACON1", true));
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, null, null, false)); // e.g. switching it off
        assertThat(c.getNfcTagUid()).isEqualTo("04A1B2");
        assertThat(c.getBleBeaconId()).isEqualTo("BEACON1");
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, "", null, false));
        assertThat(c.getNfcTagUid()).isNull();
        assertThat(c.getBleBeaconId()).isEqualTo("BEACON1");
    }

    @Test
    void anNfcTagUsedByAnotherActiveCheckpointIsRefused() {
        Checkpoint c = checkpoint(TENANT, "Gate"), other = checkpoint(TENANT, "Server Room");
        when(repo.findByNfcTagUid(TENANT, "04A1B2")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, "04A1B2", null, true)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already used by Server Room");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void theSameCheckpointKeepingItsOwnTagIsFine() {
        Checkpoint c = checkpoint(TENANT, "Gate"); stubList(c);
        when(repo.findByNfcTagUid(TENANT, "04A1B2")).thenReturn(Optional.of(c));
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, "04A1B2", null, true));
        verify(repo).saveAndFlush(c);
    }

    @Test
    void deactivatingKeepsTheCheckpointButStopsItBeingScannedOrChecked() {
        Checkpoint c = checkpoint(TENANT, "Gate"); stubList(c);
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, "04A1B2", null, false));
        assertThat(c.isActive()).isFalse();
        verify(repo, never()).findByNfcTagUid(any(), any()); // an inactive checkpoint need not be unique
    }

    @Test
    void aBlankNameOrAnotherTenantsCheckpointIsRefused() {
        Checkpoint c = checkpoint(TENANT, "Gate");
        assertThatThrownBy(() -> service.update(TENANT, c.getId(), new UpdateRequest("  ", null, null, null, true))).hasMessageContaining("needs a name");
        Checkpoint theirs = checkpoint(TenantId.generate(), "Theirs");
        assertThatThrownBy(() -> service.update(TENANT, theirs.getId(), new UpdateRequest("x", null, null, null, true))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void switchingOffAnActiveCheckpointAdjustsAndCompletesOpenRounds() {
        Checkpoint c = checkpoint(TENANT, "Gate"); stubList(c);
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, null, null, false));
        // one update lowering the expected count, one completing rounds that now have every remaining scan
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("checkpoints_expected = GREATEST"), org.mockito.ArgumentMatchers.any(Object[].class));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("status = 'COMPLETE'"), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void switchingBackOnRestoresTheCountButNeverCompletesRounds() {
        Checkpoint c = checkpoint(TENANT, "Gate"); c.updateDetails("Gate", null, null, null, false); stubList(c);
        service.update(TENANT, c.getId(), new UpdateRequest("Gate", null, null, null, true));
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("checkpoints_expected = GREATEST"), org.mockito.ArgumentMatchers.any(Object[].class));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.contains("status = 'COMPLETE'"), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    void editingWithoutChangingTheActiveFlagLeavesRoundsAlone() {
        Checkpoint c = checkpoint(TENANT, "Gate"); stubList(c);
        service.update(TENANT, c.getId(), new UpdateRequest("Gate 2", null, null, null, true));
        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class));
    }
}
