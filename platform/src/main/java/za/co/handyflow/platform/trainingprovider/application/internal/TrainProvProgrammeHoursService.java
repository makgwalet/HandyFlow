package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeVersion;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeWorkplaceHours;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeVersionRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeWorkplaceHoursRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.ProgrammeHoursRules;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeHoursDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Optional workplace hours on a programme version. Recorded only with a source note, and only while the version is a draft: an approved
 * version is frozen so a learner keeps the figure they studied under. A change after approval is a new version. Never defaulted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvProgrammeHoursService {

    private final TrainProvProgrammeWorkplaceHoursRepository hours;
    private final TrainProvProgrammeVersionRepository versions;

    public VersionHours get(TenantId t, UUID versionId) {
        TrainProvProgrammeVersion v = version(t, versionId);
        List<HoursView> history = hours.forVersion(t, versionId).stream().<HoursView>map(TrainProvProgrammeHoursService::view).toList();
        return new VersionHours(v.getId(), v.getVersionNo(), v.getStatus(), "DRAFT".equals(v.getStatus()), history.stream().filter(h -> "ACTIVE".equals(h.status())).findFirst().orElse(null), history);
    }

    /** The live figure for a version, if the programme records one. */
    public Optional<HoursView> live(TenantId t, UUID versionId) {
        return hours.findLive(t, versionId).map(TrainProvProgrammeHoursService::view);
    }

    @Transactional
    public VersionHours record(TenantId t, UUID versionId, HoursRequest r, UUID by, LocalDate today) {
        TrainProvProgrammeVersion v = version(t, versionId);
        guardRun(v::requireDraft);
        ProgrammeHoursRules.Entry e = new ProgrammeHoursRules.Entry(r.hours(), r.sourceType(), r.sourceReference(), r.sourceVersionDate(), r.sourceSection(), r.effectiveDate(),
                r.verifiedBy(), r.verifiedOn());
        String problem = ProgrammeHoursRules.problem(e, today);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_PROGRAMME_HOURS_INVALID");
        }
        Optional<TrainProvProgrammeWorkplaceHours> live = hours.findLive(t, versionId);
        if (live.isPresent()) {
            live.get().apply(e, r.covers(), by);
            hours.save(live.get());
        } else {
            hours.save(TrainProvProgrammeWorkplaceHours.create(t, versionId, e, r.covers(), by));
        }
        return get(t, versionId);
    }

    /** Takes the figure off a draft version. The record stays, withdrawn, with the reason. */
    @Transactional
    public VersionHours withdraw(TenantId t, UUID versionId, String reason, UUID by) {
        TrainProvProgrammeVersion v = version(t, versionId);
        guardRun(v::requireDraft);
        TrainProvProgrammeWorkplaceHours h = hours.findLive(t, versionId).orElseThrow(() -> new HandyFlowException("This version records no workplace hours.", HttpStatus.CONFLICT, "TP_PROGRAMME_HOURS_STATE"));
        guard(() -> h.withdraw(reason, by));
        hours.save(h);
        return get(t, versionId);
    }

    private static HoursView view(TrainProvProgrammeWorkplaceHours h) {
        return new HoursView(h.getId(), h.getProgrammeVersionId(), h.getHours(), h.getCovers(), h.getSourceType(), h.getSourceReference(), h.getSourceVersionDate(), h.getSourceSection(),
                h.getEffectiveDate(), h.getVerifiedBy(), h.getVerifiedOn(), h.getStatus(), h.getWithdrawnReason(), h.getUpdatedAt());
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
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_PROGRAMME_HOURS_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_PROGRAMME_HOURS_INVALID");
        }
    }
}
