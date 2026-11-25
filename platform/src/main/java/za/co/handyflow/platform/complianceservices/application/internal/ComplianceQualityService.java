package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.QualityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Quality review: checklists, second-person review of finished work, sending work back for rework, and random sampling of finished work.
 * It checks work. It never scores a client, marks one compliant or changes the work it reviews.
 */
@Service
@RequiredArgsConstructor
public class ComplianceQualityService {

    private final ComplianceReviewTemplateRepository templates;
    private final ComplianceReviewTemplateItemRepository templateItems;
    private final ComplianceReviewRepository reviews;
    private final ComplianceReviewItemRepository items;
    private final ComplianceClientRepository clients;
    private final ComplianceAssessmentRepository assessments;
    private final ComplianceCaseRepository cases;
    private final ComplianceQuoteRepository quotes;

    // ---- checklists ----

    @Transactional(readOnly = true)
    public List<TemplateView> templates(TenantId tenantId) {
        Map<UUID, List<ComplianceReviewTemplateItem>> byTemplate = templateItems.findAllForTenant(tenantId).stream().collect(Collectors.groupingBy(ComplianceReviewTemplateItem::getTemplateId));
        return templates.findAllForTenant(tenantId).stream().map(t -> view(t, byTemplate.getOrDefault(t.getId(), List.of()))).toList();
    }

    @Transactional
    public TemplateView createTemplate(TenantId tenantId, TemplateRequest r, UUID by) {
        nameFree(tenantId, r.name(), null);
        List<TemplateItemRequest> lines = r.items() == null ? List.of() : r.items();
        guard(() -> { ComplianceReviewTemplate.checkItems(lines.stream().map(TemplateItemRequest::text).toList()); return null; });
        ComplianceReviewTemplate t = templates.save(guard(() -> ComplianceReviewTemplate.create(tenantId, r.name(), r.subjectType(), r.description(), by)));
        return view(t, saveItems(tenantId, t.getId(), lines));
    }

    /** Replaces the checks. Reviews already done keep the wording they were done with. */
    @Transactional
    public TemplateView editTemplate(TenantId tenantId, UUID id, TemplateRequest r) {
        ComplianceReviewTemplate t = template(tenantId, id);
        nameFree(tenantId, r.name(), id);
        List<TemplateItemRequest> lines = r.items() == null ? List.of() : r.items();
        guard(() -> { ComplianceReviewTemplate.checkItems(lines.stream().map(TemplateItemRequest::text).toList()); t.edit(r.name(), r.subjectType(), r.description()); return null; });
        templates.save(t);
        templateItems.deleteAll(templateItems.findForTemplate(tenantId, id));
        templateItems.flush();
        return view(t, saveItems(tenantId, id, lines));
    }

    @Transactional
    public TemplateView setTemplateActive(TenantId tenantId, UUID id, boolean active) {
        ComplianceReviewTemplate t = template(tenantId, id);
        t.setActive(active);
        templates.save(t);
        return view(t, templateItems.findForTemplate(tenantId, id));
    }

    // ---- reviews ----

    @Transactional(readOnly = true)
    public List<ReviewSummary> list(TenantId tenantId, String status) {
        List<ComplianceReview> found = reviews.findAllForTenant(tenantId);
        if (status != null && !status.isBlank()) found = found.stream().filter(r -> r.getStatus().equals(status.trim().toUpperCase())).toList();
        return summaries(tenantId, found);
    }

    @Transactional(readOnly = true)
    public List<ReviewSummary> forClient(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return summaries(tenantId, reviews.findForClient(tenantId, clientId));
    }

    @Transactional(readOnly = true)
    public ReviewDetail get(TenantId tenantId, UUID id) {
        return detail(tenantId, review(tenantId, id));
    }

    @Transactional
    public ReviewDetail start(TenantId tenantId, UUID clientId, StartRequest r, UUID by) {
        requireClient(tenantId, clientId);
        ComplianceReviewTemplate t = template(tenantId, r.templateId());
        String type = guard(() -> QualityRules.subjectType(r.subjectType()));
        Subject subject = subjectOf(tenantId, clientId, type, r.subjectId(), r.subjectLabel());
        if (subject.id() != null) {
            List<ComplianceReview> earlier = reviews.findForSubject(tenantId, type, subject.id());
            if (!earlier.isEmpty()) {
                String last = earlier.get(0).getStatus();
                String message = "IN_PROGRESS".equals(last) ? "This work already has a review in progress"
                        : "REWORK_REQUIRED".equals(last) ? "This work was sent back for rework. Start the next round from that review" : "This work has already been reviewed and approved";
                throw new HandyFlowException(message, HttpStatus.CONFLICT, "REVIEW_EXISTS");
            }
        }
        ComplianceReview review = guard(() -> ComplianceReview.start(tenantId, clientId, type, subject.id(), subject.label(), t, subject.preparedBy(), false, by));
        reviews.save(review);
        copyItems(tenantId, review.getId(), templateItems.findForTemplate(tenantId, t.getId()));
        return detail(tenantId, review);
    }

    @Transactional
    public ReviewDetail mark(TenantId tenantId, UUID reviewId, int position, MarkRequest r) {
        ComplianceReview review = review(tenantId, reviewId);
        guard(() -> { review.requireOpen(); return null; });
        ComplianceReviewItem item = items.findForReview(tenantId, reviewId).stream().filter(i -> i.getPosition() == position).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceReviewItem", String.valueOf(position)));
        guard(() -> { item.mark(r.result(), r.note()); return null; });
        items.save(item);
        return detail(tenantId, review);
    }

    @Transactional
    public ReviewDetail complete(TenantId tenantId, UUID reviewId, CompleteRequest r, UUID by) {
        ComplianceReview review = review(tenantId, reviewId);
        List<QualityRules.Line> lines = items.findForReview(tenantId, reviewId).stream().map(ComplianceReviewItem::line).toList();
        guard(() -> { review.complete(r.outcome(), by, r.note(), r.exceptionReason(), lines); return null; });
        return detail(tenantId, reviews.save(review));
    }

    @Transactional
    public ReviewDetail nextRound(TenantId tenantId, UUID reviewId, UUID by) {
        ComplianceReview previous = review(tenantId, reviewId);
        if (reviews.findPreviousIds(tenantId).contains(previous.getId())) {
            throw new HandyFlowException("The next round has already been started", HttpStatus.CONFLICT, "REVIEW_ROUND_EXISTS");
        }
        ComplianceReview next = guard(() -> previous.nextRound(by));
        reviews.save(next);
        for (ComplianceReviewItem i : items.findForReview(tenantId, previous.getId())) {
            items.save(ComplianceReviewItem.copyOf(tenantId, next.getId(), i.getPosition(), i.getText(), i.isCritical()));
        }
        return detail(tenantId, next);
    }

    /** Picks finished work at random for a quality check. Work that has been reviewed before is left out, and only active clients are included. */
    @Transactional
    public SampleResult sample(TenantId tenantId, SampleRequest r, UUID by) {
        return sample(tenantId, r, by, new SecureRandom());
    }

    @Transactional
    public SampleResult sample(TenantId tenantId, SampleRequest r, UUID by, Random random) {
        String type = guard(() -> QualityRules.subjectType(r.subjectType()));
        if (!QualityRules.SAMPLEABLE.contains(type)) throw new HandyFlowException("Only assessments and cases can be sampled", HttpStatus.BAD_REQUEST, "REVIEW_NOT_SAMPLEABLE");
        guard(() -> { QualityRules.checkSample(r.from(), r.to(), r.ratePercent()); return null; });
        ComplianceReviewTemplate t = template(tenantId, r.templateId());
        Set<UUID> active = clients.findAll(tenantId, "ACTIVE", Pageable.unpaged()).getContent().stream().map(ComplianceClient::getId).collect(Collectors.toSet());
        Set<UUID> already = reviews.findAllForTenant(tenantId).stream().filter(x -> x.getSubjectType().equals(type) && x.getSubjectId() != null).map(ComplianceReview::getSubjectId).collect(Collectors.toSet());
        java.time.Instant from = r.from().atStartOfDay().toInstant(ZoneOffset.UTC);
        java.time.Instant to = r.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Subject> pool = new ArrayList<>();
        List<UUID> clientOf = new ArrayList<>();
        if ("ASSESSMENT".equals(type)) {
            for (ComplianceAssessment a : assessments.findCompletedBetween(tenantId, from, to)) {
                if (!active.contains(a.getClientId()) || already.contains(a.getId())) continue;
                pool.add(new Subject(a.getId(), assessmentLabel(a), a.getCompletedBy()));
                clientOf.add(a.getClientId());
            }
        } else {
            for (ComplianceCase c : cases.findFinishedBetween(tenantId, from, to)) {
                if (!active.contains(c.getClientId()) || already.contains(c.getId())) continue;
                pool.add(new Subject(c.getId(), c.getTitle(), caseDoer(c)));
                clientOf.add(c.getClientId());
            }
        }
        List<Integer> picked = QualityRules.pick(java.util.stream.IntStream.range(0, pool.size()).boxed().toList(), r.ratePercent(), random);
        List<ComplianceReviewTemplateItem> checks = templateItems.findForTemplate(tenantId, t.getId());
        for (int index : picked) {
            Subject s = pool.get(index);
            ComplianceReview review = guard(() -> ComplianceReview.start(tenantId, clientOf.get(index), type, s.id(), s.label(), t, s.preparedBy(), true, by));
            reviews.save(review);
            copyItems(tenantId, review.getId(), checks);
        }
        return new SampleResult(pool.size(), picked.size());
    }

    // ---- internals ----

    private record Subject(UUID id, String label, UUID preparedBy) {}

    private Subject subjectOf(TenantId tenantId, UUID clientId, String type, UUID subjectId, String label) {
        if ("OTHER".equals(type)) {
            if (subjectId != null) throw new HandyFlowException("Other work is described in words, not linked to a record", HttpStatus.BAD_REQUEST, "REVIEW_SUBJECT");
            return new Subject(null, guard(() -> QualityRules.text(label, "A description of the work", 250, true)), null);
        }
        if (subjectId == null) throw new HandyFlowException("Choose the " + type.toLowerCase() + " to review", HttpStatus.BAD_REQUEST, "REVIEW_SUBJECT");
        switch (type) {
            case "ASSESSMENT" -> {
                ComplianceAssessment a = assessments.findOne(tenantId, clientId, subjectId).orElseThrow(() -> new ResourceNotFoundException("ComplianceAssessment", subjectId.toString()));
                if (!"COMPLETED".equals(a.getStatus())) throw new HandyFlowException("Only a completed assessment can be reviewed", HttpStatus.CONFLICT, "REVIEW_SUBJECT_OPEN");
                return new Subject(a.getId(), blank(label) ? assessmentLabel(a) : label.trim(), a.getCompletedBy());
            }
            case "CASE" -> {
                ComplianceCase c = cases.findOne(tenantId, subjectId).filter(x -> x.getClientId().equals(clientId)).orElseThrow(() -> new ResourceNotFoundException("ComplianceCase", subjectId.toString()));
                if (!"RESOLVED".equals(c.getStatus()) && !"CLOSED".equals(c.getStatus())) throw new HandyFlowException("Only a resolved or closed case can be reviewed", HttpStatus.CONFLICT, "REVIEW_SUBJECT_OPEN");
                return new Subject(c.getId(), blank(label) ? c.getTitle() : label.trim(), caseDoer(c));
            }
            default -> {
                ComplianceQuote q = quotes.findOne(tenantId, subjectId).filter(x -> x.getClientId().equals(clientId)).orElseThrow(() -> new ResourceNotFoundException("ComplianceQuote", subjectId.toString()));
                return new Subject(q.getId(), blank(label) ? "Quote " + q.getQuoteNumber() : label.trim(), q.getCreatedBy());
            }
        }
    }

    private static UUID caseDoer(ComplianceCase c) { return c.getResolvedBy() != null ? c.getResolvedBy() : c.getOwnerId() != null ? c.getOwnerId() : c.getCreatedBy(); }

    private static String assessmentLabel(ComplianceAssessment a) { return "Assessment as of " + a.getAsOf(); }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private void copyItems(TenantId tenantId, UUID reviewId, List<ComplianceReviewTemplateItem> checks) {
        for (ComplianceReviewTemplateItem c : checks) items.save(ComplianceReviewItem.copyOf(tenantId, reviewId, c.getPosition(), c.getText(), c.isCritical()));
    }

    private List<ComplianceReviewTemplateItem> saveItems(TenantId tenantId, UUID templateId, List<TemplateItemRequest> lines) {
        List<ComplianceReviewTemplateItem> saved = new ArrayList<>();
        int position = 1;
        for (TemplateItemRequest l : lines) saved.add(templateItems.save(ComplianceReviewTemplateItem.of(tenantId, templateId, position++, l.text(), l.critical())));
        return saved;
    }

    private void nameFree(TenantId tenantId, String name, UUID except) {
        templates.findByName(tenantId, name == null ? "" : name.trim()).filter(t -> !t.getId().equals(except)).ifPresent(t -> {
            throw new HandyFlowException("There is already a checklist with that name", HttpStatus.CONFLICT, "REVIEW_TEMPLATE_EXISTS");
        });
    }

    private ReviewDetail detail(TenantId tenantId, ComplianceReview r) {
        List<ComplianceReviewItem> lines = items.findForReview(tenantId, r.getId());
        Set<UUID> next = reviews.findPreviousIds(tenantId).stream().collect(Collectors.toSet());
        return new ReviewDetail(summary(r, nameOf(tenantId, r.getClientId()), lines, next.contains(r.getId())), r.getSubjectId(), r.getPreparedBy(), r.getReviewedBy(), r.getOutcomeNote(),
                r.getExceptionReason(), r.getPreviousReviewId(), lines.stream().map(i -> new ItemView(i.getPosition(), i.getText(), i.isCritical(), i.getResult(), i.getNote())).toList());
    }

    private List<ReviewSummary> summaries(TenantId tenantId, List<ComplianceReview> found) {
        Map<UUID, String> names = clients.findAll(tenantId, null, Pageable.unpaged()).getContent().stream().collect(Collectors.toMap(ComplianceClient::getId, ComplianceClient::getName));
        Map<UUID, List<ComplianceReviewItem>> byReview = items.findAllForTenant(tenantId).stream().collect(Collectors.groupingBy(ComplianceReviewItem::getReviewId));
        Set<UUID> next = new HashSet<>(reviews.findPreviousIds(tenantId));
        return found.stream().map(r -> summary(r, names.getOrDefault(r.getClientId(), "?"), byReview.getOrDefault(r.getId(), List.of()), next.contains(r.getId()))).toList();
    }

    private ReviewSummary summary(ComplianceReview r, String clientName, List<ComplianceReviewItem> lines, boolean hasNext) {
        int done = (int) lines.stream().filter(i -> !"PENDING".equals(i.getResult())).count();
        int failed = (int) lines.stream().filter(i -> "FAIL".equals(i.getResult())).count();
        return new ReviewSummary(r.getId(), r.getClientId(), clientName, r.getSubjectType(), r.getSubjectLabel(), r.getTemplateName(), r.getRound(), r.getStatus(), r.isSample(),
                r.getStartedAt(), r.getCompletedAt(), done, lines.size(), failed, QualityRules.actions(r.getStatus(), hasNext));
    }

    private String nameOf(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).map(ComplianceClient::getName).orElse("?");
    }

    private TemplateView view(ComplianceReviewTemplate t, List<ComplianceReviewTemplateItem> lines) {
        return new TemplateView(t.getId(), t.getName(), t.getSubjectType(), t.getDescription(), t.isActive(),
                lines.stream().sorted(Comparator.comparingInt(ComplianceReviewTemplateItem::getPosition)).map(i -> new TemplateLine(i.getPosition(), i.getText(), i.isCritical())).toList());
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceReviewTemplate template(TenantId tenantId, UUID id) {
        return templates.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceReviewTemplate", id.toString()));
    }

    private ComplianceReview review(TenantId tenantId, UUID id) {
        return reviews.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceReview", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "REVIEW_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "REVIEW_INVALID");
        }
    }
}
