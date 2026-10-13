package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A routing rule: IF a case matches all the (optional) conditions THEN only collectors who satisfy the (optional)
 * requirements are eligible. Rules are tried in ruleOrder (lowest first) and the FIRST match wins. A case no rule
 * matches may go to any eligible collector. Conditions: balance range, case age range (days since opened) and a set
 * of case statuses. Requirements: team, skill and minimum tier. strategy overrides the tenant default for this rule.
 * If a rule matches but nobody meets its requirements the case stays UNALLOCATED and is reported — it is never
 * silently sent to someone the rule excludes (e.g. a very large balance falling to a junior).
 */
@Entity
@Table(name = "debtcollection_allocation_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllocationRule extends AggregateRoot<AllocationRule> {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "rule_order", nullable = false)
    private int ruleOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "min_balance", precision = 15, scale = 2)
    private BigDecimal minBalance;

    @Column(name = "max_balance", precision = 15, scale = 2)
    private BigDecimal maxBalance;

    @Column(name = "min_age_days")
    private Integer minAgeDays;

    @Column(name = "max_age_days")
    private Integer maxAgeDays;

    /** Comma-separated CaseStatus names; empty = any status. */
    @Column(name = "statuses_csv", length = 255)
    private String statusesCsv;

    @Column(name = "required_team", length = 100)
    private String requiredTeam;

    @Column(name = "required_skill", length = 60)
    private String requiredSkill;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_tier", length = 10)
    private CollectorTier minTier;

    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private AllocationStrategy strategy;

    public static AllocationRule create(TenantId tenantId, String name, int ruleOrder, BigDecimal minBalance,
                                        BigDecimal maxBalance, Integer minAgeDays, Integer maxAgeDays, Set<CaseStatus> statuses,
                                        String requiredTeam, String requiredSkill, CollectorTier minTier,
                                        AllocationStrategy strategy) {
        AllocationRule r = new AllocationRule();
        r.initTenantId(tenantId);
        r.active = true;
        r.update(name, ruleOrder, minBalance, maxBalance, minAgeDays, maxAgeDays, statuses, requiredTeam, requiredSkill,
                minTier, strategy);
        return r;
    }

    public void update(String name, int ruleOrder, BigDecimal minBalance, BigDecimal maxBalance, Integer minAgeDays,
                       Integer maxAgeDays, Set<CaseStatus> statuses, String requiredTeam, String requiredSkill,
                       CollectorTier minTier, AllocationStrategy strategy) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (ruleOrder < 0) throw new IllegalArgumentException("ruleOrder cannot be negative");
        if (minBalance != null && maxBalance != null && minBalance.compareTo(maxBalance) > 0) {
            throw new IllegalArgumentException("minBalance is greater than maxBalance");
        }
        if (minAgeDays != null && maxAgeDays != null && minAgeDays > maxAgeDays) {
            throw new IllegalArgumentException("minAgeDays is greater than maxAgeDays");
        }
        this.name = name;
        this.ruleOrder = ruleOrder;
        this.minBalance = minBalance;
        this.maxBalance = maxBalance;
        this.minAgeDays = minAgeDays;
        this.maxAgeDays = maxAgeDays;
        this.statusesCsv = statuses == null || statuses.isEmpty() ? null
                : statuses.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        this.requiredTeam = requiredTeam == null || requiredTeam.isBlank() ? null : requiredTeam.trim();
        this.requiredSkill = requiredSkill == null || requiredSkill.isBlank() ? null : requiredSkill.trim().toLowerCase(Locale.ROOT);
        this.minTier = minTier;
        this.strategy = strategy;
    }

    public Set<CaseStatus> statuses() {
        Set<CaseStatus> out = EnumSet.noneOf(CaseStatus.class);
        if (statusesCsv != null && !statusesCsv.isBlank()) {
            for (String s : statusesCsv.split(",")) out.add(CaseStatus.valueOf(s.trim()));
        }
        return out;
    }

    public void deactivate() { this.active = false; }

    public void activate() { this.active = true; }
}
