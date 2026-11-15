package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.AllocationRequest;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Which tanker and driver may serve a site and a product. These checks sit beside, and never replace, the licence, permit, loading site
 * licence and capacity checks that allocation already makes. Every limit is the company's or the customer's own, set by a person; nothing here
 * interprets a regulation. A company or site with nothing configured is not restricted, so existing companies see no change.
 *
 * A Block stops the allocation and cannot be overridden by anyone. A Review (information that is missing, so the check cannot be made) lets the
 * allocation go ahead only for a user holding FUEL_ELIGIBILITY_REVIEW who gives a reason; that is recorded in fuel_eligibility_overrides.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelEligibilityService {

    private final FuelSiteAccessRuleRepository siteRules;
    private final FuelSiteApprovedTankerRepository siteTankers;
    private final FuelCustomerSiteRepository sites;
    private final FuelTankerRepository tankers;
    private final FuelEligibilityOverrideRepository overrides;

    // ── Evaluation ───────────────────────────────────────────────────────────

    /** Read only. Everything that applies to putting this tanker and driver on this load, most serious first. */
    @Transactional(readOnly = true)
    public EligibilityResult evaluate(TenantId tenantId, FuelDelivery d, FuelTanker tanker, UUID driverEmployeeId, String driverLabel,
                                      Collection<FuelTankerCompartment> tankerCompartments) {
        List<Finding> out = new ArrayList<>();
        if (d.getSiteId() != null) {
            FuelCustomerSite site = sites.findActiveById(tenantId, d.getSiteId()).orElse(null);
            FuelSiteAccessRule rule = siteRules.findForSite(tenantId, d.getSiteId()).orElse(null);
            if (site != null && rule != null) {
                Set<UUID> listed = siteTankers.findForSite(tenantId, d.getSiteId()).stream().map(FuelSiteApprovedTanker::getTankerId).collect(Collectors.toSet());
                BigDecimal total = tankerCompartments.stream().map(FuelTankerCompartment::getCapacityLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
                out.addAll(siteTankerFindings(site.getName(), rule, listed, tanker, total));
            }
        }
        out.sort(Comparator.comparing(f -> f.severity().ordinal()));
        return new EligibilityResult(out);
    }

    /** Pure, so it can be tested without a database. */
    static List<Finding> siteTankerFindings(String siteName, FuelSiteAccessRule rule, Set<UUID> listedTankers, FuelTanker tanker, BigDecimal totalCapacity) {
        List<Finding> out = new ArrayList<>();
        String reg = tanker.getRegistration();
        if (!listedTankers.isEmpty() && !listedTankers.contains(tanker.getId())) {
            out.add(block("SITE_TANKER_NOT_LISTED", reg + " is not on " + siteName + "'s list of approved tankers."));
        }
        if (rule.getMaxCapacityLitres() != null && totalCapacity.compareTo(rule.getMaxCapacityLitres()) > 0) {
            out.add(block("SITE_TANKER_CAPACITY", reg + " carries " + n(totalCapacity) + " L in total; " + siteName + " allows at most " + n(rule.getMaxCapacityLitres()) + " L."));
        }
        if (rule.getMaxGvmKg() != null) {
            if (tanker.getGvmKg() == null) out.add(review("SITE_TANKER_GVM_UNKNOWN", siteName + " limits gross vehicle mass to " + rule.getMaxGvmKg() + " kg but " + reg + " has none recorded."));
            else if (tanker.getGvmKg() > rule.getMaxGvmKg()) out.add(block("SITE_TANKER_GVM", reg + " has a gross vehicle mass of " + tanker.getGvmKg() + " kg; " + siteName + " allows at most " + rule.getMaxGvmKg() + " kg."));
        }
        dimension(out, siteName, reg, "length", "SITE_TANKER_LENGTH", rule.getMaxLengthM(), tanker.getLengthM());
        dimension(out, siteName, reg, "width", "SITE_TANKER_WIDTH", rule.getMaxWidthM(), tanker.getWidthM());
        dimension(out, siteName, reg, "height", "SITE_TANKER_HEIGHT", rule.getMaxHeightM(), tanker.getHeightM());
        Set<String> types = rule.approvedTypeSet();
        if (!types.isEmpty()) {
            if (tanker.getTankerType() == null) out.add(review("SITE_TANKER_TYPE_UNKNOWN", siteName + " only accepts " + String.join(", ", types) + " tankers but the type of " + reg + " is not recorded."));
            else if (!types.contains(tanker.getTankerType())) out.add(block("SITE_TANKER_TYPE", reg + " is a " + tanker.getTankerType() + "; " + siteName + " only accepts " + String.join(", ", types) + "."));
        }
        if (rule.getAccessNotes() != null) out.add(new Finding("SITE_ACCESS_NOTES", Severity.INFO, "Access at " + siteName + ": " + rule.getAccessNotes()));
        return out;
    }

    private static void dimension(List<Finding> out, String site, String reg, String what, String code, BigDecimal max, BigDecimal actual) {
        if (max == null) return;
        if (actual == null) out.add(review(code + "_UNKNOWN", site + " limits tanker " + what + " to " + n(max) + " m but the " + what + " of " + reg + " is not recorded."));
        else if (actual.compareTo(max) > 0) out.add(block(code, reg + " is " + n(actual) + " m " + (what.equals("height") ? "high" : what.equals("width") ? "wide" : "long") + "; " + site + " allows at most " + n(max) + " m."));
    }

    static Finding block(String code, String message) { return new Finding(code, Severity.BLOCK, message); }
    static Finding review(String code, String message) { return new Finding(code, Severity.REVIEW, message); }
    static String n(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    // ── Enforcement ──────────────────────────────────────────────────────────

    /**
     * Stops the allocation on a Block. On a Review, allows it only for an authorised user who gave a reason, and records who and why.
     * There is no way to get past a Block.
     */
    @Transactional
    public void enforce(TenantId tenantId, FuelDelivery d, FuelTanker tanker, UUID driverEmployeeId, EligibilityResult result, String reviewReason, ReviewAuthority who) {
        List<Finding> blocks = result.blocks();
        if (!blocks.isEmpty()) {
            throw new IllegalStateException("This load cannot be allocated to " + tanker.getRegistration() + ": " + join(blocks));
        }
        List<Finding> reviews = result.reviews();
        if (reviews.isEmpty()) return;
        if (who == null || !who.canReview() || who.userId() == null) {
            throw new IllegalStateException("This allocation needs a review before it can go ahead: " + join(reviews)
                    + " Someone with the eligibility review permission must allocate it, with a reason.");
        }
        String reason = reviewReason == null ? "" : reviewReason.trim();
        if (reason.length() < 5) {
            throw new IllegalArgumentException("Give a reason of at least 5 characters to allocate this load with these review points: " + join(reviews));
        }
        overrides.save(FuelEligibilityOverride.of(tenantId, d.getId(), tanker.getId(), driverEmployeeId, join(reviews), reason, who.userId()));
        log.info("Load {} allocated to {} with eligibility review points acknowledged by {}", d.getId(), tanker.getRegistration(), who.userId());
    }

    private static String join(List<Finding> f) { return f.stream().map(Finding::message).collect(Collectors.joining(" ")); }

    // ── Site rules ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SiteRuleView siteRule(TenantId tenantId, UUID siteId) {
        requireSite(tenantId, siteId);
        return view(tenantId, siteId, siteRules.findForSite(tenantId, siteId).orElse(null));
    }

    /** Replaces the site's rule and its list of approved tankers. Empty everywhere means nothing is restricted. */
    @Transactional
    public SiteRuleView saveSiteRule(TenantId tenantId, UUID siteId, SiteRuleRequest req, UUID by) {
        requireSite(tenantId, siteId);
        Set<UUID> ids = req.approvedTankerIds() == null ? Set.of() : new LinkedHashSet<>(req.approvedTankerIds());
        for (UUID id : ids) tankers.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Tanker", id.toString()));
        FuelSiteAccessRule rule = siteRules.findForSite(tenantId, siteId).orElseGet(() -> FuelSiteAccessRule.forSite(tenantId, siteId));
        rule.update(req.maxCapacityLitres(), req.maxGvmKg(), req.maxLengthM(), req.maxWidthM(), req.maxHeightM(),
                req.approvedTypes() == null ? Set.of() : new LinkedHashSet<>(req.approvedTypes()), req.accessNotes(), by);
        siteRules.save(rule);
        siteTankers.deleteForSite(tenantId, siteId);
        siteTankers.saveAll(ids.stream().map(t -> FuelSiteApprovedTanker.of(tenantId, siteId, t)).toList());
        return view(tenantId, siteId, rule);
    }

    private SiteRuleView view(TenantId tenantId, UUID siteId, FuelSiteAccessRule r) {
        List<ApprovedTankerView> listed = siteTankers.findForSite(tenantId, siteId).stream()
                .map(a -> tankers.findActiveById(tenantId, a.getTankerId()).map(t -> new ApprovedTankerView(t.getId(), t.getRegistration())).orElse(null))
                .filter(Objects::nonNull).sorted(Comparator.comparing(ApprovedTankerView::registration)).toList();
        if (r == null) return new SiteRuleView(siteId, !listed.isEmpty(), null, null, null, null, null, List.of(), null, listed);
        return new SiteRuleView(siteId, true, r.getMaxCapacityLitres(), r.getMaxGvmKg(), r.getMaxLengthM(), r.getMaxWidthM(), r.getMaxHeightM(),
                List.copyOf(r.approvedTypeSet()), r.getAccessNotes(), listed);
    }

    private void requireSite(TenantId tenantId, UUID siteId) {
        sites.findActiveById(tenantId, siteId).orElseThrow(() -> new ResourceNotFoundException("Delivery site", siteId.toString()));
    }

    // ── Tanker specifications ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TankerSpecsView specs(TenantId tenantId, UUID tankerId) { return specsView(find(tenantId, tankerId)); }

    @Transactional
    public TankerSpecsView saveSpecs(TenantId tenantId, UUID tankerId, TankerSpecsRequest req) {
        FuelTanker t = find(tenantId, tankerId);
        t.updateSpecs(req.tankerType(), req.gvmKg(), req.lengthM(), req.widthM(), req.heightM());
        return specsView(tankers.save(t));
    }

    private FuelTanker find(TenantId tenantId, UUID id) {
        return tankers.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Tanker", id.toString()));
    }

    private static TankerSpecsView specsView(FuelTanker t) {
        return new TankerSpecsView(t.getId(), t.getRegistration(), t.getTankerType(), t.getGvmKg(), t.getLengthM(), t.getWidthM(), t.getHeightM());
    }
}
