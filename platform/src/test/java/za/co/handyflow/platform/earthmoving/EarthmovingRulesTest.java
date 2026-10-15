package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EarthmovingRulesTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    // ── status changes and who may make them ─────────────────────────────────

    @Test
    void deployedIsNeverSetThroughTheStatusEndpoint() {
        for (AssetStatus from : AssetStatus.values())
            assertNull(EarthmovingRules.authorityForStatusChange(from, AssetStatus.DEPLOYED));
    }

    @Test
    void releaseFromMaintenanceOrBreakdownNeedsTheReleaseAuthority() {
        assertEquals("EARTHMOVING_RELEASE", EarthmovingRules.authorityForStatusChange(AssetStatus.MAINTENANCE, AssetStatus.AVAILABLE));
        assertEquals("EARTHMOVING_RELEASE", EarthmovingRules.authorityForStatusChange(AssetStatus.BREAKDOWN, AssetStatus.AVAILABLE));
        assertTrue(EarthmovingRules.isRelease(AssetStatus.BREAKDOWN, AssetStatus.AVAILABLE));
    }

    @Test
    void returningFromSiteOrHireNeedsDeployNotRelease() {
        assertEquals("EARTHMOVING_DEPLOY", EarthmovingRules.authorityForStatusChange(AssetStatus.DEPLOYED, AssetStatus.AVAILABLE));
        assertEquals("EARTHMOVING_DEPLOY", EarthmovingRules.authorityForStatusChange(AssetStatus.HIRED_OUT, AssetStatus.AVAILABLE));
        assertFalse(EarthmovingRules.isRelease(AssetStatus.DEPLOYED, AssetStatus.AVAILABLE));
    }

    @Test
    void anyoneWhoWorksTheFleetCanReportABreakdownButOnlyAdminRetires() {
        assertEquals("EARTHMOVING_WRITE", EarthmovingRules.authorityForStatusChange(AssetStatus.DEPLOYED, AssetStatus.BREAKDOWN));
        assertEquals("EARTHMOVING_MAINTAIN", EarthmovingRules.authorityForStatusChange(AssetStatus.AVAILABLE, AssetStatus.MAINTENANCE));
        assertEquals("EARTHMOVING_ADMIN", EarthmovingRules.authorityForStatusChange(AssetStatus.AVAILABLE, AssetStatus.RETIRED));
    }

    @Test
    void adminAuthorityNeverStandsInForRelease() {
        // the release rule names EARTHMOVING_RELEASE only; there is no path where ADMIN is accepted for it
        for (AssetStatus from : new AssetStatus[]{AssetStatus.MAINTENANCE, AssetStatus.BREAKDOWN})
            assertNotEquals("EARTHMOVING_ADMIN", EarthmovingRules.authorityForStatusChange(from, AssetStatus.AVAILABLE));
    }

    @Test
    void everyChangeNeedsAReasonExceptReturningFromSite() {
        assertFalse(EarthmovingRules.needsReason(AssetStatus.DEPLOYED, AssetStatus.AVAILABLE));
        assertTrue(EarthmovingRules.needsReason(AssetStatus.MAINTENANCE, AssetStatus.AVAILABLE));
        assertTrue(EarthmovingRules.needsReason(AssetStatus.AVAILABLE, AssetStatus.RETIRED));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.requireReason("ok", "x"));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.requireReason(null, "x"));
        assertEquals("Hydraulic hose replaced", EarthmovingRules.requireReason("  Hydraulic hose replaced ", "x"));
    }

    // ── deployment ───────────────────────────────────────────────────────────

    @Test
    void anUnresolvedCriticalIncidentBlocksDeployment() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                EarthmovingRules.checkDeployable(AssetStatus.AVAILABLE, 1, "OWN", null, LocalDate.of(2026, 10, 15)));
        assertTrue(e.getMessage().contains("critical"));
    }

    @Test
    void onlyAnAvailableMachineCanBeDeployed() {
        for (AssetStatus s : AssetStatus.values()) {
            if (s == AssetStatus.AVAILABLE) continue;
            assertThrows(IllegalStateException.class, () ->
                    EarthmovingRules.checkDeployable(s, 0, "OWN", null, LocalDate.of(2026, 10, 15)));
        }
        assertDoesNotThrow(() -> EarthmovingRules.checkDeployable(AssetStatus.AVAILABLE, 0, "OWN", null, LocalDate.of(2026, 10, 15)));
    }

    @Test
    void aHiredInMachineWhoseHireEndedCannotBeDeployed() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        assertThrows(IllegalStateException.class, () ->
                EarthmovingRules.checkDeployable(AssetStatus.AVAILABLE, 0, "HIRED_IN", today.minusDays(1), today));
        assertDoesNotThrow(() -> EarthmovingRules.checkDeployable(AssetStatus.AVAILABLE, 0, "HIRED_IN", today, today));
        assertDoesNotThrow(() -> EarthmovingRules.checkDeployable(AssetStatus.AVAILABLE, 0, "OWN", today.minusDays(30), today));
    }

    @Test
    void deployDatesMustBeInOrder() {
        assertThrows(IllegalArgumentException.class, () ->
                EarthmovingRules.checkDeployDates(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)));
        assertDoesNotThrow(() -> EarthmovingRules.checkDeployDates(null, LocalDate.of(2026, 11, 1)));
    }

    // ── shifts and meters ────────────────────────────────────────────────────

    @Test
    void aShiftCannotStartOnAMachineThatIsNotInService() {
        for (AssetStatus s : new AssetStatus[]{AssetStatus.MAINTENANCE, AssetStatus.BREAKDOWN, AssetStatus.RETIRED, AssetStatus.HIRED_OUT})
            assertThrows(IllegalStateException.class, () -> EarthmovingRules.checkShiftStart(s, null, n("100")));
        assertDoesNotThrow(() -> EarthmovingRules.checkShiftStart(AssetStatus.DEPLOYED, n("100"), n("100")));
        assertDoesNotThrow(() -> EarthmovingRules.checkShiftStart(AssetStatus.AVAILABLE, null, n("100")));
    }

    @Test
    void aStartReadingBelowTheMachineReadingIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                EarthmovingRules.checkShiftStart(AssetStatus.DEPLOYED, n("99.9"), n("100")));
        assertTrue(e.getMessage().contains("count up"));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkShiftStart(AssetStatus.DEPLOYED, n("-1"), n("0")));
    }

    @Test
    void aShiftCannotEndBeforeItStartedOrWithALowerReading() {
        Instant t = Instant.parse("2026-10-15T06:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkShiftEnd(t, t.minusSeconds(1), n("10"), n("12"), null));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkShiftEnd(t, t.plusSeconds(60), n("10"), n("9.9"), null));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkShiftEnd(t, t.plusSeconds(60), n("10"), n("12"), n("-5")));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkShiftEnd(t, null, null, null, null));
        assertDoesNotThrow(() -> EarthmovingRules.checkShiftEnd(t, t.plusSeconds(3600), n("10"), n("10"), n("0")));
        assertDoesNotThrow(() -> EarthmovingRules.checkShiftEnd(t, t.plusSeconds(3600), null, n("12"), null));
    }

    // ── maintenance ──────────────────────────────────────────────────────────

    @Test
    void everyMaintenanceTypeTheScreenOffersIsAccepted() {
        // the four the database used to refuse (finding B1) plus the original six
        for (String t : new String[]{"SERVICE", "REPAIR", "INSPECTION", "TYRE", "BATTERY", "ELECTRICAL", "HYDRAULICS", "ENGINE", "TRACKS", "OTHER"})
            assertDoesNotThrow(() -> EarthmovingRules.checkMaintenance(AssetStatus.AVAILABLE, t, null, null, null), t);
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkMaintenance(AssetStatus.AVAILABLE, "WELDING", null, null, null));
    }

    @Test
    void maintenanceRulesProtectTheServiceHistory() {
        assertThrows(IllegalStateException.class, () -> EarthmovingRules.checkMaintenance(AssetStatus.RETIRED, "REPAIR", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkMaintenance(AssetStatus.AVAILABLE, "SERVICE", n("400"), n("500"), null));
        assertDoesNotThrow(() -> EarthmovingRules.checkMaintenance(AssetStatus.AVAILABLE, "REPAIR", n("400"), n("500"), null));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkMaintenance(AssetStatus.AVAILABLE, "REPAIR", null, null, n("-1")));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkMaintenance(AssetStatus.AVAILABLE, "REPAIR", n("-1"), null, null));
    }

    // ── asset details and lists ──────────────────────────────────────────────

    @Test
    void assetDetailsAreChecked() {
        assertEquals("OWN", EarthmovingRules.checkAssetDetails("D9", "DOZER", null, 2020, n("10"), n("100"), null, null, null, 2026));
        assertEquals("HIRED_IN", EarthmovingRules.checkAssetDetails("D9", "dozer", "hired_in", null, null, null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails(" ", "DOZER", null, null, null, null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "SPACESHIP", null, null, null, null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", "BORROWED", null, null, null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", null, 1900, null, null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", null, 2028, null, null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", null, null, n("-1"), null, null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", null, null, null, n("-1"), null, null, null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", null, null, null, null,
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), null, 2026));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.checkAssetDetails("D9", "DOZER", null, null, null, null, null, null, n("0"), 2026));
    }

    @Test
    void incidentTypesAndSeveritiesAreValidated() {
        assertEquals("NEAR_MISS", EarthmovingRules.oneOf("near_miss", EarthmovingRules.INCIDENT_TYPES, "Incident type"));
        assertEquals("CRITICAL", EarthmovingRules.oneOf(" critical ", EarthmovingRules.SEVERITIES, "Severity"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                EarthmovingRules.oneOf("EXPLOSION", EarthmovingRules.INCIDENT_TYPES, "Incident type"));
        assertTrue(e.getMessage().contains("NEAR_MISS"));
        assertThrows(IllegalArgumentException.class, () -> EarthmovingRules.oneOf(" ", EarthmovingRules.SEVERITIES, "Severity"));
    }
}
