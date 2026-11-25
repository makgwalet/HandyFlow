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
import za.co.handyflow.platform.trainingprovider.domain.rules.AssessmentRules;
import za.co.handyflow.platform.trainingprovider.dto.AssessmentDtos.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Assessment instruments for a programme version. A draft can be edited; approval is by a second person, or by the author with a written
 * reason that is kept. An approved instrument is never edited: it is retired with a reason and replaced. No pass mark is held anywhere.
 */
@Service
@RequiredArgsConstructor
public class TrainProvAssessmentService {

    private final TrainProvAssessmentInstrumentRepository instruments;
    private final TrainProvAssessmentInstrumentEventRepository events;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvProgrammeModuleRepository modules;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvNumberGenerator numbers;

    public List<InstrumentView> list(TenantId t, UUID versionId, UUID moduleId, String status) {
        return instruments.search(t, versionId, moduleId, blank(status)).stream().<InstrumentView>map(i -> view(t, i, false)).toList();
    }

    public InstrumentView get(TenantId t, UUID id) {
        return view(t, instrument(t, id), true);
    }

    @Transactional
    public InstrumentView create(TenantId t, InstrumentRequest r, UUID by) {
        TrainProvProgrammeVersion v = version(t, r.programmeVersionId());
        if ("SUPERSEDED".equals(v.getStatus())) {
            throw new HandyFlowException("That programme version has been superseded. Add the instrument to the current version", HttpStatus.CONFLICT, "TP_INSTRUMENT_STATE");
        }
        checkModule(t, r.moduleId(), v.getId());
        checkTitle(t, v.getId(), r.title(), null);
        TrainProvAssessmentInstrument i = guard(() -> TrainProvAssessmentInstrument.create(t, numbers.nextInstrumentNumber(t), v.getId(), r.moduleId(),
                r.title(), r.assessmentType(), r.maxMark(), r.guidance(), r.requiresModeration() == null || r.requiresModeration(), by));
        instruments.save(i);
        events.save(TrainProvAssessmentInstrumentEvent.of(t, i.getId(), "CREATED", null, by));
        return view(t, i, true);
    }

    @Transactional
    public InstrumentView update(TenantId t, UUID id, InstrumentUpdate r, UUID by) {
        TrainProvAssessmentInstrument i = instrument(t, id);
        checkModule(t, r.moduleId(), i.getProgrammeVersionId());
        checkTitle(t, i.getProgrammeVersionId(), r.title(), i.getId());
        guard(() -> {
            i.change(r.moduleId(), r.title(), r.assessmentType(), r.maxMark(), r.guidance(), r.requiresModeration() == null || r.requiresModeration());
            return null;
        });
        instruments.save(i);
        events.save(TrainProvAssessmentInstrumentEvent.of(t, i.getId(), "CHANGED", null, by));
        return view(t, i, true);
    }

    @Transactional
    public InstrumentView approve(TenantId t, UUID id, String overrideReason, UUID by) {
        TrainProvAssessmentInstrument i = instrument(t, id);
        guard(() -> { i.approve(by, overrideReason); return null; });
        instruments.save(i);
        events.save(TrainProvAssessmentInstrumentEvent.of(t, i.getId(), "APPROVED", i.getApprovalOverrideReason(), by));
        return view(t, i, true);
    }

    @Transactional
    public InstrumentView retire(TenantId t, UUID id, String reason, UUID by) {
        TrainProvAssessmentInstrument i = instrument(t, id);
        guard(() -> { i.retire(reason, by); return null; });
        instruments.save(i);
        events.save(TrainProvAssessmentInstrumentEvent.of(t, i.getId(), "RETIRED", i.getRetiredReason(), by));
        return view(t, i, true);
    }

    private void checkModule(TenantId t, UUID moduleId, UUID versionId) {
        if (moduleId == null) {
            return;
        }
        TrainProvProgrammeModule m = modules.findForTenant(t, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeModule", moduleId.toString()));
        String problem = AssessmentRules.moduleProblem(m.getProgrammeVersionId(), versionId);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_INSTRUMENT_INVALID");
        }
    }

    private void checkTitle(TenantId t, UUID versionId, String title, UUID excludeId) {
        if (title != null && instruments.liveTitleTaken(t, versionId, title.trim(), excludeId)) {
            throw new HandyFlowException("This version already has a live instrument with that title", HttpStatus.CONFLICT, "TP_INSTRUMENT_DUPLICATE");
        }
    }

    private InstrumentView view(TenantId t, TrainProvAssessmentInstrument i, boolean withHistory) {
        TrainProvProgrammeVersion v = version(t, i.getProgrammeVersionId());
        String programmeTitle = programmes.findForTenant(t, v.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        String moduleTitle = i.getModuleId() == null ? null : modules.findForTenant(t, i.getModuleId()).map(TrainProvProgrammeModule::getTitle).orElse(null);
        List<InstrumentEvent> history = withHistory
                ? events.findForInstrument(t, i.getId()).stream().<InstrumentEvent>map(e -> new InstrumentEvent(e.getId(), e.getAction(), e.getNote(), e.getActedBy(), e.getActedAt())).toList()
                : List.of();
        return new InstrumentView(i.getId(), i.getInstrumentNo(), v.getProgrammeId(), programmeTitle, v.getId(), v.getVersionNo(), i.getModuleId(), moduleTitle,
                i.getTitle(), i.getAssessmentType(), i.getMaxMark(), i.getGuidance(), i.isRequiresModeration(), i.getStatus(), i.getApprovedAt(),
                i.getApprovalOverrideReason(), i.getRetiredReason(), List.copyOf(AssessmentRules.nextStatuses(i.getStatus())), history);
    }

    private TrainProvAssessmentInstrument instrument(TenantId t, UUID id) {
        return instruments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAssessmentInstrument", id.toString()));
    }

    private TrainProvProgrammeVersion version(TenantId t, UUID id) {
        return versions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeVersion", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_INSTRUMENT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_INSTRUMENT_INVALID");
        }
    }
}
