package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.CaseFacts;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.Collector;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.Decision;
import za.co.handyflow.platform.debtcollection.application.internal.AllocationEngine.RuleView;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationRule;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationScope;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationSettings;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorProfile;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemStatus;
import za.co.handyflow.platform.debtcollection.domain.repository.AllocationQueryRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.AllocationRuleRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.CollectorProfileRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.DebtCollectionCaseRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plans and applies case allocation. The decision itself is AllocationEngine (pure); this class gathers the facts
 * (cases, collectors with their live load, rules), turns decisions into a PREVIEWABLE plan, and applies a plan one case at
 * a time.
 * <p>
 * PREVIEW → APPLY. plan() changes nothing. apply() recomputes the plan and applies it case by case, each in its own
 * transaction; before moving a case it checks the case is still held by the person the plan assumed (a human may have
 * reassigned it meanwhile) and skips it if not. Applying goes through DebtCollectionCaseService.assign(), so every move is
 * audited on the case, plus an ALLOCATED entry with the reason. Open work items then follow the case to its new
 * collector (via WorkItemService.assign, so those moves are audited too).
 * <p>
 * LOADS use the case's stored totalOutstanding snapshot (fast, no invoicing call per case) and count non-terminal cases only.
 * Cases held by a person WITHOUT a collector profile are never moved automatically. Terminal = CLOSED, WRITTEN_OFF, SETTLED.
 * <p>
 * Work item drift: the existing /cases/{id}/assign endpoint does not move work items; reconcileWorkItems() (run by the
 * scheduler) repairs that by making live work items follow their case's current assignee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocationService {

    private static final Set<CaseStatus> TERMINAL = EnumSet.of(CaseStatus.CLOSED, CaseStatus.WRITTEN_OFF, CaseStatus.SETTLED);

    public record ProposedAssignment(UUID caseId, String caseNumber, String debtorName, BigDecimal balance, UUID fromUserId,
                                     String fromUserName, UUID toUserId, String toUserName, String ruleName, String reason) {}

    public record Unallocated(UUID caseId, String caseNumber, String debtorName, BigDecimal balance, String ruleName, String reason) {}

    public record Plan(AllocationScope scope, List<ProposedAssignment> assignments, List<Unallocated> unallocated) {}

    public record ApplyResult(Plan plan, int applied, int skipped, int failed, List<String> problems) {}

    public record ManualResult(DebtCollectionCase debtCase, List<String> warnings, boolean overridden) {}

    public record CollectorLoadView(CollectorProfile profile, int openCases, BigDecimal portfolioValue, long openWorkItems,
                                    long overdueWorkItems, Integer utilisationPercent) {}

    private final DebtCollectionCaseRepository caseRepository;
    private final DebtCollectionCaseService caseService;
    private final CollectorProfileRepository profileRepository;
    private final AllocationRuleRepository ruleRepository;
    private final AllocationConfigService configService;
    private final AllocationQueryRepository queryRepository;
    private final WorkItemService workItemService;
    private final CaseAuditService audit;

    // ── plan / apply ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Plan plan(TenantId tenantId, AllocationScope scope, UUID fromUserId) {
        if (scope == AllocationScope.FROM_USER && fromUserId == null) {
            throw new IllegalArgumentException("fromUserId is required for scope FROM_USER");
        }
        LocalDate today = LocalDate.now();
        AllocationSettings settings = configService.settings(tenantId);
        List<CollectorProfile> profiles = profileRepository.findAllForTenant(tenantId);
        Map<UUID, CollectorProfile> byUser = profiles.stream().collect(Collectors.toMap(CollectorProfile::getUserId, Function.identity(), (a, b) -> a));
        List<DebtCollectionCase> live = caseRepository.findAllActive(tenantId).stream().filter(c -> !TERMINAL.contains(c.getStatus())).toList();

        List<DebtCollectionCase> toMove = live.stream().filter(c -> inScope(c, scope, fromUserId, byUser, today)).toList();
        Set<UUID> moving = toMove.stream().map(DebtCollectionCase::getId).collect(Collectors.toSet());

        Map<UUID, Collector> states = new HashMap<>();
        for (CollectorProfile p : profiles) {
            if (scope == AllocationScope.FROM_USER && p.getUserId().equals(fromUserId)) continue; // the leaver is not a target
            states.put(p.getUserId(), new Collector(p.getUserId(), p.getUserName(), p.getTeam(), p.getTier(), p.getSkills(),
                    p.getMaxOpenCases(), p.getMaxCaseValue(), p.getMaxPortfolioValue(), p.getAwayUntil(), p.isActive(), 0,
                    BigDecimal.ZERO, p.getLastAssignedAt() == null ? 0L : p.getLastAssignedAt().toEpochMilli()));
        }
        for (DebtCollectionCase c : live) {
            if (moving.contains(c.getId()) || c.getAssignedToUserId() == null) continue;
            Collector s = states.get(c.getAssignedToUserId());
            if (s != null) {
                s.openCases++;
                s.portfolio = s.portfolio.add(c.getTotalOutstanding());
            }
        }

        List<RuleView> rules = ruleRepository.findActiveForTenant(tenantId).stream().map(AllocationService::view).toList();
        List<CaseFacts> facts = toMove.stream().map(c -> new CaseFacts(c.getId(), c.getCaseNumber(), c.getDebtorName(),
                c.getTotalOutstanding(), c.getStatus(), ChronoUnit.DAYS.between(c.getOpenedDate(), today),
                c.getAssignedToUserId(), c.getAssignedToUserName())).toList();

        List<ProposedAssignment> proposed = new ArrayList<>();
        List<Unallocated> unallocated = new ArrayList<>();
        for (Decision d : AllocationEngine.allocateBatch(facts, new ArrayList<>(states.values()), rules, settings.getDefaultStrategy(), today)) {
            CaseFacts f = d.facts();
            if (d.allocated()) {
                proposed.add(new ProposedAssignment(f.caseId(), f.caseNumber(), f.debtorName(), f.balance(), f.currentUserId(),
                        f.currentUserName(), d.toUserId(), d.toName(), d.ruleName(), d.reason()));
            } else {
                unallocated.add(new Unallocated(f.caseId(), f.caseNumber(), f.debtorName(), f.balance(), d.ruleName(), d.reason()));
            }
        }
        return new Plan(scope, proposed, unallocated);
    }

    /** Not @Transactional: each case is moved in its own transaction so one failure cannot undo the rest. */
    public ApplyResult apply(TenantId tenantId, AllocationScope scope, UUID fromUserId) {
        Plan plan = plan(tenantId, scope, fromUserId);
        int applied = 0, skipped = 0, failed = 0;
        List<String> problems = new ArrayList<>();
        for (ProposedAssignment a : plan.assignments()) {
            try {
                DebtCollectionCase c = caseService.get(tenantId, a.caseId());
                if (!Objects.equals(c.getAssignedToUserId(), a.fromUserId()) || TERMINAL.contains(c.getStatus())) {
                    skipped++;
                    problems.add(a.caseNumber() + ": changed since the plan was made — skipped");
                    continue;
                }
                move(tenantId, a.caseId(), a.toUserId(), a.toUserName(), a.reason());
                applied++;
            } catch (Exception e) {
                failed++;
                problems.add(a.caseNumber() + ": " + e.getMessage());
                log.warn("[Debt Collection] allocation failed case={}: {}", a.caseId(), e.getMessage());
            }
        }
        return new ApplyResult(plan, applied, skipped, failed, problems);
    }

    // ── manual assignment with capacity check ────────────────────────────────

    /**
     * Assigns one case by hand. If the target has a profile and cannot take the case (inactive, away, over a limit) the
     * assignment is refused unless override=true with a reason — then it goes through and the override is audited.
     * A target WITHOUT a profile is allowed (capacity can't be checked; the warning says so).
     */
    public ManualResult assignManually(TenantId tenantId, UUID caseId, UUID userId, boolean override, String reason) {
        DebtCollectionCase c = caseService.get(tenantId, caseId);
        if (TERMINAL.contains(c.getStatus())) throw new IllegalStateException("Cannot assign a " + c.getStatus() + " case");
        List<String> warnings = new ArrayList<>();
        String name;
        CollectorProfile p = profileRepository.findByUserId(tenantId, userId).orElse(null);
        if (p == null) {
            name = userId.toString();
            warnings.add("This user has no collector profile, so capacity and availability were not checked");
        } else {
            name = p.getUserName();
            int open = 0;
            BigDecimal value = BigDecimal.ZERO;
            for (DebtCollectionCase other : caseRepository.findAllActive(tenantId)) {
                if (!TERMINAL.contains(other.getStatus()) && userId.equals(other.getAssignedToUserId()) && !other.getId().equals(caseId)) {
                    open++;
                    value = value.add(other.getTotalOutstanding());
                }
            }
            Collector state = new Collector(userId, p.getUserName(), p.getTeam(), p.getTier(), p.getSkills(), p.getMaxOpenCases(),
                    p.getMaxCaseValue(), p.getMaxPortfolioValue(), p.getAwayUntil(), p.isActive(), open, value, 0L);
            warnings.addAll(AllocationEngine.violations(state, new CaseFacts(caseId, c.getCaseNumber(), c.getDebtorName(),
                    c.getTotalOutstanding(), c.getStatus(), 0, c.getAssignedToUserId(), c.getAssignedToUserName()), LocalDate.now()));
        }
        boolean blocking = p != null && !warnings.isEmpty();
        if (blocking && !override) {
            throw new IllegalStateException("Assignment refused: " + String.join("; ", warnings)
                    + ". A supervisor can repeat it with override=true and a reason.");
        }
        if (blocking && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A reason is required to override capacity limits");
        }
        String why = blocking ? "Manual override: " + reason : "Manual assignment";
        move(tenantId, caseId, userId, name, why);
        if (blocking) audit.record(tenantId, caseId, "ALLOCATION_OVERRIDE", String.join("; ", warnings) + " — reason: " + reason);
        return new ManualResult(caseService.get(tenantId, caseId), warnings, blocking);
    }

    // ── reconciliation & views ───────────────────────────────────────────────

    /** Makes every live work item follow its case's current assignee. Returns how many were moved. */
    public int reconcileWorkItems(TenantId tenantId) {
        Map<UUID, DebtCollectionCase> cases = caseRepository.findAllActive(tenantId).stream()
                .collect(Collectors.toMap(DebtCollectionCase::getId, Function.identity(), (a, b) -> a));
        int moved = 0;
        for (WorkItem w : queryRepository.findLiveByTenant(tenantId)) {
            DebtCollectionCase c = cases.get(w.getCaseId());
            if (c == null || c.getAssignedToUserId() == null || c.getAssignedToUserId().equals(w.getAssignedToUserId())) continue;
            try {
                workItemService.assign(tenantId, w.getId(), c.getAssignedToUserId(), c.getAssignedToUserName(),
                        "Follows the case's collector", null, "allocation");
                moved++;
            } catch (Exception e) {
                log.warn("[Debt Collection] could not move work item {}: {}", w.getId(), e.getMessage());
            }
        }
        return moved;
    }

    @Transactional(readOnly = true)
    public List<CollectorLoadView> collectorLoads(TenantId tenantId) {
        LocalDate today = LocalDate.now();
        Map<UUID, int[]> cases = new HashMap<>();
        Map<UUID, BigDecimal> value = new HashMap<>();
        for (DebtCollectionCase c : caseRepository.findAllActive(tenantId)) {
            if (TERMINAL.contains(c.getStatus()) || c.getAssignedToUserId() == null) continue;
            cases.computeIfAbsent(c.getAssignedToUserId(), k -> new int[1])[0]++;
            value.merge(c.getAssignedToUserId(), c.getTotalOutstanding(), BigDecimal::add);
        }
        Map<UUID, long[]> items = new HashMap<>();
        for (WorkItem w : queryRepository.findLiveByTenant(tenantId)) {
            if (w.getAssignedToUserId() == null) continue;
            long[] a = items.computeIfAbsent(w.getAssignedToUserId(), k -> new long[2]);
            if (w.getStatus() == WorkItemStatus.OPEN) {
                a[0]++;
                if (w.getDueDate() != null && w.getDueDate().isBefore(today)) a[1]++;
            }
        }
        List<CollectorLoadView> out = new ArrayList<>();
        for (CollectorProfile p : profileRepository.findAllForTenant(tenantId)) {
            int open = cases.getOrDefault(p.getUserId(), new int[1])[0];
            long[] wi = items.getOrDefault(p.getUserId(), new long[2]);
            Integer util = p.getMaxOpenCases() == null || p.getMaxOpenCases() == 0 ? null
                    : BigDecimal.valueOf(open * 100L).divide(BigDecimal.valueOf(p.getMaxOpenCases()), 0, RoundingMode.HALF_UP).intValue();
            out.add(new CollectorLoadView(p, open, value.getOrDefault(p.getUserId(), BigDecimal.ZERO), wi[0], wi[1], util));
        }
        return out;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void move(TenantId tenantId, UUID caseId, UUID toUserId, String toName, String reason) {
        caseService.assign(tenantId, caseId, toUserId, toName);
        audit.record(tenantId, caseId, "ALLOCATED", "to " + toName + " — " + reason);
        for (WorkItem w : queryRepository.findLiveByCaseId(tenantId, caseId)) {
            if (!toUserId.equals(w.getAssignedToUserId())) {
                workItemService.assign(tenantId, w.getId(), toUserId, toName, "Follows the case's collector", null, "allocation");
            }
        }
        profileRepository.findByUserId(tenantId, toUserId).ifPresent(p -> {
            p.markAssigned(Instant.now());
            profileRepository.save(p);
        });
    }

    private static boolean inScope(DebtCollectionCase c, AllocationScope scope, UUID fromUserId, Map<UUID, CollectorProfile> byUser,
                                   LocalDate today) {
        UUID holder = c.getAssignedToUserId();
        return switch (scope) {
            case UNASSIGNED -> holder == null;
            case UNAVAILABLE -> holder == null || (byUser.containsKey(holder) && !available(byUser.get(holder), today));
            case FROM_USER -> fromUserId.equals(holder);
        };
    }

    private static boolean available(CollectorProfile p, LocalDate today) {
        return p.isActive() && (p.getAwayUntil() == null || today.isAfter(p.getAwayUntil()));
    }

    private static RuleView view(AllocationRule r) {
        return new RuleView(r.getId(), r.getName(), r.getRuleOrder(), r.getMinBalance(), r.getMaxBalance(), r.getMinAgeDays(),
                r.getMaxAgeDays(), r.statuses(), r.getRequiredTeam(), r.getRequiredSkill(), r.getMinTier(), r.getStrategy());
    }
}
