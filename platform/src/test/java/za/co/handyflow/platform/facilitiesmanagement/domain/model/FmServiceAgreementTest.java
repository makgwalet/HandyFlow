package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FmServiceAgreementTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmServiceAgreement retainer(LocalDate start, LocalDate end) {
        return FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), "Gold", "RETAINER", bd("5000.00"), bd("450.00"),
                bd("10.00"), start, end, Set.of(), Set.of(), List.of());
    }

    private FmServiceAgreement tm(Set<UUID> sites, Set<String> categories, List<FmSlaTarget> targets) {
        return FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), "T&M", "TIME_AND_MATERIALS", null, bd("650.00"),
                null, LocalDate.of(2026, 1, 1), null, sites, categories, targets);
    }

    // ── creation ─────────────────────────────────────────────────────────────

    @Test
    void retainerRequiresPositiveMonthlyFee() {
        TenantId tenantId = TenantId.generate();
        UUID clientId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(tenantId, clientId, null, "RETAINER",
                null, null, null, LocalDate.now(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(tenantId, clientId, null, "RETAINER",
                BigDecimal.ZERO, null, null, LocalDate.now(), null, null, null, null));
    }

    @Test
    void timeAndMaterialsDoesNotRequireMonthlyFee() {
        FmServiceAgreement agreement = tm(Set.of(), Set.of(), List.of());
        assertFalse(agreement.isRetainer());
        assertEquals("ACTIVE", agreement.getStatus());
        assertEquals(bd("650.00"), agreement.getHourlyRate());
    }

    @Test
    void billingTypeMustBeKnownAndDefaultsToTimeAndMaterials() {
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), null,
                "FIXED", null, null, null, LocalDate.now(), null, null, null, null));
        FmServiceAgreement defaulted = FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), null, null,
                null, null, null, LocalDate.now(), null, null, null, null);
        assertEquals("TIME_AND_MATERIALS", defaulted.getBillingType());
    }

    @Test
    void endDateMustBeAfterStartDate() {
        LocalDate start = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> retainer(start, start));
        assertThrows(IllegalArgumentException.class, () -> retainer(start, start.minusDays(1)));
    }

    @Test
    void rateAndMarkupMustBeSensible() {
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), null,
                "TIME_AND_MATERIALS", null, bd("-1"), null, LocalDate.now(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), null,
                "TIME_AND_MATERIALS", null, null, bd("-5"), LocalDate.now(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), null,
                "TIME_AND_MATERIALS", null, null, bd("1000.01"), LocalDate.now(), null, null, null, null));
    }

    @Test
    void clientAndStartDateAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(TenantId.generate(), null, null,
                "TIME_AND_MATERIALS", null, null, null, LocalDate.now(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FmServiceAgreement.create(TenantId.generate(), UUID.randomUUID(), null,
                "TIME_AND_MATERIALS", null, null, null, null, null, null, null, null));
    }

    // ── dates ────────────────────────────────────────────────────────────────

    @Test
    void coversDateRespectsActiveStatusAndDateBounds() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        FmServiceAgreement agreement = retainer(start, end);

        assertFalse(agreement.coversDate(start.minusDays(1)));
        assertTrue(agreement.coversDate(start));
        assertTrue(agreement.coversDate(LocalDate.of(2026, 6, 15)));
        assertTrue(agreement.coversDate(end));
        assertFalse(agreement.coversDate(end.plusDays(1)));

        agreement.end();
        assertFalse(agreement.coversDate(start));
        assertEquals("ENDED", agreement.getStatus());
    }

    @Test
    void coversDateWithNoEndDateIsOpenEnded() {
        FmServiceAgreement agreement = retainer(LocalDate.now().minusMonths(1), null);
        assertTrue(agreement.coversDate(LocalDate.now().plusYears(5)));
    }

    @Test
    void endingAnOpenEndedAgreementClosesItToday() {
        FmServiceAgreement agreement = retainer(LocalDate.now().minusMonths(1), null);
        agreement.end();
        assertNotNull(agreement.getEndDate());
    }

    // ── coverage ─────────────────────────────────────────────────────────────

    @Test
    void noSitesAndNoCategoriesMeansEverything() {
        FmServiceAgreement a = tm(Set.of(), Set.of(), List.of());
        assertTrue(a.coversSite(UUID.randomUUID()));
        assertTrue(a.coversCategory("EMERGENCY"));
        assertEquals(0, a.specificity());
    }

    @Test
    void siteAndCategoryRestrictionsNarrowCoverage() {
        UUID siteA = UUID.randomUUID();
        UUID siteB = UUID.randomUUID();
        FmServiceAgreement a = tm(Set.of(siteA), Set.of("ppm", "corrective"), List.of());
        assertTrue(a.coversSite(siteA));
        assertFalse(a.coversSite(siteB));
        assertTrue(a.coversCategory("PPM"));
        assertTrue(a.coversCategory("CORRECTIVE"));
        assertFalse(a.coversCategory("EMERGENCY"));
        assertEquals(Set.of("CORRECTIVE", "PPM"), a.categorySet());
        assertEquals(3, a.specificity());
    }

    @Test
    void siteSpecificBeatsCategorySpecificBeatsEverything() {
        FmServiceAgreement everything = tm(Set.of(), Set.of(), List.of());
        FmServiceAgreement byCategory = tm(Set.of(), Set.of("PPM"), List.of());
        FmServiceAgreement bySite = tm(Set.of(UUID.randomUUID()), Set.of(), List.of());
        assertTrue(everything.specificity() < byCategory.specificity());
        assertTrue(byCategory.specificity() < bySite.specificity());
    }

    @Test
    void unknownCategoriesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tm(Set.of(), Set.of("PLUMBING"), List.of()));
    }

    // ── SLA targets ──────────────────────────────────────────────────────────

    @Test
    void slaTargetsAreLookedUpByPriority() {
        FmServiceAgreement a = tm(Set.of(), Set.of(), List.of(
                FmSlaTarget.of("urgent", 30, 240, "24x7"), FmSlaTarget.of("NORMAL", 240, 2880, "BUSINESS_HOURS")));
        assertEquals(240, a.slaTargetFor("URGENT").orElseThrow().getResolutionMinutes());
        assertEquals("24X7", a.slaTargetFor("URGENT").orElseThrow().getClock());
        assertEquals("BUSINESS_HOURS", a.slaTargetFor("NORMAL").orElseThrow().getClock());
        assertTrue(a.slaTargetFor("LOW").isEmpty());
    }

    @Test
    void duplicatePriorityTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tm(Set.of(), Set.of(), List.of(
                FmSlaTarget.of("HIGH", 60, 240, "24X7"), FmSlaTarget.of("HIGH", 30, 120, "24X7"))));
    }

    @Test
    void resolutionCannotBeShorterThanResponse() {
        assertThrows(IllegalArgumentException.class, () -> tm(Set.of(), Set.of(), List.of(FmSlaTarget.of("HIGH", 120, 60, "24X7"))));
    }

    @Test
    void responseMustBeAtLeastOneMinute() {
        assertThrows(IllegalArgumentException.class, () -> tm(Set.of(), Set.of(), List.of(FmSlaTarget.of("HIGH", 0, 60, "24X7"))));
    }

    @Test
    void unknownPriorityOrClockIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> tm(Set.of(), Set.of(), List.of(FmSlaTarget.of("CRITICAL", 30, 60, "24X7"))));
        assertThrows(IllegalArgumentException.class, () -> tm(Set.of(), Set.of(), List.of(FmSlaTarget.of("HIGH", 30, 60, "OFFICE"))));
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void updateLeavesUnsuppliedFieldsAndReplacesTheEndDate() {
        FmServiceAgreement a = retainer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        a.update(null, null, bd("500.00"), null, null, null, null, null);
        assertEquals("Gold", a.getName());
        assertEquals(bd("5000.00"), a.getMonthlyFee());
        assertEquals(bd("500.00"), a.getHourlyRate());
        assertEquals(bd("10.00"), a.getPartsMarkupPercent());
        assertNull(a.getEndDate()); // endDate is full-replace: omitted means open-ended
    }

    @Test
    void updateCanNarrowAndWidenCoverage() {
        UUID site = UUID.randomUUID();
        FmServiceAgreement a = retainer(LocalDate.of(2026, 1, 1), null);
        a.update(null, null, null, null, null, Set.of(site), Set.of("PPM"), null);
        assertTrue(a.coversSite(site));
        assertFalse(a.coversSite(UUID.randomUUID()));
        a.update(null, null, null, null, null, Set.of(), Set.of(), null); // empty = everything again
        assertTrue(a.coversSite(UUID.randomUUID()));
        assertTrue(a.coversCategory("EMERGENCY"));
    }

    @Test
    void updateCannotLeaveARetainerWithoutAFee() {
        FmServiceAgreement a = retainer(LocalDate.of(2026, 1, 1), null);
        assertThrows(IllegalArgumentException.class, () -> a.update(null, BigDecimal.ZERO, null, null, null, null, null, null));
    }

    @Test
    void updateCannotMoveTheEndDateBeforeTheStart() {
        FmServiceAgreement a = retainer(LocalDate.of(2026, 1, 1), null);
        assertThrows(IllegalArgumentException.class, () -> a.update(null, null, null, null, LocalDate.of(2026, 1, 1), null, null, null));
    }

    @Test
    void updateReplacesSlaTargetsOnlyWhenGiven() {
        FmServiceAgreement a = tm(Set.of(), Set.of(), List.of(FmSlaTarget.of("HIGH", 60, 240, "24X7")));
        a.update(null, null, null, null, null, null, null, null);
        assertEquals(1, a.getSlaTargets().size());
        a.update(null, null, null, null, null, null, null, List.of());
        assertTrue(a.getSlaTargets().isEmpty());
    }
}
