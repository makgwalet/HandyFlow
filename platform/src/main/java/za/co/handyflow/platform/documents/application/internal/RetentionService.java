package za.co.handyflow.platform.documents.application.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.documents.application.RetentionFacade;
import za.co.handyflow.platform.documents.application.RetentionProvider;
import za.co.handyflow.platform.documents.application.RetentionViews;
import za.co.handyflow.platform.documents.domain.model.DisposalBatch;
import za.co.handyflow.platform.documents.domain.model.DisposalBatchItem;
import za.co.handyflow.platform.documents.domain.model.RetentionItem;
import za.co.handyflow.platform.documents.domain.model.RetentionPolicy;
import za.co.handyflow.platform.documents.domain.model.RetentionRules;
import za.co.handyflow.platform.documents.domain.repository.DisposalBatchItemRepository;
import za.co.handyflow.platform.documents.domain.repository.DisposalBatchRepository;
import za.co.handyflow.platform.documents.domain.repository.RetentionItemRepository;
import za.co.handyflow.platform.documents.domain.repository.RetentionPolicyRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records retention: policies, legal holds, and disposal in two steps by two people. Nothing is ever deleted automatically.
 * <p>
 * Every public method runs its own transactions. A refusal that matters (approving your own proposal, a support session trying to
 * dispose) is written to the audit trail in a transaction of its own before it is thrown, so the attempt is on record. When a
 * disposal is approved, each record is disposed in its own transaction: one that fails, or that was put on hold or stopped being
 * due since it was proposed, goes back to the list and the rest carry on.
 */
@Service
public class RetentionService implements RetentionFacade {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    static final int MAX_LIST = 500;

    private final RetentionPolicyRepository policies;
    private final RetentionItemRepository items;
    private final DisposalBatchRepository batches;
    private final DisposalBatchItemRepository batchItems;
    private final AuditTrailFacade trail;
    private final ObjectProvider<RetentionProvider> providers;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    Clock clock = Clock.system(ZONE);

    RetentionService(RetentionPolicyRepository policies, RetentionItemRepository items, DisposalBatchRepository batches,
                     DisposalBatchItemRepository batchItems, AuditTrailFacade trail, ObjectProvider<RetentionProvider> providers,
                     PlatformTransactionManager txManager) {
        this.policies = policies;
        this.items = items;
        this.batches = batches;
        this.batchItems = batchItems;
        this.trail = trail;
        this.providers = providers;
        this.tx = new TransactionTemplate(txManager);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    LocalDate today() { return LocalDate.now(clock); }

    // ── For modules (joins the caller's transaction) ─────────────────────────

    @Override
    public void track(TenantId tenantId, String moduleCode, String sourceType, UUID sourceId, String category,
                      String reference, String title, String contextLabel) {
        String module = RetentionRules.code(moduleCode, 40, "module");
        String type = RetentionRules.code(sourceType, 40, "source type");
        RetentionItem i = items.findBySource(tenantId.getValue(), module, type, sourceId).orElse(null);
        if (i == null) i = RetentionItem.track(tenantId.getValue(), module, type, sourceId, category, RetentionRules.DEFAULT_JURISDICTION);
        else if (i.isDisposed()) return;
        i.describe(reference, title, contextLabel);
        items.save(i);
    }

    @Override
    public int startClock(TenantId tenantId, String moduleCode, String sourceType, Collection<UUID> sourceIds, LocalDate start) {
        if (sourceIds == null || sourceIds.isEmpty() || start == null) return 0;
        String module = RetentionRules.code(moduleCode, 40, "module");
        String type = RetentionRules.code(sourceType, 40, "source type");
        List<RetentionPolicy> rules = policies.findForTenant(tenantId.getValue());
        int n = 0;
        for (RetentionItem i : items.findBySources(tenantId.getValue(), module, type, sourceIds)) {
            if (i.isDisposed()) continue;
            int years = RetentionRules.resolve(rules, i.getModuleCode(), i.getCategory(), i.getJurisdiction()).years();
            if (i.startClock(start, years)) { items.save(i); n++; }
        }
        return n;
    }

    @Override
    public void moduleHoldChanged(TenantId tenantId, String moduleCode, String sourceType, UUID sourceId, boolean hold, String reason,
                                  UUID by, String byName) {
        RetentionItem i = items.findBySource(tenantId.getValue(), RetentionRules.code(moduleCode, 40, "module"),
                RetentionRules.code(sourceType, 40, "source type"), sourceId).orElse(null);
        if (i == null || i.isDisposed() || i.isLegalHold() == hold) return;
        applyHold(tenantId, i, hold, reason, by, byName);
        items.save(i);
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    public RetentionViews.Summary summary(TenantId tenantId) {
        UUID t = tenantId.getValue();
        LocalDate today = today();
        Map<String, Long> byStatus = new HashMap<>();
        for (Object[] r : items.countByStatus(t)) byStatus.put(String.valueOf(r[0]), ((Number) r[1]).longValue());
        long tracked = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long unconfirmed = policyRows(t).stream().filter(p -> !p.confirmed() && p.recordsKept() > 0).count();
        return new RetentionViews.Summary(tracked, items.countClockNotStarted(t), items.countDue(t, today),
                items.countUpcoming(t, today, today.plusDays(RetentionRules.UPCOMING_DAYS)), items.countHeld(t),
                byStatus.getOrDefault(RetentionItem.PROPOSED, 0L), byStatus.getOrDefault(RetentionItem.DISPOSED, 0L),
                batches.countOpen(t), unconfirmed, RetentionRules.UPCOMING_DAYS);
    }

    /** {@code view}: DUE (default), UPCOMING, HELD, DISPOSED or ALL (everything still kept). */
    public List<RetentionViews.Item> items(TenantId tenantId, String view, String moduleCode, Integer limit) {
        UUID t = tenantId.getValue();
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit == null ? 200 : limit, MAX_LIST)));
        LocalDate today = today();
        String v = view == null ? "DUE" : view.trim().toUpperCase(Locale.ROOT);
        List<RetentionItem> found = switch (v) {
            case "DUE" -> items.findDue(t, today, page);
            case "UPCOMING" -> items.findUpcoming(t, today, today.plusDays(RetentionRules.UPCOMING_DAYS), page);
            case "HELD" -> items.findHeld(t, page);
            case "DISPOSED" -> items.findDisposed(t, page);
            case "ALL" -> moduleCode == null || moduleCode.isBlank() ? items.findKept(t, page)
                    : items.findKeptForModule(t, RetentionRules.code(moduleCode, 40, "module"), page);
            default -> throw new IllegalArgumentException("Unknown list: use DUE, UPCOMING, HELD, DISPOSED or ALL.");
        };
        if (moduleCode != null && !moduleCode.isBlank() && !v.equals("ALL")) {
            String m = RetentionRules.code(moduleCode, 40, "module");
            found = found.stream().filter(i -> i.getModuleCode().equals(m)).toList();
        }
        return views(t, found);
    }

    public RetentionViews.Item item(TenantId tenantId, UUID itemId) {
        return views(tenantId.getValue(), List.of(owned(tenantId, itemId))).get(0);
    }

    /** Every policy row, plus each category in use that has no row (shown as the 7-year default). */
    public List<RetentionViews.Policy> policies(TenantId tenantId) { return policyRows(tenantId.getValue()); }

    public List<RetentionViews.Batch> batches(TenantId tenantId, Integer limit) {
        return batches.findForTenant(tenantId.getValue(), PageRequest.of(0, Math.max(1, Math.min(limit == null ? 50 : limit, 200))))
                .stream().map(b -> batchView(b, null)).toList();
    }

    public RetentionViews.Batch batch(TenantId tenantId, UUID batchId) {
        DisposalBatch b = batches.findOwned(tenantId.getValue(), batchId).orElseThrow(() -> new ResourceNotFoundException("Disposal request", String.valueOf(batchId)));
        return batchView(b, batchItems.findForBatch(b.getId()));
    }

    // ── Legal hold ───────────────────────────────────────────────────────────

    public RetentionViews.Item setHold(TenantId tenantId, UUID itemId, RetentionViews.HoldRequest req, UUID by, String byName) {
        if (req == null || req.hold() == null) throw new IllegalArgumentException("Say whether to place or lift the hold.");
        boolean hold = req.hold();
        String reason = RetentionRules.reason(req.reason(), hold ? "place a legal hold" : "lift a legal hold");
        RetentionItem saved = tx.execute(s -> {
            RetentionItem i = items.findOwnedForUpdate(tenantId.getValue(), itemId).orElseThrow(() -> notFound(itemId));
            if (i.isDisposed()) throw conflict("This record was disposed of; there is nothing left to hold.", "DISPOSED");
            if (i.isLegalHold() == hold) return i;
            applyHold(tenantId, i, hold, reason, by, byName);
            RetentionItem out = items.save(i);
            provider(i.getModuleCode()).ifPresent(p -> p.legalHoldChanged(tenantId, i.getSourceType(), i.getSourceId(), hold, reason, by, byName));
            return out;
        });
        return item(tenantId, saved.getId());
    }

    private void applyHold(TenantId tenantId, RetentionItem i, boolean hold, String reason, UUID by, String byName) {
        Map<String, Object> before = itemSummary(i);
        if (hold) i.hold(reason, by, byName); else i.release();
        Map<String, Object> after = itemSummary(i);
        after.put("reason", RetentionRules.optional(reason, 500) == null ? "" : reason);
        trail.record(tenantId, hold ? "LEGAL_HOLD_PLACED" : "LEGAL_HOLD_LIFTED", "RETENTION_ITEM", i.getId(), before, after);
    }

    // ── Policies ─────────────────────────────────────────────────────────────

    public RetentionViews.Policy savePolicy(TenantId tenantId, RetentionViews.PolicyRequest req, UUID by, String byName) {
        if (req == null) throw new IllegalArgumentException("Send the policy.");
        String module = RetentionRules.code(req.moduleCode(), 40, "module");
        String category = RetentionRules.code(req.category(), 60, "category");
        String jurisdiction = RetentionRules.jurisdiction(req.jurisdiction());
        int years = RetentionRules.years(req.retainYears());
        int[] moved = new int[1];
        RetentionPolicy saved = tx.execute(s -> {
            RetentionPolicy p = policies.findOne(tenantId.getValue(), module, category, jurisdiction).orElse(null);
            Map<String, Object> before = p == null ? null : policySummary(p);
            if (p == null) p = RetentionPolicy.create(tenantId.getValue(), module, category, jurisdiction);
            p.set(years, Boolean.TRUE.equals(req.personalInfo()), Boolean.TRUE.equals(req.confirmed()), req.notes(), by, byName);
            RetentionPolicy out = policies.save(p);
            moved[0] = reapply(tenantId, module);
            Map<String, Object> after = policySummary(out);
            after.put("recordsRedated", moved[0]);
            trail.record(tenantId, "RETENTION_POLICY_SAVED", "RETENTION_POLICY", out.getId(), before, after);
            return out;
        });
        log.info("Retention policy {} {}/{}/{} = {} years; {} record(s) re-dated", saved.getId(), module, category, jurisdiction, years, moved[0]);
        return policyRows(tenantId.getValue()).stream().filter(p -> saved.getId().equals(p.id())).findFirst().orElseThrow();
    }

    /** Removes a policy row: its records fall back to the module's '*' policy or the 7-year default, and are re-dated. */
    public void removePolicy(TenantId tenantId, UUID policyId, UUID by, String byName) {
        tx.executeWithoutResult(s -> {
            RetentionPolicy p = policies.findOwned(tenantId.getValue(), policyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Retention policy", String.valueOf(policyId)));
            Map<String, Object> before = policySummary(p);
            policies.delete(p);
            policies.flush();
            int moved = reapply(tenantId, p.getModuleCode());
            trail.record(tenantId, "RETENTION_POLICY_REMOVED", "RETENTION_POLICY", p.getId(), before, Map.of("recordsRedated", moved, "by", String.valueOf(byName)));
        });
    }

    /** Re-dates every clocked, not-disposed record of the module after a policy change. */
    private int reapply(TenantId tenantId, String module) {
        List<RetentionPolicy> rules = policies.findForTenant(tenantId.getValue());
        int n = 0;
        for (RetentionItem i : items.findClockedForModule(tenantId.getValue(), module)) {
            if (i.applyPeriod(RetentionRules.resolve(rules, i.getModuleCode(), i.getCategory(), i.getJurisdiction()).years())) {
                items.save(i);
                n++;
            }
        }
        return n;
    }

    // ── Disposal: step one, propose ──────────────────────────────────────────

    public RetentionViews.Batch propose(TenantId tenantId, RetentionViews.ProposeRequest req, UUID by, String byName, boolean supportSession) {
        if (supportSession) throw refused(tenantId, null, "A support session cannot propose a disposal.", "SUPPORT_SESSION", by, byName);
        if (by == null) throw new HandyFlowException("Sign in to propose a disposal.", HttpStatus.FORBIDDEN, "SIGN_IN");
        if (req == null || req.itemIds() == null || req.itemIds().isEmpty()) throw new IllegalArgumentException("Choose the records to dispose of.");
        Set<UUID> ids = new LinkedHashSet<>(req.itemIds());
        if (ids.size() > RetentionRules.MAX_BATCH)
            throw new IllegalArgumentException("Propose at most " + RetentionRules.MAX_BATCH + " records at a time.");
        String reason = RetentionRules.reason(req.reason(), "propose a disposal");
        LocalDate today = today();
        DisposalBatch saved = tx.execute(s -> {
            List<RetentionItem> locked = new ArrayList<>();
            List<String> notDue = new ArrayList<>();
            for (UUID id : ids) {
                RetentionItem i = items.findOwnedForUpdate(tenantId.getValue(), id).orElseThrow(() -> notFound(id));
                if (!i.isDue(today)) notDue.add(label(i) + " (" + whyNotDue(i, today) + ")");
                locked.add(i);
            }
            if (!notDue.isEmpty())
                throw conflict("Only records that are due can be proposed. Not due: " + String.join("; ", notDue.subList(0, Math.min(5, notDue.size())))
                        + (notDue.size() > 5 ? " and " + (notDue.size() - 5) + " more" : ""), "NOT_DUE");
            DisposalBatch b = batches.save(DisposalBatch.propose(tenantId.getValue(), reason, locked.size(), by, byName));
            for (RetentionItem i : locked) {
                i.propose(b.getId());
                items.save(i);
                batchItems.save(DisposalBatchItem.of(b.getId(), i.getId()));
            }
            trail.record(tenantId, "DISPOSAL_PROPOSED", "DISPOSAL_BATCH", b.getId(), null,
                    Map.of("records", locked.size(), "reason", reason, "proposedBy", String.valueOf(byName)));
            return b;
        });
        log.info("Disposal {} proposed: {} record(s) by {}", saved.getId(), saved.getItemCount(), by);
        return batch(tenantId, saved.getId());
    }

    // ── Disposal: step two, decide ───────────────────────────────────────────

    /**
     * Approves a proposal made by someone else and disposes of its records, or carries on with one that was approved but did not
     * finish. The proposer can never approve; neither can a support session.
     */
    public RetentionViews.Batch approve(TenantId tenantId, UUID batchId, RetentionViews.DecisionRequest req, UUID by, String byName, boolean supportSession) {
        if (supportSession) throw refused(tenantId, batchId, "A support session cannot approve a disposal.", "SUPPORT_SESSION", by, byName);
        String note = req == null ? null : RetentionRules.optional(req.note(), 500);
        DisposalBatch decided = batches.findOwned(tenantId.getValue(), batchId).orElseThrow(() -> new ResourceNotFoundException("Disposal request", String.valueOf(batchId)));
        if (RetentionRules.sameOrUnknownPerson(decided.getProposedBy(), by))
            throw refused(tenantId, batchId, "You proposed this disposal, so someone else must approve it.", "SAME_PERSON", by, byName);
        tx.executeWithoutResult(s -> {
            DisposalBatch b = batches.findOwnedForUpdate(tenantId.getValue(), batchId).orElseThrow();
            if (DisposalBatch.PROPOSED.equals(b.getStatus())) {
                b.approve(by, byName, note);
                batches.save(b);
                trail.record(tenantId, "DISPOSAL_APPROVED", "DISPOSAL_BATCH", b.getId(), Map.of("status", DisposalBatch.PROPOSED),
                        Map.of("status", DisposalBatch.APPROVED, "approvedBy", String.valueOf(byName), "proposedBy", String.valueOf(b.getProposedByName()),
                                "note", note == null ? "" : note));
            } else if (!DisposalBatch.APPROVED.equals(b.getStatus())) {
                throw conflict("This disposal request is " + b.getStatus().toLowerCase(Locale.ROOT) + " and cannot be approved.", "WRONG_STATE");
            }
        });
        int disposed = 0, skipped = 0, failed = 0;
        for (DisposalBatchItem bi : batchItems.findForBatch(batchId)) {
            if (!bi.isPending()) continue;
            String outcome = disposeOne(tenantId, batchId, bi.getItemId(), by);
            switch (outcome) {
                case DisposalBatchItem.DISPOSED -> disposed++;
                case DisposalBatchItem.SKIPPED -> skipped++;
                default -> failed++;
            }
        }
        tx.executeWithoutResult(s -> {
            DisposalBatch b = batches.findOwnedForUpdate(tenantId.getValue(), batchId).orElseThrow();
            if (!DisposalBatch.APPROVED.equals(b.getStatus())) return;
            List<DisposalBatchItem> all = batchItems.findForBatch(batchId);
            if (all.stream().anyMatch(DisposalBatchItem::isPending)) return;   // another approver is still working through it
            int d = count(all, DisposalBatchItem.DISPOSED), k = count(all, DisposalBatchItem.SKIPPED), f = count(all, DisposalBatchItem.FAILED);
            b.complete(d, k, f);
            batches.save(b);
            trail.record(tenantId, "DISPOSAL_COMPLETED", "DISPOSAL_BATCH", b.getId(), null, Map.of("disposed", d, "skipped", k, "failed", f));
        });
        log.info("Disposal {} run by {}: {} disposed, {} skipped, {} failed", batchId, by, disposed, skipped, failed);
        return batch(tenantId, batchId);
    }

    /** One record, in its own transaction. Returns the outcome; a failure puts the record back on the due list. */
    private String disposeOne(TenantId tenantId, UUID batchId, UUID itemId, UUID by) {
        try {
            return tx.execute(s -> {
                RetentionItem i = items.findOwnedForUpdate(tenantId.getValue(), itemId).orElseThrow(() -> notFound(itemId));
                DisposalBatchItem bi = batchItems.findById(new DisposalBatchItem.Key(batchId, itemId)).orElseThrow();
                if (!bi.isPending()) return bi.getOutcome();
                if (!RetentionItem.PROPOSED.equals(i.getStatus()) || !batchId.equals(i.getBatchId()))
                    return skip(tenantId, i, bi, "No longer part of this request", false);
                if (i.isLegalHold()) return skip(tenantId, i, bi, "Placed under legal hold after it was proposed", true);
                if (i.getRetainUntil() == null || !i.getRetainUntil().isBefore(today()))
                    return skip(tenantId, i, bi, "No longer due: its retention period changed after it was proposed", true);
                RetentionProvider p = provider(i.getModuleCode())
                        .orElseThrow(() -> new IllegalStateException("No module is set up to dispose of " + i.getModuleCode() + " records"));
                RetentionProvider.Disposed d = p.dispose(tenantId, i.getSourceType(), i.getSourceId(), batchId, by);
                String detail = d == null ? "Disposed" : (d.detail() == null ? d.filesRemoved() + " file(s) removed" : d.detail());
                i.disposed(batchId, detail);
                items.save(i);
                bi.done(DisposalBatchItem.DISPOSED, detail);
                batchItems.save(bi);
                Map<String, Object> after = itemSummary(i);
                after.put("detail", detail);
                trail.record(tenantId, "RECORD_DISPOSED", "RETENTION_ITEM", i.getId(), null, after);
                return DisposalBatchItem.DISPOSED;
            });
        } catch (RuntimeException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("Disposal {} could not dispose of item {}: {}", batchId, itemId, why);
            newTx.executeWithoutResult(s -> {
                RetentionItem i = items.findOwnedForUpdate(tenantId.getValue(), itemId).orElse(null);
                DisposalBatchItem bi = batchItems.findById(new DisposalBatchItem.Key(batchId, itemId)).orElse(null);
                if (bi != null && bi.isPending()) { bi.done(DisposalBatchItem.FAILED, why); batchItems.save(bi); }
                if (i != null && RetentionItem.PROPOSED.equals(i.getStatus()) && batchId.equals(i.getBatchId())) { i.returnToActive(); items.save(i); }
                trail.record(tenantId, "DISPOSAL_FAILED", "RETENTION_ITEM", itemId, null, Map.of("batch", batchId.toString(), "reason", why));
            });
            return DisposalBatchItem.FAILED;
        }
    }

    private String skip(TenantId tenantId, RetentionItem i, DisposalBatchItem bi, String why, boolean returnItem) {
        if (returnItem) { i.returnToActive(); items.save(i); }
        bi.done(DisposalBatchItem.SKIPPED, why);
        batchItems.save(bi);
        trail.record(tenantId, "DISPOSAL_SKIPPED", "RETENTION_ITEM", i.getId(), null, Map.of("batch", bi.getBatchId().toString(), "reason", why));
        return DisposalBatchItem.SKIPPED;
    }

    public RetentionViews.Batch reject(TenantId tenantId, UUID batchId, RetentionViews.DecisionRequest req, UUID by, String byName, boolean supportSession) {
        if (supportSession) throw refused(tenantId, batchId, "A support session cannot decide a disposal.", "SUPPORT_SESSION", by, byName);
        String note = RetentionRules.reason(req == null ? null : req.note(), "reject a disposal");
        DisposalBatch found = batches.findOwned(tenantId.getValue(), batchId).orElseThrow(() -> new ResourceNotFoundException("Disposal request", String.valueOf(batchId)));
        if (RetentionRules.sameOrUnknownPerson(found.getProposedBy(), by))
            throw refused(tenantId, batchId, "You proposed this disposal: withdraw it instead.", "SAME_PERSON", by, byName);
        close(tenantId, batchId, b -> { b.reject(by, byName, note); return "Rejected: " + note; }, "DISPOSAL_REJECTED", byName);
        return batch(tenantId, batchId);
    }

    public RetentionViews.Batch cancel(TenantId tenantId, UUID batchId, UUID by, String byName) {
        DisposalBatch found = batches.findOwned(tenantId.getValue(), batchId).orElseThrow(() -> new ResourceNotFoundException("Disposal request", String.valueOf(batchId)));
        if (by == null || !by.equals(found.getProposedBy()))
            throw new HandyFlowException("Only the person who proposed it can withdraw a disposal request.", HttpStatus.FORBIDDEN, "NOT_PROPOSER");
        close(tenantId, batchId, b -> { b.cancel(by); return "Withdrawn by the proposer"; }, "DISPOSAL_CANCELLED", byName);
        return batch(tenantId, batchId);
    }

    /** Rejects or withdraws: every record goes back to the due list. */
    private void close(TenantId tenantId, UUID batchId, Function<DisposalBatch, String> decide, String action, String byName) {
        tx.executeWithoutResult(s -> {
            DisposalBatch b = batches.findOwnedForUpdate(tenantId.getValue(), batchId).orElseThrow();
            if (!DisposalBatch.PROPOSED.equals(b.getStatus()))
                throw conflict("This disposal request is " + b.getStatus().toLowerCase(Locale.ROOT) + " and can no longer be changed.", "WRONG_STATE");
            String why;
            try { why = decide.apply(b); }
            catch (IllegalStateException e) { throw conflict(e.getMessage(), "WRONG_STATE"); }
            batches.save(b);
            for (DisposalBatchItem bi : batchItems.findForBatch(batchId)) {
                if (!bi.isPending()) continue;
                bi.done(DisposalBatchItem.SKIPPED, why);
                batchItems.save(bi);
                items.findOwnedForUpdate(tenantId.getValue(), bi.getItemId()).ifPresent(i -> {
                    if (RetentionItem.PROPOSED.equals(i.getStatus()) && batchId.equals(i.getBatchId())) { i.returnToActive(); items.save(i); }
                });
            }
            trail.record(tenantId, action, "DISPOSAL_BATCH", b.getId(), Map.of("status", DisposalBatch.PROPOSED),
                    Map.of("status", b.getStatus(), "by", String.valueOf(byName), "note", why));
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Writes the refused attempt to the audit trail (committed on its own) and returns the refusal to throw. */
    private HandyFlowException refused(TenantId tenantId, UUID batchId, String message, String code, UUID by, String byName) {
        try {
            newTx.executeWithoutResult(s -> trail.record(tenantId, "DISPOSAL_REFUSED", "DISPOSAL_BATCH", batchId, null,
                    Map.of("reason", code, "by", String.valueOf(byName), "userId", String.valueOf(by))));
        } catch (RuntimeException e) {
            log.error("Could not record a refused disposal attempt ({}): {}", code, e.getMessage());
        }
        return new HandyFlowException(message, HttpStatus.FORBIDDEN, code);
    }

    private Optional<RetentionProvider> provider(String module) {
        return providers.orderedStream().filter(p -> module.equals(p.moduleCode())).findFirst();
    }

    private RetentionItem owned(TenantId tenantId, UUID id) {
        return items.findOwned(tenantId.getValue(), id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(UUID id) { return new ResourceNotFoundException("Retention record", String.valueOf(id)); }

    private static HandyFlowException conflict(String message, String code) { return new HandyFlowException(message, HttpStatus.CONFLICT, code); }

    private static String label(RetentionItem i) {
        return i.getReference() != null ? i.getReference() : i.getTitle();
    }

    private static String whyNotDue(RetentionItem i, LocalDate today) {
        if (i.isDisposed()) return "already disposed";
        if (RetentionItem.PROPOSED.equals(i.getStatus())) return "already in a disposal request";
        if (i.isLegalHold()) return "under legal hold";
        if (i.getRetainUntil() == null) return "its retention period has not started";
        return "kept until " + i.getRetainUntil();
    }

    private static int count(List<DisposalBatchItem> all, String outcome) {
        return (int) all.stream().filter(b -> outcome.equals(b.getOutcome())).count();
    }

    private List<RetentionViews.Item> views(UUID tenantId, List<RetentionItem> list) {
        if (list.isEmpty()) return List.of();
        List<RetentionPolicy> rules = policies.findForTenant(tenantId);
        LocalDate today = today();
        return list.stream().map(i -> view(i, rules, today)).toList();
    }

    private static RetentionViews.Item view(RetentionItem i, List<RetentionPolicy> rules, LocalDate today) {
        RetentionRules.Resolved r = RetentionRules.resolve(rules, i.getModuleCode(), i.getCategory(), i.getJurisdiction());
        return new RetentionViews.Item(i.getId(), i.getModuleCode(), i.getSourceType(), i.getSourceId(), i.getCategory(), i.getJurisdiction(),
                i.getReference(), i.getTitle(), i.getContextLabel(), i.getClockStart(), i.getRetainUntil(), i.getPolicyYears(),
                r.source().name(), r.confirmed(), r.personalInfo(), i.isDue(today), i.isLegalHold(), i.getHoldReason(), i.getHoldByName(),
                i.getHoldAt(), i.getStatus(), i.getBatchId(), i.getDisposedAt(), i.getDisposalDetail());
    }

    private List<RetentionViews.Policy> policyRows(UUID tenantId) {
        List<RetentionPolicy> rows = policies.findForTenant(tenantId);
        Map<String, Long> kept = new HashMap<>();
        List<String[]> inUse = new ArrayList<>();
        for (Object[] r : items.countKeptByCategory(tenantId)) {
            String m = String.valueOf(r[0]), c = String.valueOf(r[1]), j = String.valueOf(r[2]);
            kept.put(m + "|" + c + "|" + j, ((Number) r[3]).longValue());
            inUse.add(new String[]{m, c, j});
        }
        List<RetentionViews.Policy> out = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (RetentionPolicy p : rows) {
            String key = p.getModuleCode() + "|" + p.getCategory() + "|" + p.getJurisdiction();
            covered.add(key);
            long n = RetentionRules.ANY_CATEGORY.equals(p.getCategory())
                    ? inUse.stream().filter(u -> u[0].equals(p.getModuleCode()) && u[2].equals(p.getJurisdiction())
                            && rows.stream().noneMatch(q -> q.getModuleCode().equals(u[0]) && q.getCategory().equals(u[1]) && q.getJurisdiction().equals(u[2])))
                      .mapToLong(u -> kept.getOrDefault(u[0] + "|" + u[1] + "|" + u[2], 0L)).sum()
                    : kept.getOrDefault(key, 0L);
            out.add(new RetentionViews.Policy(p.getId(), p.getModuleCode(), p.getCategory(), p.getJurisdiction(), p.getRetainYears(),
                    p.isPersonalInfo(), p.isConfirmed(), p.getNotes(), p.getUpdatedByName(), p.getUpdatedAt(), n, false));
        }
        for (String[] u : inUse) {
            String key = u[0] + "|" + u[1] + "|" + u[2];
            if (covered.contains(key)) continue;
            RetentionRules.Resolved r = RetentionRules.resolve(rows, u[0], u[1], u[2]);
            if (r.source() != RetentionRules.Source.DEFAULT) continue;   // covered by the module's '*' row, already listed
            out.add(new RetentionViews.Policy(null, u[0], u[1], u[2], RetentionRules.DEFAULT_YEARS, false, false,
                    "No policy set: the 7-year default applies until one is set", null, null, kept.getOrDefault(key, 0L), true));
        }
        out.sort(Comparator.comparing(RetentionViews.Policy::moduleCode).thenComparing(RetentionViews.Policy::category)
                .thenComparing(RetentionViews.Policy::jurisdiction));
        return out;
    }

    private RetentionViews.Batch batchView(DisposalBatch b, List<DisposalBatchItem> list) {
        List<RetentionViews.BatchItem> rows = null;
        if (list != null) {
            Map<UUID, RetentionViews.Item> byId = views(b.getTenantId(), items.findOwnedIn(b.getTenantId(), list.stream().map(DisposalBatchItem::getItemId).toList()))
                    .stream().collect(Collectors.toMap(RetentionViews.Item::id, Function.identity()));
            rows = list.stream().map(x -> new RetentionViews.BatchItem(x.getItemId(), x.getOutcome(), x.getDetail(), x.getDoneAt(), byId.get(x.getItemId())))
                    .sorted(Comparator.comparing((RetentionViews.BatchItem x) -> x.item() == null || x.item().reference() == null ? "" : x.item().reference()))
                    .toList();
        }
        return new RetentionViews.Batch(b.getId(), b.getStatus(), b.getReason(), b.getProposedBy(), b.getProposedByName(), b.getProposedAt(),
                b.getDecidedBy(), b.getDecidedByName(), b.getDecidedAt(), b.getDecisionNote(), b.getCancelledAt(), b.getCompletedAt(),
                b.getItemCount(), b.getDisposedCount(), b.getSkippedCount(), b.getFailedCount(), rows);
    }

    private static Map<String, Object> itemSummary(RetentionItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("module", i.getModuleCode());
        m.put("sourceType", i.getSourceType());
        m.put("sourceId", i.getSourceId().toString());
        m.put("category", i.getCategory());
        if (i.getReference() != null) m.put("reference", i.getReference());
        m.put("title", i.getTitle());
        if (i.getRetainUntil() != null) m.put("retainUntil", i.getRetainUntil().toString());
        m.put("legalHold", i.isLegalHold());
        m.put("status", i.getStatus());
        return m;
    }

    private static Map<String, Object> policySummary(RetentionPolicy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("module", p.getModuleCode());
        m.put("category", p.getCategory());
        m.put("jurisdiction", p.getJurisdiction());
        m.put("retainYears", p.getRetainYears());
        m.put("personalInfo", p.isPersonalInfo());
        m.put("confirmed", p.isConfirmed());
        return m;
    }
}
