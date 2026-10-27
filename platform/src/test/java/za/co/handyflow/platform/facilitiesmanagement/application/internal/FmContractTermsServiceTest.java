package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmBusinessCalendar;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSlaTarget;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmBusinessCalendarRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmServiceAgreementRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain JUnit with hand-written fakes (no Mockito) so the contract-resolution rules can be exercised anywhere.
 * The fake repository honours the same date/client filter the real query applies.
 */
class FmContractTermsServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final UUID siteA = UUID.randomUUID();
    private final UUID siteB = UUID.randomUUID();
    private final List<FmServiceAgreement> agreements = new ArrayList<>();

    private final FmServiceAgreementRepository agreementRepo = (FmServiceAgreementRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmServiceAgreementRepository.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "findActiveAsOfDate":
                        LocalDate d = (LocalDate) a[2];
                        return agreements.stream().filter(x -> x.getClientId().equals(a[1]) && x.coversDate(d)).toList();
                    case "findActiveById":
                        return agreements.stream().filter(x -> x.getId().equals(a[1])).findFirst();
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });

    private final FmBusinessCalendarRepository calendarRepo = (FmBusinessCalendarRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmBusinessCalendarRepository.class}, (p, m, a) -> Optional.empty());

    private final FmContractTermsService service = new FmContractTermsService(agreementRepo, calendarRepo);

    private FmServiceAgreement agreement(String type, Set<UUID> sites, Set<String> cats, LocalDate start,
                                         BigDecimal rate, List<FmSlaTarget> sla) {
        FmServiceAgreement a = FmServiceAgreement.create(tenant, client, "A-" + agreements.size(), type,
                "RETAINER".equals(type) ? new BigDecimal("10000") : null, rate, new BigDecimal("15"),
                start, null, sites, cats, sla);
        agreements.add(a);
        return a;
    }

    private FmWorkOrder order(UUID site, String category, String priority) {
        return FmWorkOrder.create(tenant, "WO-1", client, site, null, null, category, priority, "d", "r", FmTime.today());
    }

    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);

    @Test
    void noAgreementMeansChargeableWithNoSla() {
        FmWorkOrder wo = order(siteA, "CORRECTIVE", "NORMAL");
        service.applyTerms(tenant, wo);
        assertNull(wo.getAgreementId());
        assertEquals("CHARGEABLE", wo.getCoverage());
        assertFalse(wo.hasSla());
    }

    @Test
    void siteSpecificBeatsAllSitesAndCategorySpecificBeatsAllCategories() {
        FmServiceAgreement all = agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null, List.of());
        FmServiceAgreement cat = agreement("TIME_AND_MATERIALS", Set.of(), Set.of("CORRECTIVE"), LocalDate.of(2020, 6, 1), null, List.of());
        FmServiceAgreement site = agreement("TIME_AND_MATERIALS", Set.of(siteA), Set.of(), LocalDate.of(2021, 1, 1), null, List.of());
        // site (2) > category (1) > all (0)
        assertEquals(site.getId(), service.resolveAgreement(tenant, client, siteA, "CORRECTIVE", FmTime.today()).orElseThrow().getId());
        assertEquals(cat.getId(), service.resolveAgreement(tenant, client, siteB, "CORRECTIVE", FmTime.today()).orElseThrow().getId());
        assertEquals(all.getId(), service.resolveAgreement(tenant, client, siteB, "EMERGENCY", FmTime.today()).orElseThrow().getId());
    }

    @Test
    void equalSpecificityPrefersTheLaterStart() {
        FmServiceAgreement older = agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null, List.of());
        FmServiceAgreement newer = agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LocalDate.of(2024, 1, 1), null, List.of());
        assertEquals(newer.getId(), service.resolveAgreement(tenant, client, siteA, "OTHER", FmTime.today()).orElseThrow().getId());
        assertNotEquals(older.getId(), newer.getId());
    }

    @Test
    void anEndedAgreementIsNotResolved() {
        FmServiceAgreement a = agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null, List.of());
        a.end();
        assertTrue(service.resolveAgreement(tenant, client, siteA, "OTHER", FmTime.today()).isEmpty());
    }

    @Test
    void retainerStampsIncludedAndTimeAndMaterialsStampsChargeable() {
        agreement("RETAINER", Set.of(siteA), Set.of(), LONG_AGO, new BigDecimal("450"), List.of());
        agreement("TIME_AND_MATERIALS", Set.of(siteB), Set.of(), LONG_AGO, new BigDecimal("600"), List.of());
        FmWorkOrder included = order(siteA, "CORRECTIVE", "NORMAL");
        FmWorkOrder chargeable = order(siteB, "CORRECTIVE", "NORMAL");
        service.applyTerms(tenant, included);
        service.applyTerms(tenant, chargeable);
        assertEquals("INCLUDED", included.getCoverage());
        assertEquals(0, new BigDecimal("450").compareTo(included.getLabourRate()));
        assertEquals("CHARGEABLE", chargeable.getCoverage());
        assertEquals(0, new BigDecimal("600").compareTo(chargeable.getLabourRate()));
    }

    @Test
    void slaIsStampedFromTheTargetForThePriority() {
        agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null,
                List.of(FmSlaTarget.of("URGENT", 30, 120, "24X7"), FmSlaTarget.of("NORMAL", 60, 480, "24X7")));
        FmWorkOrder wo = order(siteA, "CORRECTIVE", "URGENT");
        service.applyTerms(tenant, wo);
        assertTrue(wo.hasSla());
        assertEquals(30, (int) wo.getResponseTargetMinutes());
        assertEquals(120, (int) wo.getResolutionTargetMinutes());
        assertEquals(wo.getCreatedAt().plusSeconds(30 * 60), wo.getResponseDueAt());
        assertEquals(wo.getCreatedAt().plusSeconds(120 * 60), wo.getResolutionDueAt());
    }

    @Test
    void aPriorityWithNoTargetGetsNoSla() {
        agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null, List.of(FmSlaTarget.of("URGENT", 30, 120, "24X7")));
        FmWorkOrder wo = order(siteA, "CORRECTIVE", "LOW");
        service.applyTerms(tenant, wo);
        assertFalse(wo.hasSla());
    }

    @Test
    void ppmWorkIsCoveredButNeverGetsAnSlaClock() {
        agreement("RETAINER", Set.of(), Set.of(), LONG_AGO, null, List.of(FmSlaTarget.of("NORMAL", 60, 480, "24X7")));
        FmWorkOrder wo = order(siteA, "PPM", "NORMAL");
        service.applyTerms(tenant, wo);
        assertEquals("INCLUDED", wo.getCoverage());
        assertFalse(wo.hasSla());
    }

    @Test
    void reapplySlaFollowsAPriorityRaiseButSkipsPpm() {
        agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null,
                List.of(FmSlaTarget.of("URGENT", 30, 120, "24X7"), FmSlaTarget.of("NORMAL", 60, 480, "24X7")));
        FmWorkOrder wo = FmWorkOrder.createRequest(tenant, "WO-2", client, siteA, null, "CORRECTIVE", "NORMAL",
                "d", "r", UUID.randomUUID(), false, null);
        service.applyTerms(tenant, wo);
        assertEquals(480, (int) wo.getResolutionTargetMinutes());
        wo.approve(UUID.randomUUID(), "URGENT", null, null); // priority raised at approval
        service.reapplySla(tenant, wo);
        assertEquals(120, (int) wo.getResolutionTargetMinutes());

        FmWorkOrder ppm = order(siteA, "PPM", "URGENT");
        service.applyTerms(tenant, ppm);
        service.reapplySla(tenant, ppm);
        assertFalse(ppm.hasSla());
    }

    @Test
    void resumingAPausedClockMovesTheDeadlineOutByThePausedTime() {
        agreement("TIME_AND_MATERIALS", Set.of(), Set.of(), LONG_AGO, null, List.of(FmSlaTarget.of("NORMAL", 60, 240, "24X7")));
        FmWorkOrder wo = order(siteA, "CORRECTIVE", "NORMAL");
        service.applyTerms(tenant, wo);
        Instant before = wo.getResolutionDueAt();
        Instant pausedAt = wo.getCreatedAt().plusSeconds(60 * 60);
        Instant now = pausedAt.plusSeconds(90 * 60);
        service.resumeSla(tenant, wo, pausedAt, now);
        assertEquals(90, wo.getSlaPausedMinutes());
        assertEquals(before.plusSeconds(90 * 60), wo.getResolutionDueAt());
    }

    @Test
    void resumingWithoutAnSlaJustClearsThePause() {
        FmWorkOrder wo = order(siteA, "CORRECTIVE", "NORMAL");
        service.applyTerms(tenant, wo);
        service.resumeSla(tenant, wo, Instant.now().minusSeconds(600), Instant.now());
        assertEquals(0, wo.getSlaPausedMinutes());
        assertFalse(wo.hasSla());
    }
}
