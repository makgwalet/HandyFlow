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
import za.co.handyflow.platform.trainingprovider.domain.rules.AuditRules;
import za.co.handyflow.platform.trainingprovider.dto.AuditDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Internal quality audits. The provider writes its own checklists (saved as versions that never change) and runs audits against a site, a cohort,
 * a programme, or in general. Each audit keeps a copy of the questions it was run against. The auditor answers each item by their own judgement:
 * the system never calls anyone compliant or competent. Every issue needs a corrective action before the audit can be completed, and completing an
 * audit or verifying an action needs a second person or a written reason. Nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvAuditService {

    public static final String NOTE = "An audit records what the auditor saw, in their own judgement. It does not show that anyone or anything meets a standard.";

    private final TrainProvAuditTemplateRepository templates;
    private final TrainProvAuditTemplateVersionRepository templateVersions;
    private final TrainProvAuditRepository audits;
    private final TrainProvAuditItemRepository items;
    private final TrainProvAuditActionRepository actions;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvNumberGenerator numbers;

    // ---- checklists ----

    public List<TemplateView> templates(TenantId t) {
        return templates.allForTenant(t).stream().<TemplateView>map(x -> templateView(t, x)).toList();
    }

    @Transactional
    public TemplateView createTemplate(TenantId t, TemplateRequest r, UUID by) {
        String problem = AuditRules.templateProblem(r.name(), r.scopeType(), r.items());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_AUDIT_INVALID");
        }
        TrainProvAuditTemplate x = templates.save(TrainProvAuditTemplate.create(t, numbers.nextAuditTemplateNumber(t), r.name(), r.scopeType(), by));
        templateVersions.save(guard(() -> TrainProvAuditTemplateVersion.of(t, x.getId(), 1, r.items(), "First version", by)));
        return templateView(t, x);
    }

    /** A changed checklist is a new version. Audits already run keep the questions they were run against. */
    @Transactional
    public TemplateView newVersion(TenantId t, UUID templateId, VersionRequest r, UUID by) {
        TrainProvAuditTemplate x = template(t, templateId);
        if (!x.isActive()) {
            throw new HandyFlowException("This checklist is retired", HttpStatus.CONFLICT, "TP_AUDIT_STATE");
        }
        int next = templateVersions.latestNo(t, templateId) + 1;
        templateVersions.save(guard(() -> TrainProvAuditTemplateVersion.of(t, templateId, next, r.items(), r.changeNote(), by)));
        return templateView(t, x);
    }

    @Transactional
    public TemplateView retireTemplate(TenantId t, UUID templateId, String reason) {
        TrainProvAuditTemplate x = template(t, templateId);
        guard(() -> { x.retire(reason); return null; });
        templates.save(x);
        return templateView(t, x);
    }

    // ---- audits ----

    public List<AuditSummary> list(TenantId t, String status, String scope) {
        return audits.search(t, blank(status), blank(scope)).stream().<AuditSummary>map(a -> summary(t, a)).toList();
    }

    public AuditDetail get(TenantId t, UUID id, LocalDate today) {
        return detail(t, audit(t, id), today);
    }

    @Transactional
    public AuditDetail create(TenantId t, AuditRequest r, UUID by, LocalDate today) {
        TrainProvAuditTemplate x = template(t, r.templateId());
        if (!x.isActive()) {
            throw new HandyFlowException("That checklist is retired", HttpStatus.CONFLICT, "TP_AUDIT_STATE");
        }
        boolean hasTarget = r.siteId() != null || r.cohortId() != null || r.programmeId() != null;
        String problem = AuditRules.setupProblem(r.title(), x.getScopeType(), r.scopeType(), hasTarget, r.plannedOn());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "TP_AUDIT_INVALID");
        }
        checkTarget(t, r);
        TrainProvAuditTemplateVersion v = templateVersions.forTemplate(t, x.getId()).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("TrainProvAuditTemplateVersion", x.getId().toString()));
        TrainProvAudit a = audits.save(TrainProvAudit.create(t, numbers.nextAuditNumber(t), v.getId(), r.title(), r.scopeType(), r.siteId(), r.cohortId(), r.programmeId(), r.plannedOn(), by));
        int pos = 1;
        for (String q : AuditRules.parseItems(v.getItemsText())) {
            items.save(TrainProvAuditItem.of(t, a.getId(), pos++, q));
        }
        return detail(t, a, today);
    }

    @Transactional
    public AuditDetail start(TenantId t, UUID id, LocalDate today) {
        TrainProvAudit a = audit(t, id);
        guard(() -> { a.start(today); return null; });
        audits.save(a);
        return detail(t, a, today);
    }

    @Transactional
    public AuditDetail answer(TenantId t, UUID itemId, AnswerRequest r, UUID by, LocalDate today) {
        TrainProvAuditItem i = items.findForTenant(t, itemId).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuditItem", itemId.toString()));
        TrainProvAudit a = audit(t, i.getAuditId());
        guard(() -> { a.requireInProgress(); i.answer(r.outcome(), r.note(), by); return null; });
        items.save(i);
        return detail(t, a, today);
    }

    @Transactional
    public AuditDetail addAction(TenantId t, UUID auditId, ActionRequest r, UUID by, LocalDate today) {
        TrainProvAudit a = audit(t, auditId);
        if (AuditRules.CANCELLED.equals(a.getStatus()) || AuditRules.PLANNED.equals(a.getStatus())) {
            throw new HandyFlowException("Actions are added once the audit has started", HttpStatus.CONFLICT, "TP_AUDIT_STATE");
        }
        if (r.itemId() != null) {
            TrainProvAuditItem i = items.findForTenant(t, r.itemId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuditItem", r.itemId().toString()));
            if (!i.getAuditId().equals(auditId)) {
                throw new HandyFlowException("That item is not part of this audit", HttpStatus.CONFLICT, "TP_AUDIT_STATE");
            }
        }
        actions.save(guard(() -> TrainProvAuditAction.create(t, auditId, r.itemId(), r.description(), r.ownerName(), r.dueOn(), today, by)));
        return detail(t, a, today);
    }

    @Transactional
    public AuditDetail markDone(TenantId t, UUID actionId, String note, UUID by, LocalDate today) {
        TrainProvAuditAction x = action(t, actionId);
        guard(() -> { x.markDone(today, note, by); return null; });
        actions.save(x);
        return detail(t, audit(t, x.getAuditId()), today);
    }

    @Transactional
    public AuditDetail verify(TenantId t, UUID actionId, VerifyRequest r, UUID by, LocalDate today) {
        TrainProvAuditAction x = action(t, actionId);
        guard(() -> { x.verify(by, r.confirmAlone(), r.aloneReason()); return null; });
        actions.save(x);
        return detail(t, audit(t, x.getAuditId()), today);
    }

    @Transactional
    public AuditDetail cancelAction(TenantId t, UUID actionId, String reason, LocalDate today) {
        TrainProvAuditAction x = action(t, actionId);
        guard(() -> { x.cancel(reason); return null; });
        actions.save(x);
        return detail(t, audit(t, x.getAuditId()), today);
    }

    @Transactional
    public AuditDetail complete(TenantId t, UUID id, CompleteRequest r, UUID by, LocalDate today) {
        TrainProvAudit a = audit(t, id);
        guardRun(a::requireInProgress);
        List<TrainProvAuditItem> all = items.forAudit(t, id);
        List<TrainProvAuditAction> live = actions.forAudit(t, id).stream().filter(x -> !"CANCELLED".equals(x.getStatus())).toList();
        int unanswered = (int) all.stream().filter(i -> !i.answered()).count();
        int noAction = (int) all.stream().filter(i -> AuditRules.ISSUE.equals(i.getOutcome()) && live.stream().noneMatch(x -> i.getId().equals(x.getItemId()))).count();
        String problem = AuditRules.completeProblem(unanswered, noAction, r.summary());
        if (problem == null) {
            problem = AuditRules.secondPersonProblem(a.getLeadUserId(), by, r.confirmAlone(), r.aloneReason(), "complete this audit");
        }
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_AUDIT_STATE");
        }
        guard(() -> { a.complete(today, r.summary(), by, a.getLeadUserId().equals(by) ? r.aloneReason() : null); return null; });
        audits.save(a);
        return detail(t, a, today);
    }

    @Transactional
    public AuditDetail cancel(TenantId t, UUID id, String reason, LocalDate today) {
        TrainProvAudit a = audit(t, id);
        guard(() -> { a.cancel(reason); return null; });
        audits.save(a);
        return detail(t, a, today);
    }

    // ---- views and lookups ----

    private void checkTarget(TenantId t, AuditRequest r) {
        if (r.siteId() != null) {
            sites.findForTenant(t, r.siteId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", r.siteId().toString()));
        }
        if (r.cohortId() != null) {
            cohorts.findForTenant(t, r.cohortId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", r.cohortId().toString()));
        }
        if (r.programmeId() != null) {
            programmes.findForTenant(t, r.programmeId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", r.programmeId().toString()));
        }
    }

    private TemplateView templateView(TenantId t, TrainProvAuditTemplate x) {
        List<TrainProvAuditTemplateVersion> vs = templateVersions.forTemplate(t, x.getId());
        TrainProvAuditTemplateVersion latest = vs.isEmpty() ? null : vs.get(0);
        return new TemplateView(x.getId(), x.getTemplateNo(), x.getName(), x.getScopeType(), x.getStatus(), x.getRetiredReason(), latest == null ? 0 : latest.getVersionNo(),
                latest == null ? null : latest.getId(), latest == null ? List.of() : AuditRules.parseItems(latest.getItemsText()), vs.size());
    }

    private AuditSummary summary(TenantId t, TrainProvAudit a) {
        List<TrainProvAuditItem> all = items.forAudit(t, a.getId());
        int open = (int) actions.forAudit(t, a.getId()).stream().filter(x -> AuditRules.OPEN.equals(x.getStatus())).count();
        return new AuditSummary(a.getId(), a.getAuditNo(), a.getTitle(), a.getScopeType(), targetName(t, a), a.getPlannedOn(), a.getStatus(), all.size(),
                (int) all.stream().filter(TrainProvAuditItem::answered).count(), (int) all.stream().filter(i -> AuditRules.ISSUE.equals(i.getOutcome())).count(), open);
    }

    private AuditDetail detail(TenantId t, TrainProvAudit a, LocalDate today) {
        TrainProvAuditTemplateVersion v = templateVersions.findForTenant(t, a.getTemplateVersionId()).orElse(null);
        String templateName = v == null ? "" : templates.findForTenant(t, v.getTemplateId()).map(TrainProvAuditTemplate::getName).orElse("");
        List<ItemView> itemViews = items.forAudit(t, a.getId()).stream().<ItemView>map(i -> new ItemView(i.getId(), i.getPosition(), i.getQuestion(), i.getOutcome(), i.getNote(), i.getAnsweredAt())).toList();
        List<ActionView> actionViews = actions.forAudit(t, a.getId()).stream().<ActionView>map(x -> new ActionView(x.getId(), x.getItemId(), x.getDescription(), x.getOwnerName(), x.getDueOn(),
                x.getStatus(), AuditRules.overdue(x.getStatus(), x.getDueOn(), today), x.getDoneOn(), x.getDoneNote(), x.getVerifiedBy() != null,
                x.getVerifiedAloneReason() != null, x.getVerifiedAloneReason(), x.getCancelledReason())).toList();
        return new AuditDetail(a.getId(), a.getAuditNo(), a.getTitle(), a.getScopeType(), targetName(t, a), a.getLeadUserId(), a.getPlannedOn(), a.getStatus(), a.getStartedOn(),
                a.getCompletedOn(), a.getCompletedAloneReason() != null, a.getCompletedAloneReason(), a.getSummary(), a.getCancelledReason(), templateName,
                v == null ? 0 : templateVersions.findForTenant(t, v.getId()).map(TrainProvAuditTemplateVersion::getVersionNo).orElse(0), NOTE, itemViews, actionViews);
    }

    private String targetName(TenantId t, TrainProvAudit a) {
        if (a.getSiteId() != null) {
            return sites.findForTenant(t, a.getSiteId()).map(TrainProvDeliverySite::getName).orElse(null);
        }
        if (a.getCohortId() != null) {
            return cohorts.findForTenant(t, a.getCohortId()).map(TrainProvCohort::getName).orElse(null);
        }
        if (a.getProgrammeId() != null) {
            return programmes.findForTenant(t, a.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        }
        return null;
    }

    private TrainProvAuditTemplate template(TenantId t, UUID id) {
        return templates.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuditTemplate", id.toString()));
    }

    private TrainProvAudit audit(TenantId t, UUID id) {
        return audits.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAudit", id.toString()));
    }

    private TrainProvAuditAction action(TenantId t, UUID id) {
        return actions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuditAction", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
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
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_AUDIT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_AUDIT_INVALID");
        }
    }
}
