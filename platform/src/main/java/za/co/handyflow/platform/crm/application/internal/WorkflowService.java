package za.co.handyflow.platform.crm.application.internal;

import za.co.handyflow.platform.crm.domain.model.AuditAction;
import za.co.handyflow.platform.crm.domain.model.AuditEntity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.Workflow;
import za.co.handyflow.platform.crm.domain.model.WorkflowAction;
import za.co.handyflow.platform.crm.domain.model.WorkflowRun;
import za.co.handyflow.platform.crm.domain.model.WorkflowTrigger;
import za.co.handyflow.platform.crm.domain.repository.CustomerFollowUpRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.WorkflowRepository;
import za.co.handyflow.platform.crm.domain.repository.WorkflowRunRepository;
import za.co.handyflow.platform.crm.dto.WorkflowRequest;
import za.co.handyflow.platform.crm.dto.WorkflowResponse;
import za.co.handyflow.platform.crm.dto.WorkflowRunResponse;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

/**
 * Workflow rules, and running them when something happens to a lead. Actions change the lead directly and never start another workflow,
 * so workflows cannot trigger each other in a loop. One failing workflow never undoes the change that started it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private final WorkflowRepository workflowRepository;
    private final WorkflowRunRepository runRepository;
    private final CustomerRepository customerRepository;
    private final CustomerFollowUpRepository followUpRepository;
    private final UserRecipientResolver userResolver;
    private final TenantAdminRecipients tenantAdminRecipients;
    private final NotificationService notificationService;
    private final CrmAuditService audit;

    @Transactional(readOnly = true)
    public List<WorkflowResponse> list(TenantId tenantId) {
        return workflowRepository.findAllActive(tenantId).stream().map(WorkflowService::toResponse).toList();
    }

    @Transactional
    public WorkflowResponse create(TenantId tenantId, WorkflowRequest req) {
        validate(tenantId, req);
        if (workflowRepository.findAllActive(tenantId).size() >= WorkflowRules.MAX_WORKFLOWS) throw new IllegalArgumentException("At most " + WorkflowRules.MAX_WORKFLOWS + " workflows");
        var w = Workflow.create(tenantId, req.name(), req.trigger(), req.triggerStage(), req.condField(), req.condOp(), req.condValue(), req.action(), req.actionText(), req.actionDays(), req.actionUser(), req.active() == null || req.active());
        var saved = workflowRepository.save(w);
        audit.record(tenantId, AuditEntity.WORKFLOW, saved.getId(), saved.getName(), AuditAction.CREATED, WorkflowRules.describe(toRule(saved), null));
        return toResponse(saved);
    }

    @Transactional
    public WorkflowResponse update(TenantId tenantId, UUID id, WorkflowRequest req) {
        var w = require(tenantId, id);
        validate(tenantId, req);
        w.change(req.name(), req.trigger(), req.triggerStage(), req.condField(), req.condOp(), req.condValue(), req.action(), req.actionText(), req.actionDays(), req.actionUser(), req.active() == null || req.active());
        audit.record(tenantId, AuditEntity.WORKFLOW, id, w.getName(), AuditAction.UPDATED, w.isActive() ? null : "Turned off");
        return toResponse(workflowRepository.save(w));
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        var w = require(tenantId, id);
        w.softDelete();
        workflowRepository.save(w);
        audit.record(tenantId, AuditEntity.WORKFLOW, id, w.getName(), AuditAction.DELETED, null);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunResponse> recentRuns(TenantId tenantId, int limit) {
        var runs = runRepository.findRecent(tenantId, PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
        Map<UUID, String> workflowNames = new HashMap<>();
        workflowRepository.findAllActive(tenantId).forEach(w -> workflowNames.put(w.getId(), w.getName()));
        Map<UUID, String> customerNames = new HashMap<>();
        var ids = runs.stream().map(WorkflowRun::getCustomerId).distinct().toList();
        if (!ids.isEmpty()) customerRepository.findActiveByIds(tenantId, ids).forEach(c -> customerNames.put(c.getId(), c.getName()));
        return runs.stream().map(r -> new WorkflowRunResponse(r.getId(), r.getWorkflowId(), workflowNames.getOrDefault(r.getWorkflowId(), "Deleted workflow"), r.getCustomerId(),
                customerNames.getOrDefault(r.getCustomerId(), "Deleted customer"), r.getOutcome(), r.getDetail(), r.getRanAt())).toList();
    }

    /** Runs every active workflow that fits this event. Never throws. */
    @Transactional
    public void fire(TenantId tenantId, WorkflowTrigger event, Customer lead, LeadStage stageMovedTo) {
        try {
            var facts = LeadFacts.of(lead);
            int ran = 0;
            for (Workflow w : workflowRepository.findActiveFor(tenantId, event)) {
                if (ran >= WorkflowRules.MAX_PER_EVENT) break;
                if (!WorkflowRules.applies(toRule(w), event, stageMovedTo, facts)) continue;
                ran++;
                runOne(tenantId, w, lead);
            }
        } catch (Exception e) {
            log.warn("[CRM] Workflows for {} on lead={} tenant={} could not run: {}", event, lead.getId(), tenantId, e.getMessage());
        }
    }

    private void runOne(TenantId tenantId, Workflow w, Customer lead) {
        String detail;
        boolean done = true;
        try {
            detail = perform(tenantId, w.getAction(), w.getActionText(), w.getActionDays(), w.getActionUser(), lead);
        } catch (Exception e) {
            done = false;
            detail = e.getMessage();
            log.warn("[CRM] Workflow {} failed for lead={} tenant={}: {}", w.getId(), lead.getId(), tenantId, e.getMessage());
        }
        runRepository.save(WorkflowRun.of(tenantId, w.getId(), lead.getId(), done, detail));
    }

    /**
     * Does one action to a lead and says what it did; throws with a readable message when it cannot. Shared by workflows and journeys.
     * The caller decides what a failure means.
     */
    String perform(TenantId tenantId, WorkflowAction action, String text, Integer days, UUID user, Customer lead) {
        return switch (action) {
            case CREATE_TASK -> {
                UUID who = user != null ? user : lead.getOwnerId();
                LocalDate due = WorkflowRules.dueDate(LocalDate.now(ZONE), days == null ? 0 : days);
                followUpRepository.save(CustomerFollowUp.create(tenantId, lead.getId(), due, text, who, null));
                yield "Task added, due " + due;
            }
            case ADD_TAG -> { lead.addTag(text, null); customerRepository.save(lead); yield "Tagged " + text.toLowerCase(); }
            case ASSIGN_OWNER -> {
                if (userResolver.resolveUser(tenantId, user).isEmpty()) throw new IllegalStateException("The new owner is no longer a user of this account");
                lead.assignOwner(user, null);
                customerRepository.save(lead);
                yield "Owner changed";
            }
            case NOTIFY_OWNER -> notifyOwner(tenantId, text, lead);
        };
    }

    private String notifyOwner(TenantId tenantId, String text, Customer lead) {
        List<Recipient> to = lead.getOwnerId() == null ? List.of() : userResolver.resolveUser(tenantId, lead.getOwnerId()).map(List::of).orElse(List.of());
        if (to.isEmpty()) to = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (to.isEmpty()) throw new IllegalStateException("Nobody could be found to tell");
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId).type(NotificationType.CRM_WORKFLOW_MESSAGE).title(lead.getName()).message(text)
                .actionUrl("/crm").sourceModule("crm").sourceEntityId(lead.getId().toString()).recipients(to).build());
        return "Message sent";
    }

    private void validate(TenantId tenantId, WorkflowRequest req) {
        WorkflowRules.check(req.name(), new WorkflowRules.Workflow(req.trigger(), req.triggerStage(), cond(req), req.action(), req.actionText(), req.actionDays(), req.actionUser()));
        if (req.actionUser() != null && (req.action() == WorkflowAction.ASSIGN_OWNER || req.action() == WorkflowAction.CREATE_TASK)
                && userResolver.resolveUser(tenantId, req.actionUser()).isEmpty()) {
            throw new IllegalArgumentException("That person is not a user of this account");
        }
    }

    private static Condition cond(WorkflowRequest r) {
        return r.condField() == null && r.condOp() == null && (r.condValue() == null || r.condValue().isBlank()) ? null : new Condition(r.condField(), r.condOp(), r.condValue());
    }

    private static WorkflowRules.Workflow toRule(Workflow w) {
        return new WorkflowRules.Workflow(w.getTrigger(), w.getTriggerStage(), w.getCondField() == null ? null : new Condition(w.getCondField(), w.getCondOp(), w.getCondValue()),
                w.getAction(), w.getActionText(), w.getActionDays(), w.getActionUser());
    }

    private Workflow require(TenantId tenantId, UUID id) {
        return workflowRepository.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Workflow", id.toString()));
    }

    static WorkflowResponse toResponse(Workflow w) {
        return new WorkflowResponse(w.getId(), w.getName(), w.getTrigger(), w.getTriggerStage(), w.getCondField(), w.getCondOp(), w.getCondValue(), w.getAction(),
                w.getActionText(), w.getActionDays(), w.getActionUser(), w.isActive(), WorkflowRules.describe(toRule(w), null));
    }
}
