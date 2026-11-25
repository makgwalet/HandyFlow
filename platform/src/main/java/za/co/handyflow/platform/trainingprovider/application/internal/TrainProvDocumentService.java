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
import za.co.handyflow.platform.trainingprovider.domain.rules.DocumentRules;
import za.co.handyflow.platform.trainingprovider.dto.DocumentDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The provider's own learner documents. Wording comes from DocumentRules and is fixed when the document is issued. A completion letter is only
 * issued from a standing completed outcome. Nothing here says a learner is competent, certified or accredited, and documents are never deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvDocumentService {

    private final TrainProvLearnerDocumentRepository documents;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvCompletionRepository completions;
    private final TrainProvNumberGenerator numbers;

    public List<DocumentView> list(TenantId t, UUID enrolmentId, UUID learnerId, String docType, String status) {
        return documents.search(t, enrolmentId, learnerId, blank(docType), blank(status)).stream().<DocumentView>map(d -> view(t, d)).toList();
    }

    public DocumentView get(TenantId t, UUID id) {
        return view(t, find(t, id));
    }

    @Transactional
    public DocumentView issue(TenantId t, IssueRequest r, UUID by, LocalDate today) {
        TrainProvLearnerEnrolment e = enrolments.findForTenant(t, r.enrolmentId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", r.enrolmentId().toString()));
        TrainProvCompletion c = completions.findLiveForEnrolment(t, e.getId()).orElse(null);
        boolean live = r.docType() != null && documents.liveExists(t, e.getId(), r.docType());
        String problem = DocumentRules.issueProblem(r.docType(), e.getStatus(), live, c == null ? null : c.getOutcome(), c == null ? null : c.getStatus());
        if (problem != null) {
            boolean shape = r.docType() == null || !DocumentRules.TYPES.contains(r.docType());
            throw new HandyFlowException(problem, shape ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT, shape ? "TP_DOCUMENT_INVALID" : "TP_DOCUMENT_STATE");
        }
        boolean letter = DocumentRules.COMPLETION_LETTER.equals(r.docType());
        TrainProvLearner l = learners.findForTenant(t, e.getLearnerId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", e.getLearnerId().toString()));
        TrainProvProgramme p = programmes.findForTenant(t, e.getProgrammeId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", e.getProgrammeId().toString()));
        Integer versionNo = versions.findForTenant(t, e.getProgrammeVersionId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null);
        TrainProvLearnerDocument d = TrainProvLearnerDocument.issue(t, numbers.nextDocumentNumber(t), r.docType(), e.getId(), e.getLearnerId(), letter ? c.getId() : null,
                l.getFirstNames() + " " + l.getSurname(), p.getTitle(), versionNo, today, letter ? c.getOutcomeOn() : null, by);
        documents.save(d);
        return view(t, d);
    }

    @Transactional
    public DocumentView handOver(TenantId t, UUID id, HandOverRequest r, UUID by, LocalDate today) {
        TrainProvLearnerDocument d = find(t, id);
        guard(() -> { d.handOver(r.handedOverOn(), r.handedOverTo(), r.method(), by, today); return null; });
        documents.save(d);
        return view(t, d);
    }

    @Transactional
    public DocumentView voidDocument(TenantId t, UUID id, String reason, UUID by) {
        TrainProvLearnerDocument d = find(t, id);
        guard(() -> { d.voidIt(reason, by); return null; });
        documents.save(d);
        return view(t, d);
    }

    private DocumentView view(TenantId t, TrainProvLearnerDocument d) {
        TrainProvLearnerEnrolment e = enrolments.findForTenant(t, d.getEnrolmentId()).orElse(null);
        boolean stands = d.getCompletionId() == null || completions.findLiveForEnrolment(t, d.getEnrolmentId()).map(c -> c.getId().equals(d.getCompletionId())).orElse(false);
        return new DocumentView(d.getId(), d.getDocumentNo(), d.getDocType(), d.getEnrolmentId(), e == null ? null : e.getEnrolmentNo(), d.getLearnerId(), d.getLearnerName(),
                d.getProgrammeTitle(), d.getVersionNo(), d.getIssuedOn(), d.getTitle(), d.getBody(), d.getDisclaimer(), d.getStatus(), d.getVoidReason(), d.getHandedOverOn(),
                d.getHandedOverTo(), d.getHandedOverMethod(), stands);
    }

    private TrainProvLearnerDocument find(TenantId t, UUID id) {
        return documents.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerDocument", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_DOCUMENT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_DOCUMENT_INVALID");
        }
    }
}
