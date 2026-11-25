package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLmsLink;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeVersion;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLmsLinkRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeVersionRepository;
import za.co.handyflow.platform.trainingprovider.dto.LmsLinkDtos.*;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvLmsLinkServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final String URL = "https://lms.example.org/course/7";

    @Mock TrainProvLmsLinkRepository links;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvProgrammeRepository programmes;
    @InjectMocks TrainProvLmsLinkService service;

    private TrainProvProgrammeVersion version;
    private UUID versionId;

    @BeforeEach
    void setUp() {
        versionId = UUID.randomUUID();
        version = mock(TrainProvProgrammeVersion.class);
        lenient().when(version.getId()).thenReturn(versionId);
        lenient().when(version.getStatus()).thenReturn("APPROVED");
        lenient().when(versions.findForTenant(any(), any())).thenReturn(Optional.of(version));
    }

    @Test
    void addsAnActiveLink() {
        LinkView v = service.create(TENANT, new LinkRequest(versionId, "Moodle", URL, null), USER);
        assertThat(v.status()).isEqualTo("ACTIVE");
        verify(links).save(any(TrainProvLmsLink.class));
    }

    @Test
    void refusesSecondActiveLinkAndSupersededVersion() {
        when(links.activeExists(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, new LinkRequest(versionId, "Moodle", URL, null), USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already has an active link");
        when(version.getStatus()).thenReturn("SUPERSEDED");
        assertThatThrownBy(() -> service.create(TENANT, new LinkRequest(versionId, "Moodle", URL, null), USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("superseded");
        verify(links, never()).save(any());
    }

    @Test
    void refusesAddressWithAToken() {
        assertThatThrownBy(() -> service.create(TENANT, new LinkRequest(versionId, "Moodle", URL + "?token=abc", null), USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("password, key or token");
    }

    @Test
    void endsWithAReasonThenCannotChange() {
        TrainProvLmsLink l = TrainProvLmsLink.create(TENANT, versionId, "Moodle", URL, null, USER);
        when(links.findForTenant(any(), any())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.end(TENANT, l.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.end(TENANT, l.getId(), "Contract with the system ended", USER).status()).isEqualTo("ENDED");
        assertThatThrownBy(() -> service.update(TENANT, l.getId(), new LinkUpdate("Moodle", URL, null))).isInstanceOf(HandyFlowException.class).hasMessageContaining("ended link");
    }
}
