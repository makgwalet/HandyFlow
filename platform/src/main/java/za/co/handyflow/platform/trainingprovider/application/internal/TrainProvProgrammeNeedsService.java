package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.ProgrammeNeedsRules;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeNeedsDtos.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Resources a programme version lists as needed, and how a cohort's site register compares. Needs are recorded only while the version is a
 * draft, each with a note of where it comes from, and withdrawn with a reason, never deleted. The comparison is a prompt to check: it counts
 * in-service resources in the site register and never says a programme meets or fails any standard.
 */
@Service
@RequiredArgsConstructor
public class TrainProvProgrammeNeedsService {

    private final TrainProvProgrammeResourceNeedRepository needs;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvSiteResourceRepository resources;
    private final TrainProvDeliverySiteRepository sites;

    public VersionNeeds get(TenantId t, UUID versionId) {
        TrainProvProgrammeVersion v = version(t, versionId);
        return new VersionNeeds(v.getId(), v.getVersionNo(), v.getStatus(), "DRAFT".equals(v.getStatus()), needs.forVersion(t, versionId).stream().<NeedView>map(TrainProvProgrammeNeedsService::view).toList());
    }

    @Transactional
    public VersionNeeds add(TenantId t, UUID versionId, NeedRequest r, UUID by) {
        TrainProvProgrammeVersion v = version(t, versionId);
        guardRun(v::requireDraft);
        TrainProvProgrammeResourceNeed n = guard(() -> TrainProvProgrammeResourceNeed.create(t, versionId, r.resourceType(), r.description(), r.matchText(), r.quantity(), r.perLearners(), r.sourceNote(), by));
        needs.save(n);
        return get(t, versionId);
    }

    @Transactional
    public VersionNeeds withdraw(TenantId t, UUID needId, String reason, UUID by) {
        TrainProvProgrammeResourceNeed n = needs.findForTenant(t, needId).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeResourceNeed", needId.toString()));
        guardRun(version(t, n.getProgrammeVersionId())::requireDraft);
        guard(() -> { n.withdraw(reason, by); return null; });
        needs.save(n);
        return get(t, n.getProgrammeVersionId());
    }

    /** How the cohort's site register compares with what the cohort's programme version lists. A prompt, never a finding. */
    public CohortReadiness readiness(TenantId t, UUID cohortId) {
        TrainProvCohort c = cohorts.findForTenant(t, cohortId).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", cohortId.toString()));
        long learners = members.countActive(t, cohortId);
        List<TrainProvSiteResource> inService = resources.search(t, c.getSiteId(), "IN_SERVICE", null);
        List<ReadinessRow> rows = needs.live(t, c.getProgrammeVersionId()).stream().map(n -> {
            int held = inService.stream().filter(r -> r.getResourceType().equals(n.getResourceType()) && ProgrammeNeedsRules.matches(r.getName(), n.getMatchText()))
                    .mapToInt(TrainProvSiteResource::getQuantity).sum();
            int required = ProgrammeNeedsRules.required(n.getQuantity(), n.getPerLearners(), learners);
            String status = ProgrammeNeedsRules.readiness(required, held);
            return new ReadinessRow(n.getId(), n.getDescription(), n.getResourceType(), required, held, status, ProgrammeNeedsRules.note(status, required, held));
        }).toList();
        String siteName = sites.findForTenant(t, c.getSiteId()).map(TrainProvDeliverySite::getName).orElse("");
        return new CohortReadiness(c.getId(), siteName, learners, "A comparison of the site register with the programme's own list. It is a prompt to check and does not show that the programme meets any standard.", rows);
    }

    private static NeedView view(TrainProvProgrammeResourceNeed n) {
        return new NeedView(n.getId(), n.getProgrammeVersionId(), n.getResourceType(), n.getDescription(), n.getMatchText(), n.getQuantity(), n.getPerLearners(), n.getSourceNote(),
                n.getStatus(), n.getWithdrawnReason(), n.getCreatedAt());
    }

    private TrainProvProgrammeVersion version(TenantId t, UUID id) {
        return versions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeVersion", id.toString()));
    }

    private static void guardRun(Runnable work) {
        guard(() -> {
            work.run();
            return null;
        });
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_PROGRAMME_NEEDS_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_PROGRAMME_NEEDS_INVALID");
        }
    }
}
