package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Eligibility: site tanker rules. Blocks cannot be overridden; Reviews need an authorised user and a reason, and are recorded. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelEligibilityServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelSiteAccessRuleRepository siteRules;
    @Mock FuelSiteApprovedTankerRepository siteTankers;
    @Mock FuelCustomerSiteRepository sites;
    @Mock FuelTankerRepository tankers;
    @Mock FuelEligibilityOverrideRepository overrides;
    @Mock FuelSiteDriverApprovalRepository driverApprovals;
    @Mock HrFacade hr;
    @Mock FuelCompartmentProductRepository compartmentProducts;
    @Mock FuelChangeoverRuleRepository changeovers;
    @Mock FuelCompartmentCleaningRepository cleanings;
    @Mock FuelDeliveryCompartmentRepository loadSplits;
    @Mock FuelTankerCompartmentRepository tankerCompartments;
    @Mock FuelSettingsRepository settings;
    @InjectMocks FuelEligibilityService service;

    FuelTanker tanker; FuelCustomerSite site; FuelSiteAccessRule rule; FuelDelivery load; FuelTankerCompartment c1, c2;

    @BeforeEach
    void setUp() {
        tanker = FuelTanker.create(T, "gp 12 ab", "Big Blue");
        c1 = FuelTankerCompartment.create(T, tanker.getId(), 1, bd("10000"));
        c2 = FuelTankerCompartment.create(T, tanker.getId(), 2, bd("8000"));   // 18,000 L in total
        site = FuelCustomerSite.create(T, UUID.randomUUID(), "Mine Gate 2", null, null, null, null, null);
        rule = FuelSiteAccessRule.forSite(T, site.getId());
        load = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", bd("12000"), bd("22"), Instant.now(), null, null);
        load.assignSite(site.getId());
        when(sites.findActiveById(T, site.getId())).thenReturn(Optional.of(site));
        when(siteRules.findForSite(T, site.getId())).thenReturn(Optional.of(rule));
        when(siteTankers.findForSite(T, site.getId())).thenReturn(List.of());
        when(tankers.findActiveById(T, tanker.getId())).thenReturn(Optional.of(tanker));
        when(tankers.save(any())).thenAnswer(i -> i.getArgument(0));
        when(siteRules.save(any())).thenAnswer(i -> i.getArgument(0));
        when(driverApprovals.findForDriver(any(), any(), any())).thenReturn(Optional.empty());
        when(driverApprovals.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static void rule(FuelSiteAccessRule r, String cap, Integer gvm, String len, String wid, String hgt, Set<String> types, String notes) {
        r.update(cap == null ? null : bd(cap), gvm, len == null ? null : bd(len), wid == null ? null : bd(wid), hgt == null ? null : bd(hgt), types, notes, false, null, USER);
    }
    private EligibilityResult eval() { return service.evaluate(T, load, tanker, UUID.randomUUID(), "Moses", List.of(c1, c2)); }
    private static Set<String> codes(List<Finding> f) { var s = new HashSet<String>(); f.forEach(x -> s.add(x.code())); return s; }

    @Test @DisplayName("a site with no rule restricts nothing, and neither does a load with no site")
    void unrestricted() {
        when(siteRules.findForSite(T, site.getId())).thenReturn(Optional.empty());
        assertTrue(eval().findings().isEmpty());
        load.assignSite(null);
        assertTrue(eval().findings().isEmpty());
    }

    @Test @DisplayName("an empty rule restricts nothing")
    void emptyRule() { assertTrue(eval().findings().isEmpty()); }

    @Test @DisplayName("the mine example: a site that allows at most 18,000 L blocks a 20,000 L tanker, and lets an 18,000 L one in")
    void capacityLimit() {
        rule(rule, "18000", null, null, null, null, Set.of(), null);
        assertTrue(eval().allowed());
        c2 = FuelTankerCompartment.create(T, tanker.getId(), 2, bd("10000"));
        var r = service.evaluate(T, load, tanker, UUID.randomUUID(), "Moses", List.of(c1, c2));
        assertFalse(r.allowed());
        assertEquals(Set.of("SITE_TANKER_CAPACITY"), codes(r.blocks()));
        assertTrue(r.blocks().get(0).message().contains("20000") && r.blocks().get(0).message().contains("18000"));
    }

    @Test @DisplayName("when a site lists tankers only those may serve it")
    void approvedList() {
        rule(rule, null, null, null, null, null, Set.of(), null);
        var other = FuelSiteApprovedTanker.of(T, site.getId(), UUID.randomUUID());
        when(siteTankers.findForSite(T, site.getId())).thenReturn(List.of(other));
        assertEquals(Set.of("SITE_TANKER_NOT_LISTED"), codes(eval().blocks()));
        when(siteTankers.findForSite(T, site.getId())).thenReturn(List.of(FuelSiteApprovedTanker.of(T, site.getId(), tanker.getId())));
        assertTrue(eval().findings().isEmpty());
    }

    @Test @DisplayName("gross vehicle mass: over the limit blocks, not recorded is a review, within the limit is fine")
    void gvm() {
        rule(rule, null, 30000, null, null, null, Set.of(), null);
        assertEquals(Set.of("SITE_TANKER_GVM_UNKNOWN"), codes(eval().reviews()));
        assertTrue(eval().allowed());
        tanker.updateSpecs(null, 32000, null, null, null);
        assertEquals(Set.of("SITE_TANKER_GVM"), codes(eval().blocks()));
        tanker.updateSpecs(null, 28000, null, null, null);
        assertTrue(eval().findings().isEmpty());
    }

    @Test @DisplayName("dimensions: each limit blocks when exceeded and asks for a review when the tanker's size is not recorded")
    void dimensions() {
        rule(rule, null, null, "12.5", "2.6", "4.3", Set.of(), null);
        assertEquals(Set.of("SITE_TANKER_LENGTH_UNKNOWN", "SITE_TANKER_WIDTH_UNKNOWN", "SITE_TANKER_HEIGHT_UNKNOWN"), codes(eval().reviews()));
        tanker.updateSpecs(null, null, bd("13.2"), bd("2.5"), bd("4.4"));
        assertEquals(Set.of("SITE_TANKER_LENGTH", "SITE_TANKER_HEIGHT"), codes(eval().blocks()));
        assertTrue(eval().reviews().isEmpty());
    }

    @Test @DisplayName("tanker types: not in the site's list blocks, no type recorded is a review, a listed type is fine; case is ignored")
    void types() {
        rule(rule, null, null, null, null, null, Set.of("rigid"), null);
        assertEquals(Set.of("SITE_TANKER_TYPE_UNKNOWN"), codes(eval().reviews()));
        tanker.updateSpecs("articulated", null, null, null, null);
        assertEquals(Set.of("SITE_TANKER_TYPE"), codes(eval().blocks()));
        tanker.updateSpecs(" Rigid ", null, null, null, null);
        assertTrue(eval().findings().isEmpty());
    }

    @Test @DisplayName("access notes are shown as information and never block")
    void notes() {
        rule(rule, null, null, null, null, null, Set.of(), "Report to the weighbridge first");
        var r = eval();
        assertTrue(r.allowed()); assertTrue(r.reviews().isEmpty());
        assertEquals("SITE_ACCESS_NOTES", r.info().get(0).code());
    }

    @Test @DisplayName("blocks come before reviews in the list")
    void ordering() {
        rule(rule, "5000", 30000, null, null, null, Set.of(), null);
        var f = eval().findings();
        assertEquals(Severity.BLOCK, f.get(0).severity()); assertEquals(Severity.REVIEW, f.get(1).severity());
    }

    // ── Enforcement ──────────────────────────────────────────────────────────

    private EligibilityResult one(Severity s) { return new EligibilityResult(List.of(new Finding("X", s, "Something " + s))); }

    @Test @DisplayName("a block stops the allocation for everyone, including a user who can review, and records nothing")
    void blockCannotBeOverridden() {
        var e = assertThrows(IllegalStateException.class, () -> service.enforce(T, load, tanker, UUID.randomUUID(), one(Severity.BLOCK), "I insist, boss said so", new ReviewAuthority(true, USER)));
        assertTrue(e.getMessage().contains("Something BLOCK"));
        verifyNoInteractions(overrides);
    }

    @Test @DisplayName("a review without the permission is refused")
    void reviewNeedsPermission() {
        assertThrows(IllegalStateException.class, () -> service.enforce(T, load, tanker, UUID.randomUUID(), one(Severity.REVIEW), "Checked by phone", ReviewAuthority.NONE));
        assertThrows(IllegalStateException.class, () -> service.enforce(T, load, tanker, UUID.randomUUID(), one(Severity.REVIEW), "Checked by phone", new ReviewAuthority(false, USER)));
        verifyNoInteractions(overrides);
    }

    @Test @DisplayName("a review with the permission still needs a reason of at least 5 characters")
    void reviewNeedsReason() {
        var who = new ReviewAuthority(true, USER);
        assertThrows(IllegalArgumentException.class, () -> service.enforce(T, load, tanker, UUID.randomUUID(), one(Severity.REVIEW), null, who));
        assertThrows(IllegalArgumentException.class, () -> service.enforce(T, load, tanker, UUID.randomUUID(), one(Severity.REVIEW), "  ok ", who));
        verifyNoInteractions(overrides);
    }

    @Test @DisplayName("an authorised review with a reason goes ahead and is recorded with who, why and what was open")
    void reviewRecorded() {
        UUID driver = UUID.randomUUID();
        service.enforce(T, load, tanker, driver, one(Severity.REVIEW), "  Confirmed the weight by phone  ", new ReviewAuthority(true, USER));
        var cap = org.mockito.ArgumentCaptor.forClass(FuelEligibilityOverride.class);
        verify(overrides).save(cap.capture());
        var o = cap.getValue();
        assertEquals(USER, o.getApprovedBy()); assertEquals("Confirmed the weight by phone", o.getReason());
        assertEquals(load.getId(), o.getDeliveryId()); assertEquals(tanker.getId(), o.getTankerId()); assertEquals(driver, o.getDriverEmployeeId());
        assertTrue(o.getFindings().contains("Something REVIEW"));
    }

    @Test @DisplayName("information alone, or nothing, goes ahead with nothing recorded")
    void cleanGoesAhead() {
        service.enforce(T, load, tanker, UUID.randomUUID(), one(Severity.INFO), null, ReviewAuthority.NONE);
        service.enforce(T, load, tanker, UUID.randomUUID(), EligibilityResult.NONE, null, ReviewAuthority.NONE);
        verifyNoInteractions(overrides);
    }

    // ── Site rule set-up and tanker specifications ───────────────────────────

    @Test @DisplayName("saving a site rule replaces its limits and its list of approved tankers")
    void saveRule() {
        var v = service.saveSiteRule(T, site.getId(), new SiteRuleRequest(bd("18000"), 30000, bd("12"), null, null, List.of(" rigid ", "", "Super-link"), "Gate 2", List.of(tanker.getId()), true, " Mine induction "), USER);
        assertEquals(0, bd("18000").compareTo(v.maxCapacityLitres()));
        assertEquals(List.of("RIGID", "SUPER-LINK"), v.approvedTypes());
        assertTrue(v.requireApprovedDrivers()); assertEquals("Mine induction", v.requiredTraining());
        verify(siteTankers).deleteForSite(T, site.getId());
        verify(siteTankers).saveAll(argThat(list -> ((Collection<?>) list).size() == 1));
    }

    @Test @DisplayName("a rule naming an unknown tanker, or a limit of zero or less, is refused and nothing is saved")
    void ruleValidation() {
        assertThrows(ResourceNotFoundException.class, () -> service.saveSiteRule(T, site.getId(), new SiteRuleRequest(null, null, null, null, null, null, null, List.of(UUID.randomUUID()), false, null), USER));
        assertThrows(IllegalArgumentException.class, () -> service.saveSiteRule(T, site.getId(), new SiteRuleRequest(bd("0"), null, null, null, null, null, null, null, false, null), USER));
        assertThrows(IllegalArgumentException.class, () -> service.saveSiteRule(T, site.getId(), new SiteRuleRequest(null, -5, null, null, null, null, null, null, false, null), USER));
        verify(siteRules, never()).save(any());
    }

    @Test @DisplayName("an unknown site is not found")
    void unknownSite() {
        when(sites.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.siteRule(T, UUID.randomUUID()));
    }

    @Test @DisplayName("tanker specifications are saved upper-cased, can be cleared, and must be positive")
    void specs() {
        var v = service.saveSpecs(T, tanker.getId(), new TankerSpecsRequest(" articulated ", 36000, bd("17.5"), bd("2.5"), bd("4.2")));
        assertEquals("ARTICULATED", v.tankerType()); assertEquals(36000, v.gvmKg());
        var cleared = service.saveSpecs(T, tanker.getId(), new TankerSpecsRequest(null, null, null, null, null));
        assertNull(cleared.tankerType()); assertNull(cleared.gvmKg());
        assertThrows(IllegalArgumentException.class, () -> service.saveSpecs(T, tanker.getId(), new TankerSpecsRequest(null, 0, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.saveSpecs(T, tanker.getId(), new TankerSpecsRequest(null, null, bd("-1"), null, null)));
    }

    // ── Step 2: site driver approvals ────────────────────────────────────────

    static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"));
    final UUID driverId = UUID.randomUUID();

    private FuelSiteAccessRule requireDrivers(String training) {
        rule.update(null, null, null, null, null, Set.of(), null, true, training, USER);
        return rule;
    }
    private FuelSiteDriverApproval approval(String status, LocalDate inducted, LocalDate expires, String trainingRef, LocalDate trainingExpires, String conditions) {
        var a = FuelSiteDriverApproval.forDriver(T, site.getId(), driverId);
        a.update(status, inducted, expires, trainingRef, trainingExpires, conditions, USER);
        return a;
    }
    private List<Finding> driver(FuelSiteDriverApproval a) { return FuelEligibilityService.siteDriverFindings("Mine Gate 2", rule, a, "Moses", TODAY); }

    @Test @DisplayName("a site that does not require approved drivers asks nothing of a driver with no record")
    void driversNotRequired() { assertTrue(driver(null).isEmpty()); }

    @Test @DisplayName("a site that requires approved drivers blocks a driver who is not on its list")
    void notOnList() {
        requireDrivers(null);
        assertEquals(Set.of("SITE_DRIVER_NOT_APPROVED"), codes(driver(null)));
        assertEquals(Severity.BLOCK, driver(null).get(0).severity());
    }

    @Test @DisplayName("a valid induction is clear; an expired one blocks; no expiry date recorded is a review")
    void inductionExpiry() {
        requireDrivers(null);
        assertTrue(driver(approval("APPROVED", TODAY.minusDays(30), TODAY.plusDays(60), null, null, null)).isEmpty());
        assertTrue(driver(approval("APPROVED", TODAY.minusDays(30), TODAY, null, null, null)).isEmpty(), "valid through its expiry date");
        assertEquals(Set.of("SITE_DRIVER_INDUCTION_EXPIRED"), codes(driver(approval("APPROVED", TODAY.minusDays(400), TODAY.minusDays(1), null, null, null))));
        var r = driver(approval("APPROVED", TODAY.minusDays(30), null, null, null, null));
        assertEquals(Set.of("SITE_DRIVER_INDUCTION_NO_EXPIRY"), codes(r)); assertEquals(Severity.REVIEW, r.get(0).severity());
    }

    @Test @DisplayName("required training: none recorded blocks, expired blocks, no expiry is a review, valid is clear")
    void training() {
        requireDrivers("Mine safety induction");
        var base = TODAY.minusDays(30);
        assertEquals(Set.of("SITE_DRIVER_TRAINING_MISSING"), codes(driver(approval("APPROVED", base, TODAY.plusDays(60), null, null, null))));
        assertEquals(Set.of("SITE_DRIVER_TRAINING_EXPIRED"), codes(driver(approval("APPROVED", base, TODAY.plusDays(60), "CERT-9", TODAY.minusDays(2), null))));
        assertEquals(Set.of("SITE_DRIVER_TRAINING_NO_EXPIRY"), codes(driver(approval("APPROVED", base, TODAY.plusDays(60), "CERT-9", null, null))));
        assertTrue(driver(approval("APPROVED", base, TODAY.plusDays(60), "CERT-9", TODAY.plusDays(200), null)).isEmpty());
    }

    @Test @DisplayName("a suspension blocks even when the site does not require approved drivers, and says why")
    void suspension() {
        var r = driver(approval("SUSPENDED", null, null, null, null, "Safety breach on 3 Nov"));
        assertEquals(Set.of("SITE_DRIVER_SUSPENDED"), codes(r));
        assertTrue(r.get(0).message().contains("Safety breach on 3 Nov"));
        assertEquals(1, r.size(), "a suspension is the only thing said about the driver");
    }

    @Test @DisplayName("a restricted driver is a review that shows the restriction, and approved conditions are information only")
    void restrictedAndConditions() {
        var r = driver(approval("RESTRICTED", TODAY.minusDays(5), TODAY.plusDays(5), null, null, "Daylight deliveries only"));
        assertEquals(Set.of("SITE_DRIVER_RESTRICTED"), codes(r)); assertTrue(r.get(0).message().contains("Daylight deliveries only"));
        var c = driver(approval("APPROVED", TODAY.minusDays(5), TODAY.plusDays(5), null, null, "Escort from gate"));
        assertEquals(Set.of("SITE_DRIVER_CONDITIONS"), codes(c)); assertEquals(Severity.INFO, c.get(0).severity());
    }

    @Test @DisplayName("the approval's own rules: status, induction date, restriction and suspension reasons")
    void approvalValidation() {
        var a = FuelSiteDriverApproval.forDriver(T, site.getId(), driverId);
        assertThrows(IllegalArgumentException.class, () -> a.update("MAYBE", TODAY, null, null, null, null, USER));
        assertThrows(IllegalArgumentException.class, () -> a.update("APPROVED", null, null, null, null, null, USER));
        assertThrows(IllegalArgumentException.class, () -> a.update("APPROVED", TODAY, TODAY.minusDays(1), null, null, null, USER));
        assertThrows(IllegalArgumentException.class, () -> a.update("RESTRICTED", TODAY, null, null, null, " ", USER));
        assertThrows(IllegalArgumentException.class, () -> a.update("SUSPENDED", null, null, null, null, null, USER));
        a.update("SUSPENDED", null, null, null, null, "Breach", USER);
        assertEquals("SUSPENDED", a.getStatus());
    }

    @Test @DisplayName("evaluate includes the driver's findings for the load's site, and a suspension counts even when the site has no rule")
    void evaluateIncludesDriver() {
        when(siteRules.findForSite(T, site.getId())).thenReturn(Optional.empty());
        var suspended = approval("SUSPENDED", null, null, null, null, "Breach");
        when(driverApprovals.findForDriver(T, site.getId(), driverId)).thenReturn(Optional.of(suspended));
        var r = service.evaluate(T, load, tanker, driverId, "Moses", List.of(c1, c2));
        assertEquals(Set.of("SITE_DRIVER_SUSPENDED"), codes(r.blocks()));
    }

    @Test @DisplayName("the list says what each approval would run into today")
    void approvalList() {
        requireDrivers("Mine safety induction");
        var ok = approval("APPROVED", TODAY.minusDays(5), TODAY.plusDays(90), "C1", TODAY.plusDays(90), null);
        var late = FuelSiteDriverApproval.forDriver(T, site.getId(), UUID.randomUUID());
        late.update("APPROVED", TODAY.minusDays(400), TODAY.minusDays(1), "C1", TODAY.plusDays(90), null, USER);
        when(driverApprovals.findForSite(T, site.getId())).thenReturn(List.of(ok, late));
        when(hr.findEmployeeById(eq(T), any())).thenAnswer(i -> Optional.of(new EmployeeSummary(i.getArgument(1), "E1", "A", "B", "Driver " + i.getArgument(1).toString().substring(0, 4), null, null, "Driver", null, "ACTIVE")));
        var v = service.driverApprovals(T, site.getId());
        assertEquals(Set.of("OK", "EXPIRED"), new HashSet<>(v.stream().map(DriverApprovalView::state).toList()));
    }

    @Test @DisplayName("saving an approval needs a current employee and replaces an earlier record")
    void saveApproval() {
        var driver = new EmployeeSummary(driverId, "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE");
        when(hr.findEmployeeById(T, driverId)).thenReturn(Optional.of(driver));
        var req = new DriverApprovalRequest("APPROVED", TODAY.minusDays(1), TODAY.plusDays(300), "C-7", TODAY.plusDays(300), "Escort from gate");
        var v = service.saveDriverApproval(T, site.getId(), driverId, req, USER);
        assertEquals("Moses Sithole", v.driverName()); assertEquals("APPROVED", v.status());
        verify(driverApprovals).save(any());
        when(hr.findEmployeeById(T, driverId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.saveDriverApproval(T, site.getId(), driverId, req, USER));
        when(hr.findEmployeeById(T, driverId)).thenReturn(Optional.of(new EmployeeSummary(driverId, "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "TERMINATED")));
        assertThrows(IllegalStateException.class, () -> service.saveDriverApproval(T, site.getId(), driverId, req, USER));
    }

    @Test @DisplayName("removing an approval that is not there is not found")
    void removeMissing() {
        assertThrows(ResourceNotFoundException.class, () -> service.removeDriverApproval(T, site.getId(), driverId));
        var a = approval("APPROVED", TODAY, null, null, null, null);
        when(driverApprovals.findForDriver(T, site.getId(), driverId)).thenReturn(Optional.of(a));
        service.removeDriverApproval(T, site.getId(), driverId);
        verify(driverApprovals).delete(a);
    }

    // ── Compartment product rating ───────────────────────────────────────────

    private static FuelDeliveryCompartmentRepository.PreviousCargo prev(String fuel, Instant at) { return new FuelDeliveryCompartmentRepository.PreviousCargo(UUID.randomUUID(), fuel, at); }
    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Test @DisplayName("a compartment rated for other products is blocked for this one; an unrated compartment is free unless the company asks for ratings")
    void rating() {
        assertEquals(Set.of("COMPARTMENT_PRODUCT_NOT_RATED"), codes(FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of("DIESEL"), false, null, Set.of(), null)));
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "DIESEL", Set.of("DIESEL", "PETROL"), false, null, Set.of(), null).isEmpty());
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "DIESEL", Set.of(), false, null, Set.of(), null).isEmpty());
        var f = FuelEligibilityService.compartmentFindings("GP 1", 1, "DIESEL", Set.of(), true, null, Set.of(), null);
        assertEquals(Set.of("COMPARTMENT_UNRATED"), codes(f)); assertTrue(f.get(0).severity() == Severity.REVIEW);
    }

    @Test @DisplayName("diesel to paraffin is blocked without a cleaning after the diesel load, and passes with one; a cleaning before it does not count")
    void changeover() {
        var rules = Set.of("DIESEL>PARAFFIN");
        var last = prev("DIESEL", NOW);
        assertEquals(Set.of("COMPARTMENT_CLEANING_REQUIRED"), codes(FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of(), false, last, rules, null)));
        assertEquals(Set.of("COMPARTMENT_CLEANING_REQUIRED"), codes(FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of(), false, last, rules, NOW.minusSeconds(3600))));
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of(), false, last, rules, NOW.plusSeconds(3600)).isEmpty());
    }

    @Test @DisplayName("no rule for the pair, or the same product again, needs no cleaning; the company decides which pairs matter")
    void changeoverNeedsARule() {
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of(), false, prev("DIESEL", NOW), Set.of(), null).isEmpty());
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "DIESEL", Set.of(), false, prev("DIESEL", NOW), Set.of("DIESEL>PARAFFIN"), null).isEmpty());
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "PETROL", Set.of(), false, prev("DIESEL", NOW), Set.of("DIESEL>PARAFFIN"), null).isEmpty());
    }

    @Test @DisplayName("an unknown previous load is a review only when a rule leads into this product and nothing has ever been cleaned")
    void unknownPrevious() {
        var f = FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of(), false, null, Set.of("DIESEL>PARAFFIN"), null);
        assertEquals(Set.of("COMPARTMENT_PREVIOUS_CARGO_UNKNOWN"), codes(f)); assertEquals(Severity.REVIEW, f.get(0).severity());
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "PARAFFIN", Set.of(), false, null, Set.of("DIESEL>PARAFFIN"), NOW).isEmpty());
        assertTrue(FuelEligibilityService.compartmentFindings("GP 1", 1, "DIESEL", Set.of(), false, null, Set.of("DIESEL>PARAFFIN"), null).isEmpty());
    }

    @Test @DisplayName("evaluate checks only the compartments the load uses, and a rating on an unused compartment is ignored")
    void evaluateUsesLoadedCompartments() {
        when(compartmentProducts.findForCompartments(eq(T), any())).thenReturn(List.of(FuelCompartmentProduct.of(T, c1.getId(), "paraffin")));
        when(loadSplits.findPreviousCargo(eq(T), any(), any())).thenReturn(List.of());
        when(changeovers.findAllForTenant(T)).thenReturn(List.of());
        when(settings.findByTenant(T)).thenReturn(Optional.empty());
        when(cleanings.findForCompartment(eq(T), any(), any())).thenReturn(List.of());
        var onC2 = List.of(new za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad(c2.getId(), bd("12000")));
        assertTrue(service.evaluate(T, load, tanker, driverId, "Moses", List.of(c1, c2), onC2).blocks().isEmpty());
        var onC1 = List.of(new za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad(c1.getId(), bd("10000")));
        assertEquals(Set.of("COMPARTMENT_PRODUCT_NOT_RATED"), codes(service.evaluate(T, load, tanker, driverId, "Moses", List.of(c1, c2), onC1).blocks()));
    }

    @Test @DisplayName("evaluate takes the most recent previous load per compartment")
    void evaluateLatestPrevious() {
        when(compartmentProducts.findForCompartments(eq(T), any())).thenReturn(List.of());
        when(loadSplits.findPreviousCargo(eq(T), any(), any())).thenReturn(List.of(
                new FuelDeliveryCompartmentRepository.PreviousCargo(c1.getId(), "DIESEL", NOW.minusSeconds(86400)),
                new FuelDeliveryCompartmentRepository.PreviousCargo(c1.getId(), "PARAFFIN", NOW)));
        when(changeovers.findAllForTenant(T)).thenReturn(List.of(FuelChangeoverRule.of(T, "PARAFFIN", "DIESEL", null)));
        when(settings.findByTenant(T)).thenReturn(Optional.empty());
        when(cleanings.findForCompartment(eq(T), any(), any())).thenReturn(List.of());
        var onC1 = List.of(new za.co.handyflow.platform.fuel.dto.AllocationRequest.CompartmentLoad(c1.getId(), bd("10000")));
        assertEquals(Set.of("COMPARTMENT_CLEANING_REQUIRED"), codes(service.evaluate(T, load, tanker, driverId, "Moses", List.of(c1, c2), onC1).blocks()));
    }

    @Test @DisplayName("a cleaning needs evidence and cannot be dated in the future; a rule needs two different products")
    void cleaningAndRuleValidation() {
        assertThrows(IllegalArgumentException.class, () -> FuelCompartmentCleaning.record(T, c1.getId(), NOW, "Steam", null, " ", USER, NOW));
        assertThrows(IllegalArgumentException.class, () -> FuelCompartmentCleaning.record(T, c1.getId(), NOW.plusSeconds(7200), "Steam", "CERT-1", null, USER, NOW));
        assertThrows(IllegalArgumentException.class, () -> FuelCompartmentCleaning.record(T, c1.getId(), null, null, "CERT-1", null, USER, NOW));
        assertEquals("CERT-1", FuelCompartmentCleaning.record(T, c1.getId(), NOW, null, "CERT-1", null, USER, NOW).getCertificateRef());
        assertThrows(IllegalArgumentException.class, () -> FuelChangeoverRule.of(T, "diesel", "DIESEL", null));
        assertEquals("DIESEL", FuelChangeoverRule.of(T, "diesel", "paraffin", null).getFromFuel());
    }

    @Test @DisplayName("a changeover rule cannot be added twice, and a compartment of another tanker cannot be rated")
    void ruleDuplicatesAndOwnership() {
        when(changeovers.findAllForTenant(T)).thenReturn(List.of(FuelChangeoverRule.of(T, "DIESEL", "PARAFFIN", null)));
        assertThrows(IllegalStateException.class, () -> service.addChangeoverRule(T, new ChangeoverRuleRequest("diesel", "paraffin", null)));
        when(tankerCompartments.findByTanker(T, tanker.getId())).thenReturn(List.of(c1));
        assertThrows(ResourceNotFoundException.class, () -> service.saveCompartmentRating(T, tanker.getId(), UUID.randomUUID(), new CompartmentRatingRequest(List.of("DIESEL"))));
    }
}
