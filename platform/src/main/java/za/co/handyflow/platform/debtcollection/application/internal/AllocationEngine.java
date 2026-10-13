package za.co.handyflow.platform.debtcollection.application.internal;

import za.co.handyflow.platform.debtcollection.domain.model.AllocationStrategy;
import za.co.handyflow.platform.debtcollection.domain.model.CaseStatus;
import za.co.handyflow.platform.debtcollection.domain.model.CollectorTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The allocation decision, with no database or framework: given cases, collectors (with their current load), rules and a
 * default strategy, decide who gets each case — deterministically, so the same inputs always give the same plan (which is
 * what makes a "preview" trustworthy).
 * <p>
 * ORDER. Cases are handled highest balance first, so the biggest balances get first pick of the right collectors, and
 * each decision updates the in-memory loads so the batch spreads evenly rather than piling onto one person.
 * ELIGIBILITY for a case (all must hold): collector active and not away; under maxOpenCases; case balance within
 * maxCaseValue; held value + this balance within maxPortfolioValue; and — only if a rule matched — the rule's team,
 * skill and minimum tier. The FIRST matching rule (by order) decides the requirements; no matching rule = no extra
 * requirements. If nobody is eligible the case is UNALLOCATED with the reason; it is never relaxed to "anyone".
 * Strategy ties are broken by user id so results are stable.
 */
final class AllocationEngine {

    record CaseFacts(UUID caseId, String caseNumber, String debtorName, BigDecimal balance, CaseStatus status,
                     long ageDays, UUID currentUserId, String currentUserName) {}

    record RuleView(UUID id, String name, int order, BigDecimal minBalance, BigDecimal maxBalance, Integer minAgeDays,
                    Integer maxAgeDays, Set<CaseStatus> statuses, String team, String skill, CollectorTier minTier,
                    AllocationStrategy strategy) {}

    /** Mutable working state of one collector during a batch. */
    static final class Collector {
        final UUID userId;
        final String name;
        final String team;
        final CollectorTier tier;
        final Set<String> skills;
        final Integer maxOpenCases;
        final BigDecimal maxCaseValue;
        final BigDecimal maxPortfolioValue;
        final LocalDate awayUntil;
        final boolean active;
        int openCases;
        BigDecimal portfolio;
        long lastAssigned;

        Collector(UUID userId, String name, String team, CollectorTier tier, Set<String> skills, Integer maxOpenCases,
                  BigDecimal maxCaseValue, BigDecimal maxPortfolioValue, LocalDate awayUntil, boolean active,
                  int openCases, BigDecimal portfolio, long lastAssigned) {
            this.userId = userId;
            this.name = name;
            this.team = team;
            this.tier = tier;
            this.skills = skills;
            this.maxOpenCases = maxOpenCases;
            this.maxCaseValue = maxCaseValue;
            this.maxPortfolioValue = maxPortfolioValue;
            this.awayUntil = awayUntil;
            this.active = active;
            this.openCases = openCases;
            this.portfolio = portfolio;
            this.lastAssigned = lastAssigned;
        }

        boolean availableOn(LocalDate today) {
            return active && (awayUntil == null || today.isAfter(awayUntil));
        }
    }

    record Decision(CaseFacts facts, UUID toUserId, String toName, String ruleName, String reason) {
        boolean allocated() { return toUserId != null; }
    }

    private AllocationEngine() {}

    static RuleView matchRule(List<RuleView> rules, CaseFacts f) {
        return rules.stream().sorted(Comparator.comparingInt(RuleView::order))
                .filter(r -> matches(r, f)).findFirst().orElse(null);
    }

    static boolean matches(RuleView r, CaseFacts f) {
        if (r.minBalance() != null && f.balance().compareTo(r.minBalance()) < 0) return false;
        if (r.maxBalance() != null && f.balance().compareTo(r.maxBalance()) > 0) return false;
        if (r.minAgeDays() != null && f.ageDays() < r.minAgeDays()) return false;
        if (r.maxAgeDays() != null && f.ageDays() > r.maxAgeDays()) return false;
        return r.statuses() == null || r.statuses().isEmpty() || r.statuses().contains(f.status());
    }

    static boolean capacityOk(Collector c, CaseFacts f) {
        if (c.maxOpenCases != null && c.openCases >= c.maxOpenCases) return false;
        if (c.maxCaseValue != null && f.balance().compareTo(c.maxCaseValue) > 0) return false;
        return c.maxPortfolioValue == null || c.portfolio.add(f.balance()).compareTo(c.maxPortfolioValue) <= 0;
    }

    static boolean meetsRule(Collector c, RuleView r) {
        if (r == null) return true;
        if (r.team() != null && (c.team == null || !c.team.equalsIgnoreCase(r.team()))) return false;
        if (r.skill() != null && !c.skills.contains(r.skill().toLowerCase(Locale.ROOT))) return false;
        return r.minTier() == null || c.tier.compareTo(r.minTier()) >= 0;
    }

    static List<Decision> allocateBatch(List<CaseFacts> cases, List<Collector> collectors, List<RuleView> rules,
                                        AllocationStrategy defaultStrategy, LocalDate today) {
        long clock = collectors.stream().mapToLong(c -> c.lastAssigned).max().orElse(0L);
        List<CaseFacts> ordered = new ArrayList<>(cases);
        ordered.sort(Comparator.comparing(CaseFacts::balance).reversed().thenComparing(f -> f.caseNumber() == null ? "" : f.caseNumber()));

        List<Decision> out = new ArrayList<>();
        for (CaseFacts f : ordered) {
            RuleView rule = matchRule(rules, f);
            String ruleName = rule == null ? null : rule.name();
            List<Collector> pool = collectors.stream()
                    .filter(c -> c.availableOn(today) && capacityOk(c, f) && meetsRule(c, rule)).toList();
            if (pool.isEmpty()) {
                out.add(new Decision(f, null, null, ruleName, rule == null
                        ? "No available collector has capacity for this case"
                        : "No available collector with capacity meets rule '" + rule.name() + "'"));
                continue;
            }
            AllocationStrategy strategy = rule != null && rule.strategy() != null ? rule.strategy() : defaultStrategy;
            Collector pick = pool.stream().min(comparator(strategy)).orElseThrow();
            pick.openCases++;
            pick.portfolio = pick.portfolio.add(f.balance());
            pick.lastAssigned = ++clock;
            out.add(new Decision(f, pick.userId, pick.name, ruleName,
                    (rule == null ? "No rule matched" : "Rule '" + rule.name() + "'") + "; " + strategy + " chose " + pick.name));
        }
        return out;
    }

    /** Human-readable reasons this collector cannot take this case now (empty = they can). Used for manual assignment warnings. */
    static List<String> violations(Collector c, CaseFacts f, LocalDate today) {
        List<String> v = new ArrayList<>();
        if (!c.active) v.add(c.name + " is inactive");
        else if (c.awayUntil != null && !today.isAfter(c.awayUntil)) v.add(c.name + " is away until " + c.awayUntil);
        if (c.maxOpenCases != null && c.openCases >= c.maxOpenCases) {
            v.add(c.name + " already holds " + c.openCases + " of a maximum " + c.maxOpenCases + " cases");
        }
        if (c.maxCaseValue != null && f.balance().compareTo(c.maxCaseValue) > 0) {
            v.add("balance R" + f.balance() + " exceeds " + c.name + "'s single-case limit of R" + c.maxCaseValue);
        }
        if (c.maxPortfolioValue != null && c.portfolio.add(f.balance()).compareTo(c.maxPortfolioValue) > 0) {
            v.add("this case would take " + c.name + " over the portfolio limit of R" + c.maxPortfolioValue);
        }
        return v;
    }

    private static Comparator<Collector> comparator(AllocationStrategy s) {
        Comparator<Collector> byRotation = Comparator.comparingLong((Collector c) -> c.lastAssigned);
        Comparator<Collector> byId = Comparator.comparing((Collector c) -> c.userId.toString());
        return switch (s) {
            case ROUND_ROBIN -> byRotation.thenComparing(byId);
            case LEAST_LOADED -> Comparator.comparingInt((Collector c) -> c.openCases).thenComparing(byRotation).thenComparing(byId);
            case LEAST_VALUE -> Comparator.comparing((Collector c) -> c.portfolio)
                    .thenComparingInt(c -> c.openCases).thenComparing(byRotation).thenComparing(byId);
        };
    }
}
