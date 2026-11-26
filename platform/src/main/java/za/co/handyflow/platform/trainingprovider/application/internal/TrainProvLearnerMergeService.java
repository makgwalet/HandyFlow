package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEvent;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerMerge;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules.Match;
import za.co.handyflow.platform.trainingprovider.dto.LearnerMatchDtos.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Joins two learner records for one person. A person proposes it and says why; a second person approves it (or the same person says
 * why alone). Approving moves the merged learner's enrolments, applications and documents to the learner being kept, archives the other
 * record pointing at the kept one, and writes a history line on both. Nothing is deleted and nothing on an enrolment is rewritten: each
 * enrolment keeps its programme version, site, sponsoring organisation and history, and each document keeps the name it was issued in.
 */
@Service
@RequiredArgsConstructor
public class TrainProvLearnerMergeService {

    private final TrainProvLearnerMergeRepository merges;
    private final TrainProvLearnerRepository learners;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerApplicationRepository applications;
    private final TrainProvLearnerDocumentRepository documents;
    private final TrainProvLearnerEventRepository events;

    public List<MergeView> list(TenantId t, String status) {
        return merges.search(t, status == null || status.isBlank() ? null : status).stream().<MergeView>map(m -> view(t, m)).toList();
    }

    public MergeView get(TenantId t, UUID id) {
        return view(t, merge(t, id));
    }

    public List<MergeView> forLearner(TenantId t, UUID learnerId) {
        return merges.forLearner(t, learnerId).stream().<MergeView>map(m -> view(t, m)).toList();
    }

    @Transactional
    public MergeView propose(TenantId t, MergeProposalRequest r, UUID by) {
        TrainProvLearner keep = learner(t, r.survivorId());
        TrainProvLearner drop = learner(t, r.mergedId());
        Match match = LearnerMatchRules.compare(TrainProvLearnerMatchService.learnerPerson(keep), TrainProvLearnerMatchService.learnerPerson(drop));
        String p = LearnerMatchRules.proposalProblem(keep.getId(), drop.getId(), keep.getStatus(), drop.getStatus(), keep.getMergedIntoId(),
                drop.getMergedIntoId(), match.conflicts(), r.conflictsAccepted(), r.reason());
        if (p != null) {
            throw new HandyFlowException(p, HttpStatus.BAD_REQUEST, "TP_MERGE_INVALID");
        }
        if (merges.openFor(t, drop.getId())) {
            throw new HandyFlowException("A merge of " + drop.fullName() + " is already waiting for a decision", HttpStatus.CONFLICT, "TP_MERGE_STATE");
        }
        TrainProvLearnerMerge m = TrainProvLearnerMerge.propose(t, keep.getId(), drop.getId(), String.join(", ", match.reasons()),
                String.join("; ", match.conflicts()), r.conflictsAccepted(), r.reason(), snapshot(keep), snapshot(drop), by);
        merges.save(m);
        return view(t, m);
    }

    @Transactional
    public MergeView approve(TenantId t, UUID id, MergeDecisionRequest r, UUID by) {
        TrainProvLearnerMerge m = merge(t, id);
        String why = LearnerMatchRules.decisionProblem(m.getStatus(), m.getProposedBy(), by, r.confirmAlone(), r.singleReason(), r.reason());
        if (why != null) {
            throw new HandyFlowException(why, HttpStatus.CONFLICT, "TP_MERGE_STATE");
        }
        TrainProvLearner keep = learner(t, m.getSurvivorId());
        TrainProvLearner drop = learner(t, m.getMergedId());
        if (!"ACTIVE".equals(keep.getStatus()) || !"ACTIVE".equals(drop.getStatus())) {
            throw new HandyFlowException("One of the learners is no longer live, so this merge cannot go ahead. Reject it", HttpStatus.CONFLICT, "TP_MERGE_STATE");
        }
        List<UUID> clash = LearnerMatchRules.clashes(programmes(t, keep.getId()), programmes(t, drop.getId()));
        if (!clash.isEmpty()) {
            throw new HandyFlowException("Both learners are live on the same programme (" + clash.size() + "). Finish or withdraw one of them first, "
                    + "so the person is not enrolled twice", HttpStatus.CONFLICT, "TP_MERGE_CLASH");
        }
        String delegateNote = null;
        UUID keepDelegate = keep.getDelegateId();
        UUID dropDelegate = drop.getDelegateId();
        merged(drop, keep, m, by);
        learners.saveAndFlush(drop);
        if (dropDelegate != null && keepDelegate == null) {
            keep.relink(dropDelegate);
            events.save(TrainProvLearnerEvent.delegate(t, keep.getId(), true, dropDelegate, "Taken over from merged learner " + drop.getLearnerNo(), by));
            delegateNote = "The delegate link of " + drop.getLearnerNo() + " now sits on " + keep.getLearnerNo();
        } else if (dropDelegate != null && !dropDelegate.equals(keepDelegate)) {
            delegateNote = "The merged learner was linked to a different delegate. That delegate is now unmatched and can be linked or left";
        }
        learners.save(keep);
        int e = enrolments.moveLearner(t, drop.getId(), keep.getId());
        int a = applications.moveLearner(t, drop.getId(), keep.getId());
        int d = documents.moveLearner(t, drop.getId(), keep.getId());
        events.save(TrainProvLearnerEvent.merge(t, drop.getId(), true, keep.getId(), m.getReason(), by));
        events.save(TrainProvLearnerEvent.merge(t, keep.getId(), false, drop.getId(), m.getReason(), by));
        final String note = delegateNote;
        guard(() -> { m.complete(by, r.reason(), r.confirmAlone(), r.singleReason(), e, a, d, note); return null; });
        merges.save(m);
        return view(t, m);
    }

    @Transactional
    public MergeView reject(TenantId t, UUID id, MergeDecisionRequest r, UUID by) {
        TrainProvLearnerMerge m = merge(t, id);
        guard(() -> { m.reject(by, r.reason(), r.confirmAlone(), r.singleReason()); return null; });
        merges.save(m);
        return view(t, m);
    }

    @Transactional
    public MergeView cancel(TenantId t, UUID id, String reason, UUID by) {
        TrainProvLearnerMerge m = merge(t, id);
        guard(() -> { m.cancel(by, reason); return null; });
        merges.save(m);
        return view(t, m);
    }

    private static void merged(TrainProvLearner drop, TrainProvLearner keep, TrainProvLearnerMerge m, UUID by) {
        guard(() -> { drop.mergeInto(keep.getId(), "Merged into " + keep.getLearnerNo() + ": " + m.getReason(), by); return null; });
    }

    private List<UUID> programmes(TenantId t, UUID learnerId) {
        List<UUID> all = new ArrayList<>(enrolments.liveProgrammeIds(t, learnerId));
        applications.openProgrammeIds(t, learnerId).forEach(p -> {
            if (!all.contains(p)) {
                all.add(p);
            }
        });
        return all;
    }

    private static String snapshot(TrainProvLearner l) {
        return LearnerMatchRules.snapshot(l.getLearnerNo(), l.fullName(), l.getIdType(), l.getIdNumber(), l.getDateOfBirth(), l.getEmail(),
                l.getPhone(), l.getClientId(), l.getDelegateId());
    }

    private MergeView view(TenantId t, TrainProvLearnerMerge m) {
        TrainProvLearner keep = learners.findForTenant(t, m.getSurvivorId()).orElse(null);
        TrainProvLearner drop = learners.findForTenant(t, m.getMergedId()).orElse(null);
        return new MergeView(m.getId(), m.getStatus(), m.getSurvivorId(), keep == null ? null : keep.fullName(), keep == null ? null : keep.getLearnerNo(),
                m.getMergedId(), drop == null ? null : drop.fullName(), drop == null ? null : drop.getLearnerNo(), split(m.getMatchReasons(), ", "),
                split(m.getConflicts(), "; "), m.isConflictsAccepted(), m.getReason(), m.getSurvivorSnapshot(), m.getMergedSnapshot(), m.getProposedBy(),
                m.getProposedAt(), m.getDecidedBy(), m.getDecidedAt(), m.getDecisionReason(), m.getMovedEnrolments(), m.getMovedApplications(),
                m.getMovedDocuments(), m.getDelegateNote());
    }

    private static List<String> split(String s, String sep) {
        return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split(java.util.regex.Pattern.quote(sep)));
    }

    private TrainProvLearnerMerge merge(TenantId t, UUID id) {
        return merges.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerMerge", id.toString()));
    }

    private TrainProvLearner learner(TenantId t, UUID id) {
        return learners.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_MERGE_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_MERGE_INVALID");
        }
    }
}
