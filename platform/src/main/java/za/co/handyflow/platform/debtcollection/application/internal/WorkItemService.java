package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.PriorityBand;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemDisposition;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemEvent;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemStatus;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.WorkItemEventRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.WorkItemRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the work-item lifecycle and the "My Work" queue. Every state change
 * writes an append-only WorkItemEvent. Raising is idempotent on sourceKey, so
 * schedulers and (later) PTP/communication hooks can call raise() freely.
 * <p>
 * Dependency note: this service reads cases via DebtCollectionCaseService and
 * is NOT called back by it (no cycle). Closed/deleted cases have their live
 * items cancelled by WorkItemScheduler's daily sweep rather than by hooking
 * DebtCollectionCaseService.close(), which keeps Chunk 1 purely additive.
 */
@Service
@RequiredArgsConstructor
public class WorkItemService {

    /** A work item joined with the case fields the queue needs to display. */
    public record WorkItemView(WorkItem item, DebtCollectionCase debtCase) {}

    private final WorkItemRepository repository;
    private final WorkItemEventRepository eventRepository;
    private final DebtCollectionCaseRepository caseRepository;
    private final DebtCollectionCaseService caseService;
    private final WorkItemPriorityCalculator calculator;

    /** Idempotent. Returns the existing item (any status) if sourceKey was already raised. Auto-assigns to the case's collector. */
    @Transactional
    public WorkItem raise(TenantId tenantId, UUID caseId, WorkItemType type, String title, LocalDate dueDate,
                          String sourceKey, UUID actorId, String actorName) {
        if (sourceKey != null) {
            var existing = repository.findBySourceKey(tenantId, sourceKey);
            if (existing.isPresent()) return existing.get();
        }
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        if (c.getStatus() == CaseStatus.CLOSED) {
            throw new IllegalStateException("Cannot raise work on a closed case");
        }
        WorkItem w = WorkItem.raise(tenantId, caseId, type, title, dueDate, c.getAssignedToUserId(),
                c.getAssignedToUserName(), sourceKey, actorId);
        applyScore(w, c);
        w = repository.save(w);
        audit(w, "RAISED", type + " — " + w.getPriorityReasons(), actorId, actorName);
        return w;
    }

    @Transactional
    public WorkItem raiseManual(TenantId tenantId, UUID caseId, String title, LocalDate dueDate, UUID assigneeId,
                                String assigneeName, UUID actorId, String actorName) {
        WorkItem w = raise(tenantId, caseId, WorkItemType.MANUAL, title, dueDate, null, actorId, actorName);
        if (assigneeId != null) {
            w.assign(assigneeId, assigneeName);
            repository.save(w);
        }
        return w;
    }

    @Transactional
    public WorkItem assign(TenantId tenantId, UUID id, UUID userId, String userName, String reason,
                           UUID actorId, String actorName) {
        WorkItem w = find(tenantId, id);
        String from = w.getAssignedToUserName();
        w.assign(userId, userName);
        audit(w, "ASSIGNED", (from != null ? from : "unassigned") + " → " + userName
                + (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""), actorId, actorName);
        return repository.save(w);
    }

    @Transactional
    public WorkItem snooze(TenantId tenantId, UUID id, LocalDate until, String reason, UUID actorId, String actorName) {
        WorkItem w = find(tenantId, id);
        w.snooze(until, reason);
        audit(w, "SNOOZED", "until " + until + ": " + reason, actorId, actorName);
        return repository.save(w);
    }

    @Transactional
    public WorkItem wake(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        WorkItem w = find(tenantId, id);
        w.wake();
        audit(w, "WOKEN", "manually woken", actorId, actorName);
        return repository.save(w);
    }

    @Transactional
    public WorkItem complete(TenantId tenantId, UUID id, WorkItemDisposition disposition, String notes,
                             UUID actorId, String actorName) {
        WorkItem w = find(tenantId, id);
        w.complete(disposition, notes, actorId);
        audit(w, "COMPLETED", disposition + (notes != null && !notes.isBlank() ? " — " + notes : ""), actorId, actorName);
        return repository.save(w);
    }

    @Transactional
    public WorkItem overridePriority(TenantId tenantId, UUID id, Integer priority, String reason,
                                     UUID actorId, String actorName) {
        WorkItem w = find(tenantId, id);
        Integer before = w.getPriorityOverride();
        w.overridePriority(priority, reason);
        audit(w, "PRIORITY_OVERRIDE", (before != null ? before : "none") + " → " + (priority != null ? priority : "cleared")
                + ": " + reason, actorId, actorName);
        return repository.save(w);
    }

    /** Cancels a live item raised by a trigger that has since resolved (e.g. a broken PTP later recovered). No-op if absent or already closed. */
    @Transactional
    public void cancelBySourceKey(TenantId tenantId, String sourceKey, String reason) {
        repository.findBySourceKey(tenantId, sourceKey).ifPresent(w -> {
            if (!w.isLive()) return;
            w.cancel(reason);
            audit(w, "CANCELLED", reason, null, "system");
            repository.save(w);
        });
    }

    @Transactional(readOnly = true)
    public Page<WorkItemView> myQueue(TenantId tenantId, UUID userId, Pageable pageable) {
        Page<WorkItem> page = repository.findMyQueue(tenantId, userId, pageable);
        Map<UUID, DebtCollectionCase> cases = casesFor(page.getContent());
        return page.map(w -> new WorkItemView(w, cases.get(w.getCaseId())));
    }

    @Transactional(readOnly = true)
    public Page<WorkItemView> teamQueue(TenantId tenantId, WorkItemStatus status, UUID assigneeId, Pageable pageable) {
        Page<WorkItem> page = repository.findTeamQueue(tenantId, status, assigneeId, pageable);
        Map<UUID, DebtCollectionCase> cases = casesFor(page.getContent());
        return page.map(w -> new WorkItemView(w, cases.get(w.getCaseId())));
    }

    /** Band counts for the queue header chips. Loads the user's open items (small per collector) rather than a GROUP BY on the override/score COALESCE. */
    @Transactional(readOnly = true)
    public Map<PriorityBand, Long> summaryFor(TenantId tenantId, UUID userId) {
        Map<PriorityBand, Long> out = new EnumMap<>(PriorityBand.class);
        for (PriorityBand b : PriorityBand.values()) out.put(b, 0L);
        repository.findOpenForUser(tenantId, userId)
                .forEach(w -> out.merge(PriorityBand.of(w.effectivePriority()), 1L, Long::sum));
        return out;
    }

    @Transactional(readOnly = true)
    public List<WorkItemEvent> history(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return eventRepository.findByWorkItemId(tenantId, id);
    }

    /** Joins one item with its case for single-item responses. */
    @Transactional(readOnly = true)
    public WorkItemView view(TenantId tenantId, WorkItem w) {
        return new WorkItemView(w, caseRepository.findActiveById(tenantId, w.getCaseId()).orElse(null));
    }

    /** Collectors may only act on items assigned to them; admins (supervisors) on any. */
    @Transactional(readOnly = true)
    public void assertOwnerOrAdmin(TenantId tenantId, UUID id, UUID userId, boolean admin) {
        if (admin) return;
        WorkItem w = find(tenantId, id);
        if (w.getAssignedToUserId() == null || !w.getAssignedToUserId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This work item is assigned to someone else — ask a supervisor to reassign it");
        }
    }

    // ── Scheduler-facing (system actor: null user) ───────────────────────────

    /** Recalculate score for one live item; cancel it if its case is closed or gone. */
    @Transactional
    public void refreshLive(UUID workItemId, LocalDate today) {
        repository.findById(workItemId).ifPresent(w -> {
            if (!w.isLive()) return;
            DebtCollectionCase c = caseRepository.findActiveById(w.getTenantId(), w.getCaseId()).orElse(null);
            if (c == null || c.getStatus() == CaseStatus.CLOSED) {
                w.cancel("Case closed or removed");
                audit(w, "CANCELLED", "case closed or removed", null, "system");
            } else {
                int before = w.getPriorityScore();
                applyScore(w, c, today);
                if (before != w.getPriorityScore()) {
                    audit(w, "RESCORED", before + " → " + w.getPriorityScore(), null, "system");
                }
            }
            repository.save(w);
        });
    }

    @Transactional
    public void wakeExpired(UUID workItemId) {
        repository.findById(workItemId).ifPresent(w -> {
            if (w.getStatus() == WorkItemStatus.SNOOZED) {
                w.wake();
                audit(w, "WOKEN", "snooze expired", null, "system");
                repository.save(w);
            }
        });
    }

    // ── internals ────────────────────────────────────────────────────────────

    private WorkItem find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkItem", id.toString()));
    }

    private void applyScore(WorkItem w, DebtCollectionCase c) {
        applyScore(w, c, LocalDate.now());
    }

    private void applyScore(WorkItem w, DebtCollectionCase c, LocalDate today) {
        var r = calculator.score(w, c, today);
        w.rescore(r.score(), r.reasonText());
    }

    private void audit(WorkItem w, String type, String detail, UUID actorId, String actorName) {
        eventRepository.save(WorkItemEvent.of(w.getTenantId(), w.getId(), w.getCaseId(), type, detail, actorId, actorName));
    }

    private Map<UUID, DebtCollectionCase> casesFor(List<WorkItem> items) {
        List<UUID> ids = items.stream().map(WorkItem::getCaseId).distinct().toList();
        return caseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DebtCollectionCase::getId, Function.identity()));
    }
}
