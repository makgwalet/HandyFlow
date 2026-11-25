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
import za.co.handyflow.platform.trainingprovider.domain.rules.LessonRules;
import za.co.handyflow.platform.trainingprovider.dto.LessonDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Learning content and learner progress. A lesson is approved by a second person or by the author with a written reason, and is never edited
 * once approved. Progress records that a learner finished an approved lesson of their own programme version. It is not a competency decision,
 * attendance or a grade, and a record is reversed with a reason, never deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvLessonService {

    private final TrainProvLessonRepository lessons;
    private final TrainProvLessonProgressRepository progress;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvProgrammeModuleRepository modules;
    private final TrainProvNumberGenerator numbers;

    public List<LessonView> list(TenantId t, UUID versionId, UUID moduleId, String status) {
        return lessons.search(t, versionId, moduleId, blank(status)).stream().<LessonView>map(l -> view(t, l)).toList();
    }

    public LessonView get(TenantId t, UUID id) {
        return view(t, lesson(t, id));
    }

    @Transactional
    public LessonView create(TenantId t, LessonRequest r, UUID by) {
        TrainProvProgrammeVersion v = versions.findForTenant(t, r.programmeVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeVersion", r.programmeVersionId().toString()));
        if ("SUPERSEDED".equals(v.getStatus())) {
            throw new HandyFlowException("That programme version has been superseded. Add the lesson to the current version", HttpStatus.CONFLICT, "TP_LESSON_STATE");
        }
        checkModule(t, r.moduleId(), v.getId());
        checkTitle(t, v.getId(), r.title(), null);
        TrainProvLesson l = guard(() -> TrainProvLesson.create(t, numbers.nextLessonNumber(t), v.getId(), r.moduleId(), r.title(), r.kind(), r.contentText(), r.url(), r.position(), by));
        lessons.save(l);
        return view(t, l);
    }

    @Transactional
    public LessonView update(TenantId t, UUID id, LessonUpdate r, UUID by) {
        TrainProvLesson l = lesson(t, id);
        checkModule(t, r.moduleId(), l.getProgrammeVersionId());
        checkTitle(t, l.getProgrammeVersionId(), r.title(), l.getId());
        guard(() -> { l.change(r.moduleId(), r.title(), r.kind(), r.contentText(), r.url(), r.position()); return null; });
        lessons.save(l);
        return view(t, l);
    }

    @Transactional
    public LessonView approve(TenantId t, UUID id, String overrideReason, UUID by) {
        TrainProvLesson l = lesson(t, id);
        guard(() -> { l.approve(by, overrideReason); return null; });
        lessons.save(l);
        return view(t, l);
    }

    @Transactional
    public LessonView retire(TenantId t, UUID id, String reason, UUID by) {
        TrainProvLesson l = lesson(t, id);
        guard(() -> { l.retire(reason, by); return null; });
        lessons.save(l);
        return view(t, l);
    }

    public ProgressView progress(TenantId t, UUID enrolmentId) {
        TrainProvLearnerEnrolment e = enrolment(t, enrolmentId);
        List<TrainProvLesson> approved = lessons.search(t, e.getProgrammeVersionId(), null, AssessmentRules.APPROVED);
        Map<UUID, TrainProvLessonProgress> done = progress.forEnrolment(t, enrolmentId).stream().filter(p -> LessonRules.FINISHED.equals(p.getStatus()))
                .collect(Collectors.toMap(TrainProvLessonProgress::getLessonId, Function.identity(), (a, b) -> a));
        List<ProgressRow> rows = new ArrayList<>();
        for (TrainProvLesson l : approved) {
            TrainProvLessonProgress p = done.get(l.getId());
            rows.add(new ProgressRow(l.getId(), l.getLessonNo(), l.getTitle(), l.getKind(), l.getModuleId(), l.getPositionNo(), p == null ? null : p.getId(),
                    p == null ? null : p.getFinishedOn(), p == null ? null : p.getNote()));
        }
        int finished = (int) rows.stream().filter(r -> r.progressId() != null).count();
        String learner = learners.findForTenant(t, e.getLearnerId()).map(x -> x.getFirstNames() + " " + x.getSurname()).orElse(null);
        String programme = programmes.findForTenant(t, e.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        Integer versionNo = versions.findForTenant(t, e.getProgrammeVersionId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null);
        return new ProgressView(e.getId(), e.getEnrolmentNo(), learner, programme, versionNo, rows, finished, rows.size(), LessonRules.summary(finished, rows.size()));
    }

    @Transactional
    public ProgressRecord finish(TenantId t, FinishRequest r, UUID by, LocalDate today) {
        TrainProvLearnerEnrolment e = enrolment(t, r.enrolmentId());
        TrainProvLesson l = lesson(t, r.lessonId());
        String problem = LessonRules.finishProblem(e.getStatus(), e.getStartDate(), e.getProgrammeVersionId().equals(l.getProgrammeVersionId()), l.getStatus(),
                progress.finishedExists(t, e.getId(), l.getId()), r.finishedOn(), today);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_PROGRESS_STATE");
        }
        TrainProvLessonProgress p = TrainProvLessonProgress.finish(t, e.getId(), l.getId(), r.finishedOn(), r.note(), by);
        progress.save(p);
        return record(p);
    }

    @Transactional
    public ProgressRecord reverse(TenantId t, UUID progressId, String reason, UUID by) {
        TrainProvLessonProgress p = progress.findForTenant(t, progressId).orElseThrow(() -> new ResourceNotFoundException("TrainProvLessonProgress", progressId.toString()));
        guard(() -> { p.reverse(reason, by); return null; });
        progress.save(p);
        return record(p);
    }

    private static ProgressRecord record(TrainProvLessonProgress p) {
        return new ProgressRecord(p.getId(), p.getEnrolmentId(), p.getLessonId(), p.getFinishedOn(), p.getNote(), p.getStatus(), p.getReversedReason());
    }

    private void checkModule(TenantId t, UUID moduleId, UUID versionId) {
        if (moduleId == null) {
            return;
        }
        TrainProvProgrammeModule m = modules.findForTenant(t, moduleId).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgrammeModule", moduleId.toString()));
        String problem = LessonRules.moduleProblem(m.getProgrammeVersionId(), versionId);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_LESSON_INVALID");
        }
    }

    private void checkTitle(TenantId t, UUID versionId, String title, UUID excludeId) {
        if (title != null && lessons.liveTitleTaken(t, versionId, title.trim(), excludeId)) {
            throw new HandyFlowException("This version already has a live lesson with that title", HttpStatus.CONFLICT, "TP_LESSON_DUPLICATE");
        }
    }

    private LessonView view(TenantId t, TrainProvLesson l) {
        Integer versionNo = versions.findForTenant(t, l.getProgrammeVersionId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null);
        String moduleTitle = l.getModuleId() == null ? null : modules.findForTenant(t, l.getModuleId()).map(TrainProvProgrammeModule::getTitle).orElse(null);
        return new LessonView(l.getId(), l.getLessonNo(), l.getProgrammeVersionId(), versionNo, l.getModuleId(), moduleTitle, l.getTitle(), l.getKind(), l.getContentText(), l.getUrl(),
                l.getPositionNo(), l.getStatus(), l.getApprovedAt(), l.getApprovalOverrideReason(), l.getRetiredReason(), List.copyOf(AssessmentRules.nextStatuses(l.getStatus())));
    }

    private TrainProvLesson lesson(TenantId t, UUID id) {
        return lessons.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLesson", id.toString()));
    }

    private TrainProvLearnerEnrolment enrolment(TenantId t, UUID id) {
        return enrolments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_LESSON_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_LESSON_INVALID");
        }
    }
}
