package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSlaClock;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The commercial agreement between the FM company and a client. Chunk 8 turns it into a contract:
 * <ul>
 *   <li><b>Coverage</b> — which sites ({@code siteIds}; empty = every site of the client) and which work
 *       categories ({@code coveredCategories}; null = all) it applies to. A client can now have several
 *       agreements as long as they don't cover the same sites and categories in overlapping dates.</li>
 *   <li><b>Billing basis</b> — RETAINER (covered work is INCLUDED in the flat monthly fee and never billed
 *       separately) or TIME_AND_MATERIALS (covered work is chargeable).</li>
 *   <li><b>Rate card</b> — {@code hourlyRate} is the default charge for labour lines and
 *       {@code partsMarkupPercent} the default markup on parts, applied to work orders raised under it.</li>
 *   <li><b>SLA targets</b> — per priority: response and resolution minutes on a 24X7 or BUSINESS_HOURS clock.</li>
 * </ul>
 * The terms in force are stamped onto each work order when it is created, so editing an agreement later never
 * rewrites the terms existing work was raised under (see {@code FmContractTermsService}).
 */
@Entity
@Table(name = "fm_service_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FmServiceAgreement {

    public static final Set<String> CATEGORIES = Set.of("PPM", "CORRECTIVE", "EMERGENCY", "INSPECTION", "OTHER");
    public static final List<String> PRIORITIES = List.of("LOW", "NORMAL", "HIGH", "URGENT", "EMERGENCY");

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(length = 100)
    private String name;

    @Column(name = "billing_type", nullable = false)
    private String billingType; // RETAINER, TIME_AND_MATERIALS

    @Column(name = "monthly_fee", precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "hourly_rate", precision = 15, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "parts_markup_percent", precision = 6, scale = 2)
    private BigDecimal partsMarkupPercent;

    @Column(name = "covered_categories", length = 100)
    private String coveredCategories; // comma-separated; null = all categories

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fm_agreement_sites", joinColumns = @JoinColumn(name = "agreement_id"))
    @Column(name = "site_id", nullable = false)
    private Set<UUID> siteIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "fm_agreement_sla_targets", joinColumns = @JoinColumn(name = "agreement_id"))
    private List<FmSlaTarget> slaTargets = new ArrayList<>();

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, ENDED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static FmServiceAgreement create(TenantId tenantId, UUID clientId, String name, String billingType,
                                            BigDecimal monthlyFee, BigDecimal hourlyRate, BigDecimal partsMarkupPercent,
                                            LocalDate startDate, LocalDate endDate, Set<UUID> siteIds,
                                            Set<String> categories, List<FmSlaTarget> slaTargets) {
        if (clientId == null) throw new IllegalArgumentException("clientId is required");
        if (startDate == null) throw new IllegalArgumentException("startDate is required");
        String type = billingType != null ? billingType.toUpperCase() : "TIME_AND_MATERIALS";
        if (!"RETAINER".equals(type) && !"TIME_AND_MATERIALS".equals(type))
            throw new IllegalArgumentException("billingType must be RETAINER or TIME_AND_MATERIALS");
        if ("RETAINER".equals(type) && (monthlyFee == null || monthlyFee.signum() <= 0))
            throw new IllegalArgumentException("A RETAINER agreement requires a positive monthlyFee");
        if (endDate != null && !endDate.isAfter(startDate))
            throw new IllegalArgumentException("endDate must be after startDate");

        FmServiceAgreement a = new FmServiceAgreement();
        a.tenantId = tenantId;
        a.clientId = clientId;
        a.name = blankToNull(name);
        a.billingType = type;
        a.monthlyFee = monthlyFee;
        a.hourlyRate = requireNonNegative(hourlyRate, "hourlyRate");
        a.partsMarkupPercent = requireMarkup(partsMarkupPercent);
        a.startDate = startDate;
        a.endDate = endDate;
        a.status = "ACTIVE";
        a.replaceSites(siteIds);
        a.replaceCategories(categories);
        a.replaceSlaTargets(slaTargets);
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    /**
     * Update. {@code endDate} is full-replace (null = open-ended). Every other argument is "null = leave unchanged":
     * for {@code siteIds} and {@code categories} an EMPTY set means "all sites" / "all categories", and for
     * {@code slaTargets} an empty list removes all targets.
     */
    public void update(String name, BigDecimal monthlyFee, BigDecimal hourlyRate, BigDecimal partsMarkupPercent,
                       LocalDate endDate, Set<UUID> siteIds, Set<String> categories, List<FmSlaTarget> slaTargets) {
        BigDecimal newFee = monthlyFee != null ? monthlyFee : this.monthlyFee;
        if ("RETAINER".equals(billingType) && (newFee == null || newFee.signum() <= 0))
            throw new IllegalArgumentException("A RETAINER agreement requires a positive monthlyFee");
        if (endDate != null && !endDate.isAfter(startDate))
            throw new IllegalArgumentException("endDate must be after startDate");

        if (name != null) this.name = blankToNull(name);
        this.monthlyFee = newFee;
        if (hourlyRate != null) this.hourlyRate = requireNonNegative(hourlyRate, "hourlyRate");
        if (partsMarkupPercent != null) this.partsMarkupPercent = requireMarkup(partsMarkupPercent);
        this.endDate = endDate;
        if (siteIds != null) replaceSites(siteIds);
        if (categories != null) replaceCategories(categories);
        if (slaTargets != null) replaceSlaTargets(slaTargets);
        this.updatedAt = Instant.now();
    }

    public void end() {
        this.status = "ENDED";
        if (this.endDate == null) this.endDate = LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"));
        this.updatedAt = Instant.now();
    }

    private void replaceSites(Set<UUID> sites) {
        this.siteIds.clear();
        if (sites != null) this.siteIds.addAll(sites);
    }

    private void replaceCategories(Set<String> categories) {
        if (categories == null || categories.isEmpty()) {
            this.coveredCategories = null;
            return;
        }
        Set<String> normalized = new TreeSet<>();
        for (String c : categories) {
            String key = c == null ? "" : c.trim().toUpperCase();
            if (!CATEGORIES.contains(key)) throw new IllegalArgumentException("Unknown category '" + c + "'. Valid: " + CATEGORIES);
            normalized.add(key);
        }
        this.coveredCategories = String.join(",", normalized);
    }

    private void replaceSlaTargets(List<FmSlaTarget> targets) {
        List<FmSlaTarget> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (FmSlaTarget t : targets == null ? List.<FmSlaTarget>of() : targets) {
            String priority = t.getPriority() == null ? "" : t.getPriority().trim().toUpperCase();
            String clock = t.getClock() == null ? "" : t.getClock().trim().toUpperCase();
            if (!PRIORITIES.contains(priority)) throw new IllegalArgumentException("SLA priority must be one of " + PRIORITIES);
            if (!seen.add(priority)) throw new IllegalArgumentException("Duplicate SLA target for priority " + priority);
            if (!FmSlaClock.isValidClock(clock)) throw new IllegalArgumentException("SLA clock must be 24X7 or BUSINESS_HOURS");
            if (t.getResponseMinutes() < 1) throw new IllegalArgumentException("responseMinutes must be at least 1");
            if (t.getResolutionMinutes() < t.getResponseMinutes())
                throw new IllegalArgumentException("resolutionMinutes cannot be less than responseMinutes (" + priority + ")");
            normalized.add(FmSlaTarget.of(priority, t.getResponseMinutes(), t.getResolutionMinutes(), clock));
        }
        this.slaTargets.clear();
        this.slaTargets.addAll(normalized);
    }

    private static BigDecimal requireNonNegative(BigDecimal v, String field) {
        if (v != null && v.signum() < 0) throw new IllegalArgumentException(field + " must not be negative");
        return v;
    }

    private static BigDecimal requireMarkup(BigDecimal v) {
        if (v != null && (v.signum() < 0 || v.compareTo(new BigDecimal("1000")) > 0))
            throw new IllegalArgumentException("partsMarkupPercent must be between 0 and 1000");
        return v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Set<String> categorySet() {
        if (coveredCategories == null || coveredCategories.isBlank()) return Set.of();
        return Arrays.stream(coveredCategories.split(",")).collect(Collectors.toCollection(TreeSet::new));
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public boolean isRetainer() { return "RETAINER".equals(billingType); }

    public boolean coversSite(UUID siteId) { return siteIds.isEmpty() || siteIds.contains(siteId); }

    public boolean coversCategory(String category) {
        return coveredCategories == null || coveredCategories.isBlank() || categorySet().contains(category);
    }

    /** Higher = more specific. Used to choose between overlapping agreements: site-specific beats all-sites, category-specific beats all-categories. */
    public int specificity() {
        return (siteIds.isEmpty() ? 0 : 2) + (categorySet().isEmpty() ? 0 : 1);
    }

    public Optional<FmSlaTarget> slaTargetFor(String priority) {
        return slaTargets.stream().filter(t -> t.getPriority().equals(priority)).findFirst();
    }

    /** Whether this agreement was in force for the given date. */
    public boolean coversDate(LocalDate date) {
        if (!isActive()) return false;
        if (date.isBefore(startDate)) return false;
        return endDate == null || !date.isAfter(endDate);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}