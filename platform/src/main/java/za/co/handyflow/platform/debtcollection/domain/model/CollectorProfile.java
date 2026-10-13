package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * A person who works collection cases, with what the allocator needs to know: team, seniority, skills, and limits.
 * userId is the platform user id (the same id stored on a case's assignedToUserId); the name is a denormalised
 * snapshot like every other *UserName here. Only people with a profile are auto-allocated to — a case held by someone
 * without a profile is never moved automatically.
 * <p>
 * Limits (all optional): maxOpenCases, maxCaseValue (the largest single balance they may be given) and
 * maxPortfolioValue (their total held balance after the new case). awayUntil marks leave: the collector is skipped
 * through that date inclusive. lastAssignedAt drives the round-robin rotation.
 */
@Entity
@Table(name = "debtcollection_collector_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectorProfile extends AggregateRoot<CollectorProfile> {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Column(length = 100)
    private String team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CollectorTier tier;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "debtcollection_collector_skills", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "skill", nullable = false, length = 60)
    private Set<String> skills = new HashSet<>();

    @Column(name = "max_open_cases")
    private Integer maxOpenCases;

    @Column(name = "max_case_value", precision = 15, scale = 2)
    private BigDecimal maxCaseValue;

    @Column(name = "max_portfolio_value", precision = 15, scale = 2)
    private BigDecimal maxPortfolioValue;

    @Column(name = "away_until")
    private LocalDate awayUntil;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_assigned_at")
    private Instant lastAssignedAt;

    public static CollectorProfile create(TenantId tenantId, UUID userId, String userName, String team, CollectorTier tier,
                                          Set<String> skills, Integer maxOpenCases, BigDecimal maxCaseValue,
                                          BigDecimal maxPortfolioValue, LocalDate awayUntil) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        CollectorProfile p = new CollectorProfile();
        p.initTenantId(tenantId);
        p.userId = userId;
        p.active = true;
        p.update(userName, team, tier, skills, maxOpenCases, maxCaseValue, maxPortfolioValue, awayUntil);
        return p;
    }

    public void update(String userName, String team, CollectorTier tier, Set<String> skills, Integer maxOpenCases,
                       BigDecimal maxCaseValue, BigDecimal maxPortfolioValue, LocalDate awayUntil) {
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("userName is required");
        if (maxOpenCases != null && maxOpenCases < 0) throw new IllegalArgumentException("maxOpenCases cannot be negative");
        if (maxCaseValue != null && maxCaseValue.signum() <= 0) throw new IllegalArgumentException("maxCaseValue must be positive");
        if (maxPortfolioValue != null && maxPortfolioValue.signum() <= 0) throw new IllegalArgumentException("maxPortfolioValue must be positive");
        Set<String> clean = new HashSet<>();
        if (skills != null) {
            for (String s : skills) {
                if (s != null && !s.isBlank()) clean.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (clean.size() > 20) throw new IllegalArgumentException("At most 20 skills per collector");
        this.userName = userName;
        this.team = team == null || team.isBlank() ? null : team.trim();
        this.tier = tier != null ? tier : CollectorTier.STANDARD;
        this.skills = clean;
        this.maxOpenCases = maxOpenCases;
        this.maxCaseValue = maxCaseValue;
        this.maxPortfolioValue = maxPortfolioValue;
        this.awayUntil = awayUntil;
    }

    public void deactivate() { this.active = false; }

    public void activate() { this.active = true; }

    public void markAssigned(Instant at) { this.lastAssignedAt = at; }
}
