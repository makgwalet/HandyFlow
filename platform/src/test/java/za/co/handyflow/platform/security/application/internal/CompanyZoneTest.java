package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class CompanyZoneTest {

    private static String read(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    private static String code(Runnable r) { return assertThrows(HandyFlowException.class, r::run).getErrorCode(); }

    @Test
    void aRegionZoneIsAccepted() {
        assertEquals("Africa/Windhoek", CompanyZoneRules.requireValid("Africa/Windhoek"));
        assertEquals("Pacific/Auckland", CompanyZoneRules.requireValid("  Pacific/Auckland "));
        assertEquals("UTC", CompanyZoneRules.requireValid("UTC"));
    }

    @Test
    void blankUnknownAndOffsetZonesAreRefused() {
        assertEquals("TZ_REQUIRED", code(() -> CompanyZoneRules.requireValid(null)));
        assertEquals("TZ_REQUIRED", code(() -> CompanyZoneRules.requireValid("   ")));
        assertEquals("TZ_INVALID", code(() -> CompanyZoneRules.requireValid("Mars/Olympus")));
        assertEquals("TZ_INVALID", code(() -> CompanyZoneRules.requireValid("+02:00")));
        assertEquals("TZ_INVALID", code(() -> CompanyZoneRules.requireValid("GMT+2")));
        assertEquals("TZ_INVALID", code(() -> CompanyZoneRules.requireValid("Africa/Johannesburg'; DROP TABLE tenants;--")));
        assertEquals("TZ_INVALID", code(() -> CompanyZoneRules.requireValid("a".repeat(61))));
        assertEquals("TZ_INVALID", code(() -> CompanyZoneRules.requireValid("africa/johannesburg")));
    }

    @Test
    void changingToTheSameZoneIsRefused() {
        assertEquals("TZ_UNCHANGED", code(() -> CompanyZoneRules.requireDifferent("Africa/Johannesburg", "Africa/Johannesburg")));
        assertEquals("TZ_UNCHANGED", code(() -> CompanyZoneRules.requireDifferent(null, "Africa/Johannesburg")));
        assertDoesNotThrow(() -> CompanyZoneRules.requireDifferent("Africa/Johannesburg", "Africa/Windhoek"));
    }

    @Test
    void aChangeNeedsConfirmation() {
        assertEquals("TZ_CONFIRM_REQUIRED", code(() -> CompanyZoneRules.requireConfirmed(false)));
        assertDoesNotThrow(() -> CompanyZoneRules.requireConfirmed(true));
    }

    @Test
    void effectSaysHowTheClockMovesAndWhetherTodayChanges() {
        Instant now = Instant.parse("2026-11-20T22:30:00Z"); // 00:30 on the 21st in SA, 11:30 on the 21st in Auckland
        CompanyZoneRules.Effect e = CompanyZoneRules.effect(ZoneId.of("Africa/Johannesburg"), ZoneId.of("Pacific/Auckland"), now);
        assertEquals(11 * 60, e.offsetChangeMinutes());
        assertFalse(e.todayChangesNow());
        assertTrue(e.warning().contains("11 hours forward"));
        assertTrue(e.warning().contains("Pacific/Auckland"));
        CompanyZoneRules.Effect u = CompanyZoneRules.effect(ZoneId.of("Africa/Johannesburg"), ZoneId.of("UTC"), now);
        assertEquals(-120, u.offsetChangeMinutes());
        assertTrue(u.todayChangesNow());
        assertTrue(u.warning().contains("2 hours back"));
        assertTrue(u.warning().contains("different date"));
    }

    @Test
    void effectFromAMissingZoneStartsFromSouthAfrica() {
        assertEquals("Africa/Johannesburg", CompanyZoneRules.effect(null, ZoneId.of("UTC"), Instant.parse("2026-11-20T10:00:00Z")).from());
    }

    @Test
    void theServiceChecksBeforeItWritesAndForgetsTheCachedZone() throws Exception {
        String s = read("application/internal/CompanyZoneService.java");
        int change = s.indexOf("public Changed change");
        String body = s.substring(change, s.indexOf("private ZoneId zoneOf"));
        for (String step : new String[]{"requireValid", "requireDifferent", "requireConfirmed"}) assertTrue(body.contains("CompanyZoneRules." + step + "("), step);
        assertTrue(body.indexOf("requireValid") < body.indexOf("UPDATE tenants"));
        assertTrue(body.indexOf("requireDifferent") < body.indexOf("UPDATE tenants"));
        assertTrue(body.indexOf("requireConfirmed") < body.indexOf("UPDATE tenants"));
        assertTrue(body.contains("FOR UPDATE"));
        assertTrue(body.contains("UPDATE tenants SET time_zone = ? WHERE id = ?"));
        assertTrue(body.contains("TIME_ZONE_CHANGED"));
        assertTrue(body.indexOf("UPDATE tenants") < body.indexOf("TIME_ZONE_CHANGED"));
        assertTrue(body.contains("calendar.evict(tenant)"));
        assertTrue(body.indexOf("TIME_ZONE_CHANGED") < body.indexOf("calendar.evict"));
        assertTrue(s.contains("@Transactional\n    public Changed change"));
    }

    @Test
    void onlyTheCompanysOwnRowIsEverTouched() throws Exception {
        String s = read("application/internal/CompanyZoneService.java");
        assertFalse(s.contains("UPDATE tenants SET time_zone = ?;"));
        assertFalse(s.matches("(?s).*UPDATE tenants (?!SET time_zone = \\? WHERE id = \\?).*"));
        assertTrue(s.contains("TenantId tenantId"));
    }

    @Test
    void theControllerGuardsEveryEndpoint() throws Exception {
        String c = read("api/CompanyZoneController.java");
        assertEquals(3, c.split("featureGuard.requireModule\\(\"security\"\\)", -1).length - 1);
        assertEquals(3, c.split("@PreAuthorize", -1).length - 1);
        int put = c.indexOf("@PutMapping");
        assertTrue(c.substring(put, c.indexOf("public", put)).contains("SECURITY_MANAGE"));
        int pre = c.indexOf("/preview");
        assertTrue(c.substring(pre, c.indexOf("public", pre)).contains("SECURITY_MANAGE"));
        assertTrue(c.contains("TenantContext.getTenantIdAsObject()"));
        assertFalse(c.contains("tenantId\""), "tenant never comes from the request");
    }
}
