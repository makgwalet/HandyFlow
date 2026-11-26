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
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeNeedsDtos.CohortReadiness;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeNeedsDtos.NeedRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvProgrammeNeedsServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock private TrainProvProgrammeResourceNeedRepository needs;
    @Mock private TrainProvProgrammeVersionRepository versions;
    @Mock private TrainProvCohortRepository cohorts;
    @Mock private TrainProvCohortMemberRepository members;
    @Mock private TrainProvSiteResourceRepository resources;
    @Mock private TrainProvDeliverySiteRepository sites;
    @InjectMocks private TrainProvProgrammeNeedsService service;

    private final UUID versionId = UUID.randomUUID();

    private NeedRequest request() {
        return new NeedRequest("EQUIPMENT", "Arc welding machine", "welding", 2, 4, "Listed in the programme's own curriculum");
    }

    @Test
    void anApprovedVersionCannotHaveNeedsAdded() {
        TrainProvProgrammeVersion v = mock(TrainProvProgrammeVersion.class);
        doThrow(new IllegalStateException("Only a draft version can be changed")).when(v).requireDraft();
        when(versions.findForTenant(TENANT, versionId)).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> service.add(TENANT, versionId, request(), USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("draft");
        verify(needs, never()).save(any(TrainProvProgrammeResourceNeed.class));
    }

    @Test
    void aBadNeedIsRefusedWithPlainWords() {
        TrainProvProgrammeVersion v = mock(TrainProvProgrammeVersion.class);
        when(versions.findForTenant(TENANT, versionId)).thenReturn(Optional.of(v));
        NeedRequest bad = new NeedRequest("EQUIPMENT", "Arc welding machine", null, 2, null, "short");
        assertThatThrownBy(() -> service.add(TENANT, versionId, bad, USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("at least 10");
    }

    @Test
    void readinessCountsOnlyMatchingInServiceResourcesAtTheCohortsSite() {
        UUID cohortId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        TrainProvCohort c = mock(TrainProvCohort.class);
        when(c.getId()).thenReturn(cohortId);
        when(c.getSiteId()).thenReturn(siteId);
        when(c.getProgrammeVersionId()).thenReturn(versionId);
        when(cohorts.findForTenant(TENANT, cohortId)).thenReturn(Optional.of(c));
        when(members.countActive(TENANT, cohortId)).thenReturn(10L);
        TrainProvSiteResource rig = mock(TrainProvSiteResource.class);
        when(rig.getResourceType()).thenReturn("EQUIPMENT");
        when(rig.getName()).thenReturn("Arc Welding Machine");
        when(rig.getQuantity()).thenReturn(4);
        TrainProvSiteResource lift = mock(TrainProvSiteResource.class);
        when(lift.getResourceType()).thenReturn("EQUIPMENT");
        when(lift.getName()).thenReturn("Forklift");
        when(resources.search(TENANT, siteId, "IN_SERVICE", null)).thenReturn(List.of(rig, lift));
        TrainProvProgrammeResourceNeed need = TrainProvProgrammeResourceNeed.create(TENANT, versionId, "EQUIPMENT", "Arc welding machine", "welding", 2, 4, "Listed in the programme's own curriculum", USER);
        when(needs.live(TENANT, versionId)).thenReturn(List.of(need));
        when(sites.findForTenant(TENANT, siteId)).thenReturn(Optional.empty());

        CohortReadiness r = service.readiness(TENANT, cohortId);

        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).required()).isEqualTo(6);
        assertThat(r.rows().get(0).held()).isEqualTo(4);
        assertThat(r.rows().get(0).status()).isEqualTo("SHORT");
        assertThat(r.note()).contains("prompt to check").contains("does not show that the programme meets any standard");
    }
}
