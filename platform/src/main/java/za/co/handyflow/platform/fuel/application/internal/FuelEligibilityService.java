package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final FuelSiteDriverApprovalRepository driverApprovals;
    private final HrFacade hr;
    private final FuelCompartmentProductRepository compartmentProducts;
    private final FuelChangeoverRuleRepository changeovers;
    private final FuelCompartmentCleaningRepository cleanings;
    private final FuelDeliveryCompartmentRepository loadSplits;
    private final FuelTankerCompartmentRepository tankerCompartments;
    private final FuelSettingsRepository settings;

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    // ── Evaluation ───────────────────────────────────────────────────────────

    /** Read only. Everything that applies to putting this tanker and driver on this load, most serious first. */
    @Transactional(readOnly = true)
    public EligibilityResult evaluate(TenantId tenantId, FuelDelivery d, FuelTanker tanker, UUID driverEmployeeId, String driverLabel,
                                      Collection<FuelTankerCompartment> tankerCompartments) {
        return evaluate(tenantId, d, tanker, driverEmployeeId, driverLabel, tankerCompartments, List.of());
    }

    /** As above, and also checks each compartment the load would use (compartment and litres) against the product. No loads means no product check. */
    @Transactional(readOnly = true)
    public EligibilityResult evaluate(TenantId tenantId, FuelDelivery d, FuelTanker tanker, UUID driverEmployeeId, String driverLabel,
                                      Collection<FuelTankerCompartment> tankerCompartments, List<za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad> loads) {
        List<Finding> out = new ArrayList<>();
        LocalDate today = LocalDate.now(SAST);
        if (d.getSiteId() != null) {
            FuelCustomerSite site = sites.findActiveById(tenantId, d.getSiteId()).orElse(null);
            FuelSiteAccessRule rule = siteRules.findForSite(tenantId, d.getSiteId()).orElse(null);
            if (site != null && rule != null) {
                Set<UUID> listed = siteTankers.findForSite(tenantId, d.getSiteId()).stream().map(FuelSiteApprovedTanker::getTankerId).collect(Collectors.toSet());
                BigDecimal total = tankerCompartments.stream().map(FuelTankerCompartment::getCapacityLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
                out.addAll(siteTankerFindings(site.getName(), rule, listed, tanker, total));
            }
            // A suspension or restriction recorded at the site counts even when the site has no rule requiring approved drivers.
            if (site != null) {
                FuelSiteDriverApproval approval = driverApprovals.findForDriver(tenantId, d.getSiteId(), driverEmployeeId).orElse(null);
                out.addAll(siteDriverFindings(site.getName(), rule, approval, driverLabel, today));
            }
        }
        if (loads != null && !loads.isEmpty() && d.getFuelType() != null) out.addAll(productFindings(tenantId, d, tanker, tankerCompartments, loads));
        out.sort(Comparator.comparing(f -> f.severity().ordinal()));
        return new EligibilityResult(out);
    }

    /** Reads what each compartment is rated for, what it last carried and when it was last cleaned, then applies {@link #compartmentFindings}. */
    private List<Finding> productFindings(TenantId tenantId, FuelDelivery d, FuelTanker tanker, Collection<FuelTankerCompartment> comps,
                                          List<za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad> loads) {
        Map<UUID, FuelTankerCompartment> byId = comps.stream().collect(Collectors.toMap(FuelTankerCompartment::getId, c -> c, (a, b) -> a));
        Set<UUID> used = loads.stream().map(za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad::compartmentId).filter(byId::containsKey).collect(Collectors.toCollection(LinkedHashSet::new));
        if (used.isEmpty()) return List.of();
        Map<UUID, Set<String>> rated = new HashMap<>();
        compartmentProducts.findForCompartments(tenantId, used).forEach(p -> rated.computeIfAbsent(p.getCompartmentId(), k -> new TreeSet<>()).add(p.getFuelType()));
        Map<UUID, FuelDeliveryCompartmentRepository.PreviousCargo> previous = new HashMap<>();
        loadSplits.findPreviousCargo(tenantId, used, d.getId()).forEach(c -> previous.merge(c.compartmentId(), c, (a, b) -> a.at().isAfter(b.at()) ? a : b));
        Set<String> rules = changeovers.findAllForTenant(tenantId).stream().map(r -> r.getFromFuel() + ">" + r.getToFuel()).collect(Collectors.toSet());
        boolean requireRating = settings.findByTenant(tenantId).map(FuelSettings::isRequireCompartmentRating).orElse(false);
        String fuel = d.getFuelType().trim().toUpperCase();
        List<Finding> out = new ArrayList<>();
        for (UUID id : used) {
            FuelTankerCompartment c = byId.get(id);
            Instant lastCleaned = cleanings.findForCompartment(tenantId, id, org.springframework.data.domain.PageRequest.of(0, 1)).stream().map(FuelCompartmentCleaning::getCleanedAt).findFirst().orElse(null);
            out.addAll(compartmentFindings(tanker.getRegistration(), c.getNumber(), fuel, rated.getOrDefault(id, Set.of()), requireRating, previous.get(id), rules, lastCleaned));
        }
        return out;
    }

    /**
     * The product rules for one compartment. Not rated for the product is a Block. When the company has a changeover rule from what the compartment
     * last carried to this product, it is a Block until a cleaning is recorded after that cargo. When the last cargo is unknown and some rule leads
     * into this product, it is a Review, unless any cleaning has been recorded. An unrated compartment is a Review only when the company asks for
     * ratings. Pure, so it can be tested without a database.
     */
    static List<Finding> compartmentFindings(String reg, int number, String fuel, Set<String> rated, boolean requireRating,
                                             FuelDeliveryCompartmentRepository.PreviousCargo previous, Set<String> changeoverRules, Instant lastCleaned) {
        List<Finding> out = new ArrayList<>();
        String where = "Compartment " + number + " of " + reg;
        if (!rated.isEmpty() && !rated.contains(fuel)) {
            out.add(block("COMPARTMENT_PRODUCT_NOT_RATED", where + " is not rated for " + fuel + " (rated for " + String.join(", ", rated) + ")."));
        } else if (rated.isEmpty() && requireRating) {
            out.add(review("COMPARTMENT_UNRATED", where + " has no product rating recorded."));
        }
        if (previous != null) {
            if (!previous.fuelType().equalsIgnoreCase(fuel) && changeoverRules.contains(previous.fuelType().toUpperCase() + ">" + fuel)
                    && (lastCleaned == null || !lastCleaned.isAfter(previous.at()))) {
                out.add(block("COMPARTMENT_CLEANING_REQUIRED", where + " last carried " + previous.fuelType() + "; going to " + fuel
                        + " needs a cleaning recorded after that load, with its certificate or evidence."));
            }
        } else if (lastCleaned == null && changeoverRules.stream().anyMatch(r -> r.endsWith(">" + fuel))) {
            out.add(review("COMPARTMENT_PREVIOUS_CARGO_UNKNOWN", where + " has no recorded previous load or cleaning, and " + fuel + " has a changeover rule."));
        }
        return out;
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

    /**
     * The driver's standing at the site. A suspension blocks always; a restriction is a review; the other checks apply only when the site's
     * rule requires approved drivers. A driver who is not inducted, or whose induction or required training has expired, is blocked, and
     * the site's own dates are only compared with today. A missing expiry date is a review. Pure, so it can be tested without a database.
     */
    static List<Finding> siteDriverFindings(String siteName, FuelSiteAccessRule rule, FuelSiteDriverApproval a, String driver, LocalDate today) {
        List<Finding> out = new ArrayList<>();
        boolean required = rule != null && rule.isRequireApprovedDrivers();
        if (a == null) {
            if (required) out.add(block("SITE_DRIVER_NOT_APPROVED", driver + " is not on " + siteName + "'s list of approved drivers, so has not been inducted there."));
            return out;
        }
        if (FuelSiteDriverApproval.SUSPENDED.equals(a.getStatus())) {
            out.add(block("SITE_DRIVER_SUSPENDED", driver + " is suspended at " + siteName + ": " + a.getConditions()));
            return out;
        }
        if (FuelSiteDriverApproval.RESTRICTED.equals(a.getStatus())) {
            out.add(review("SITE_DRIVER_RESTRICTED", driver + " is restricted at " + siteName + ": " + a.getConditions()));
        }
        if (required) {
            if (a.getInductionExpiry() == null) out.add(review("SITE_DRIVER_INDUCTION_NO_EXPIRY", "The induction of " + driver + " at " + siteName + " has no expiry date recorded."));
            else if (a.getInductionExpiry().isBefore(today)) out.add(block("SITE_DRIVER_INDUCTION_EXPIRED", "The induction of " + driver + " at " + siteName + " expired on " + a.getInductionExpiry() + "."));
            String training = rule.getRequiredTraining();
            if (training != null) {
                if (a.getTrainingRef() == null) out.add(block("SITE_DRIVER_TRAINING_MISSING", siteName + " requires " + training + " and none is recorded for " + driver + "."));
                else if (a.getTrainingExpiry() == null) out.add(review("SITE_DRIVER_TRAINING_NO_EXPIRY", "The " + training + " of " + driver + " has no expiry date recorded."));
                else if (a.getTrainingExpiry().isBefore(today)) out.add(block("SITE_DRIVER_TRAINING_EXPIRED", "The " + training + " of " + driver + " expired on " + a.getTrainingExpiry() + "."));
            }
        }
        if (FuelSiteDriverApproval.APPROVED.equals(a.getStatus()) && a.getConditions() != null) {
            out.add(new Finding("SITE_DRIVER_CONDITIONS", Severity.INFO, "Conditions at " + siteName + " for " + driver + ": " + a.getConditions()));
        }
        return out;
    }

    /** What the check would say about an approval today, in one word, for the approvals list. */
    static String approvalState(FuelSiteAccessRule rule, FuelSiteDriverApproval a, LocalDate today) {
        if (FuelSiteDriverApproval.SUSPENDED.equals(a.getStatus())) return "SUSPENDED";
        boolean required = rule != null && rule.isRequireApprovedDrivers();
        if (required) {
            if (a.getInductionExpiry() != null && a.getInductionExpiry().isBefore(today)) return "EXPIRED";
            String training = rule.getRequiredTraining();
            if (training != null) {
                if (a.getTrainingRef() == null) return "TRAINING_MISSING";
                if (a.getTrainingExpiry() != null && a.getTrainingExpiry().isBefore(today)) return "TRAINING_EXPIRED";
                if (a.getTrainingExpiry() == null) return "TRAINING_NO_EXPIRY";
            }
            if (a.getInductionExpiry() == null) return "NO_EXPIRY";
        }
        return FuelSiteDriverApproval.RESTRICTED.equals(a.getStatus()) ? "RESTRICTED" : "OK";
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
                req.approvedTypes() == null ? Set.of() : new LinkedHashSet<>(req.approvedTypes()), req.accessNotes(),
                req.requireApprovedDrivers(), req.requiredTraining(), by);
        siteRules.save(rule);
        siteTankers.deleteForSite(tenantId, siteId);
        siteTankers.saveAll(ids.stream().map(t -> FuelSiteApprovedTanker.of(tenantId, siteId, t)).toList());
        return view(tenantId, siteId, rule);
    }

    private SiteRuleView view(TenantId tenantId, UUID siteId, FuelSiteAccessRule r) {
        List<ApprovedTankerView> listed = siteTankers.findForSite(tenantId, siteId).stream()
                .map(a -> tankers.findActiveById(tenantId, a.getTankerId()).map(t -> new ApprovedTankerView(t.getId(), t.getRegistration())).orElse(null))
                .filter(Objects::nonNull).sorted(Comparator.comparing(ApprovedTankerView::registration)).toList();
        if (r == null) return new SiteRuleView(siteId, !listed.isEmpty(), null, null, null, null, null, List.of(), null, listed, false, null);
        return new SiteRuleView(siteId, true, r.getMaxCapacityLitres(), r.getMaxGvmKg(), r.getMaxLengthM(), r.getMaxWidthM(), r.getMaxHeightM(),
                List.copyOf(r.approvedTypeSet()), r.getAccessNotes(), listed, r.isRequireApprovedDrivers(), r.getRequiredTraining());
    }

    private void requireSite(TenantId tenantId, UUID siteId) {
        sites.findActiveById(tenantId, siteId).orElseThrow(() -> new ResourceNotFoundException("Delivery site", siteId.toString()));
    }

    // ── Driver approvals ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DriverApprovalView> driverApprovals(TenantId tenantId, UUID siteId) {
        requireSite(tenantId, siteId);
        FuelSiteAccessRule rule = siteRules.findForSite(tenantId, siteId).orElse(null);
        LocalDate today = LocalDate.now(SAST);
        return driverApprovals.findForSite(tenantId, siteId).stream()
                .map(a -> approvalView(tenantId, rule, a, today))
                .sorted(Comparator.comparing((DriverApprovalView v) -> v.driverName() == null ? "" : v.driverName())).toList();
    }

    /** Adds or replaces a driver's standing at the site. The driver must be an employee who is still employed. */
    @Transactional
    public DriverApprovalView saveDriverApproval(TenantId tenantId, UUID siteId, UUID employeeId, DriverApprovalRequest req, UUID by) {
        requireSite(tenantId, siteId);
        EmployeeSummary e = hr.findEmployeeById(tenantId, employeeId).orElseThrow(() -> new ResourceNotFoundException("Driver", employeeId.toString()));
        if (e.status() != null && e.status().toUpperCase().startsWith("TERMINATED")) throw new IllegalStateException(e.fullName() + " is no longer employed.");
        FuelSiteDriverApproval a = driverApprovals.findForDriver(tenantId, siteId, employeeId).orElseGet(() -> FuelSiteDriverApproval.forDriver(tenantId, siteId, employeeId));
        a.update(req.status(), req.inductionDate(), req.inductionExpiry(), req.trainingRef(), req.trainingExpiry(), req.conditions(), by);
        driverApprovals.save(a);
        return approvalView(tenantId, siteRules.findForSite(tenantId, siteId).orElse(null), a, LocalDate.now(SAST));
    }

    @Transactional
    public void removeDriverApproval(TenantId tenantId, UUID siteId, UUID employeeId) {
        requireSite(tenantId, siteId);
        FuelSiteDriverApproval a = driverApprovals.findForDriver(tenantId, siteId, employeeId).orElseThrow(() -> new ResourceNotFoundException("Driver approval", employeeId.toString()));
        driverApprovals.delete(a);
    }

    private DriverApprovalView approvalView(TenantId tenantId, FuelSiteAccessRule rule, FuelSiteDriverApproval a, LocalDate today) {
        String name = hr.findEmployeeById(tenantId, a.getEmployeeId()).map(EmployeeSummary::fullName).orElse("(employee removed)");
        return new DriverApprovalView(a.getEmployeeId(), name, a.getStatus(), a.getInductionDate(), a.getInductionExpiry(), a.getTrainingRef(), a.getTrainingExpiry(),
                a.getConditions(), approvalState(rule, a, today));
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

    // ── Compartment ratings, changeover rules, cleanings ─────────────────────

    @Transactional(readOnly = true)
    public List<CompartmentRatingView> compartmentRatings(TenantId tenantId, UUID tankerId) {
        find(tenantId, tankerId);
        List<FuelTankerCompartment> comps = tankerCompartments.findByTanker(tenantId, tankerId);
        if (comps.isEmpty()) return List.of();
        Set<UUID> ids = comps.stream().map(FuelTankerCompartment::getId).collect(Collectors.toSet());
        Map<UUID, List<String>> rated = new HashMap<>();
        compartmentProducts.findForCompartments(tenantId, ids).forEach(p -> rated.computeIfAbsent(p.getCompartmentId(), k -> new ArrayList<>()).add(p.getFuelType()));
        Map<UUID, FuelDeliveryCompartmentRepository.PreviousCargo> previous = new HashMap<>();
        loadSplits.findPreviousCargo(tenantId, ids, new UUID(0, 0)).forEach(c -> previous.merge(c.compartmentId(), c, (a, b) -> a.at().isAfter(b.at()) ? a : b));
        return comps.stream().map(c -> {
            var prev = previous.get(c.getId());
            Instant cleaned = cleanings.findForCompartment(tenantId, c.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).stream().map(FuelCompartmentCleaning::getCleanedAt).findFirst().orElse(null);
            List<String> types = rated.getOrDefault(c.getId(), List.of()).stream().sorted().toList();
            return new CompartmentRatingView(c.getId(), c.getNumber(), c.getCapacityLitres(), types, prev == null ? null : prev.fuelType(), prev == null ? null : prev.at(), cleaned);
        }).toList();
    }

    /** Replaces the products a compartment is rated for. Empty removes the restriction. */
    @Transactional
    public CompartmentRatingView saveCompartmentRating(TenantId tenantId, UUID tankerId, UUID compartmentId, CompartmentRatingRequest req) {
        requireCompartment(tenantId, tankerId, compartmentId);
        Set<String> types = new TreeSet<>();
        if (req.fuelTypes() != null) req.fuelTypes().forEach(t -> types.add(FuelCompartmentProduct.normalise(t)));
        compartmentProducts.deleteForCompartment(tenantId, compartmentId);
        compartmentProducts.saveAll(types.stream().map(t -> FuelCompartmentProduct.of(tenantId, compartmentId, t)).toList());
        return compartmentRatings(tenantId, tankerId).stream().filter(v -> v.compartmentId().equals(compartmentId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ChangeoverRuleView> changeoverRules(TenantId tenantId) {
        return changeovers.findAllForTenant(tenantId).stream().map(r -> new ChangeoverRuleView(r.getId(), r.getFromFuel(), r.getToFuel(), r.getNote())).toList();
    }

    @Transactional
    public ChangeoverRuleView addChangeoverRule(TenantId tenantId, ChangeoverRuleRequest req) {
        FuelChangeoverRule rule = FuelChangeoverRule.of(tenantId, req.fromFuel(), req.toFuel(), req.note());
        if (changeovers.findAllForTenant(tenantId).stream().anyMatch(r -> r.getFromFuel().equals(rule.getFromFuel()) && r.getToFuel().equals(rule.getToFuel()))) {
            throw new IllegalStateException("There is already a changeover rule from " + rule.getFromFuel() + " to " + rule.getToFuel() + ".");
        }
        FuelChangeoverRule saved = changeovers.save(rule);
        return new ChangeoverRuleView(saved.getId(), saved.getFromFuel(), saved.getToFuel(), saved.getNote());
    }

    @Transactional
    public void removeChangeoverRule(TenantId tenantId, UUID id) {
        FuelChangeoverRule r = changeovers.findById(id).filter(x -> x.getTenantId().equals(tenantId)).orElseThrow(() -> new ResourceNotFoundException("Changeover rule", id.toString()));
        changeovers.delete(r);
    }

    @Transactional(readOnly = true)
    public List<CleaningView> cleanings(TenantId tenantId, UUID tankerId, UUID compartmentId) {
        requireCompartment(tenantId, tankerId, compartmentId);
        return cleanings.findForCompartment(tenantId, compartmentId, org.springframework.data.domain.PageRequest.of(0, 50)).stream().map(FuelEligibilityService::cleaningView).toList();
    }

    @Transactional
    public CleaningView recordCleaning(TenantId tenantId, UUID tankerId, UUID compartmentId, CleaningRequest req, UUID by) {
        requireCompartment(tenantId, tankerId, compartmentId);
        FuelCompartmentCleaning c = FuelCompartmentCleaning.record(tenantId, compartmentId, req.cleanedAt(), req.method(), req.certificateRef(), req.evidenceNote(), by, Instant.now());
        return cleaningView(cleanings.save(c));
    }

    private static CleaningView cleaningView(FuelCompartmentCleaning c) {
        return new CleaningView(c.getId(), c.getCompartmentId(), c.getCleanedAt(), c.getMethod(), c.getCertificateRef(), c.getEvidenceNote(), c.getRecordedBy());
    }

    private void requireCompartment(TenantId tenantId, UUID tankerId, UUID compartmentId) {
        find(tenantId, tankerId);
        if (tankerCompartments.findByTanker(tenantId, tankerId).stream().noneMatch(c -> c.getId().equals(compartmentId))) {
            throw new ResourceNotFoundException("Compartment", compartmentId.toString());
        }
    }

    @Transactional(readOnly = true)
    public EligibilitySettings eligibilitySettings(TenantId tenantId) {
        return new EligibilitySettings(settings.findByTenant(tenantId).map(FuelSettings::isRequireCompartmentRating).orElse(false));
    }

    @Transactional
    public EligibilitySettings saveEligibilitySettings(TenantId tenantId, EligibilitySettings req) {
        FuelSettings s = settings.findByTenant(tenantId).orElseGet(() -> FuelSettings.forTenant(tenantId));
        s.updateRequireCompartmentRating(req.requireCompartmentRating());
        return new EligibilitySettings(settings.save(s).isRequireCompartmentRating());
    }
}
