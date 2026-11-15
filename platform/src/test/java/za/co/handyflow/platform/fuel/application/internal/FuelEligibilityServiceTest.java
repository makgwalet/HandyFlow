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
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
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
    }

    private static void rule(FuelSiteAccessRule r, String cap, Integer gvm, String len, String wid, String hgt, Set<String> types, String notes) {
        r.update(cap == null ? null : bd(cap), gvm, len == null ? null : bd(len), wid == null ? null : bd(wid), hgt == null ? null : bd(hgt), types, notes, USER);
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
        var v = service.saveSiteRule(T, site.getId(), new SiteRuleRequest(bd("18000"), 30000, bd("12"), null, null, List.of(" rigid ", "", "Super-link"), "Gate 2", List.of(tanker.getId())), USER);
        assertEquals(0, bd("18000").compareTo(v.maxCapacityLitres()));
        assertEquals(List.of("RIGID", "SUPER-LINK"), v.approvedTypes());
        verify(siteTankers).deleteForSite(T, site.getId());
        verify(siteTankers).saveAll(argThat(list -> ((Collection<?>) list).size() == 1));
    }

    @Test @DisplayName("a rule naming an unknown tanker, or a limit of zero or less, is refused and nothing is saved")
    void ruleValidation() {
        assertThrows(ResourceNotFoundException.class, () -> service.saveSiteRule(T, site.getId(), new SiteRuleRequest(null, null, null, null, null, null, null, List.of(UUID.randomUUID())), USER));
        assertThrows(IllegalArgumentException.class, () -> service.saveSiteRule(T, site.getId(), new SiteRuleRequest(bd("0"), null, null, null, null, null, null, null), USER));
        assertThrows(IllegalArgumentException.class, () -> service.saveSiteRule(T, site.getId(), new SiteRuleRequest(null, -5, null, null, null, null, null, null), USER));
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
}
