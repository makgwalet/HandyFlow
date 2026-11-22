package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.TenantAccessRules.Access.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class TenantEntryRulesTest {
    @Test void motionEventsStillRaiseAlarmsWhileSuspendedButNotWhenCancelled() {
        assertTrue(TenantEntryRules.motionWebhookAllowed(FULL));
        assertTrue(TenantEntryRules.motionWebhookAllowed(RESTRICTED));
        assertFalse(TenantEntryRules.motionWebhookAllowed(BLOCKED));
    }

    @Test void portalIsOnlyForCompaniesInGoodStanding() {
        assertTrue(TenantEntryRules.portalAllowed(FULL));
        assertFalse(TenantEntryRules.portalAllowed(RESTRICTED));
        assertFalse(TenantEntryRules.portalAllowed(BLOCKED));
    }

    @Test void apiKeysReadOnlyWhenSuspendedAndRefusedWhenCancelled() {
        for (String m : new String[]{"GET", "get", "HEAD", "OPTIONS"}) assertTrue(TenantEntryRules.apiKeyAllowed(RESTRICTED, m), m);
        for (String m : new String[]{"POST", "PUT", "PATCH", "DELETE", null}) assertFalse(TenantEntryRules.apiKeyAllowed(RESTRICTED, m), String.valueOf(m));
        assertTrue(TenantEntryRules.apiKeyAllowed(FULL, "POST"));
        assertFalse(TenantEntryRules.apiKeyAllowed(BLOCKED, "GET"));
    }

    // ── source checks: the Spring parts cannot be run in the sandbox ──
    private static String read(String p) throws Exception { return Files.readString(Path.of("src/main/java/za/co/handyflow/platform/security/" + p)); }

    @Test void cameraWebhookChecksStandingAfterTheSecretAndComparesInConstantTime() throws Exception {
        String s = read("application/internal/CameraService.java");
        int secret = s.indexOf("MessageDigest.isEqual("), standing = s.indexOf("TenantEntryRules.motionWebhookAllowed(");
        assertTrue(secret > 0 && standing > secret, "secret first, then standing");
        assertFalse(s.contains("getWebhookSecret().equals("), "plain equals must be gone");
        assertTrue(s.contains("\"TENANT_SUSPENDED\""));
        assertTrue(standing < s.indexOf("if (!camera.isActive())"));
    }

    @Test void portalAnswersNotFoundForCompaniesNotInGoodStanding() throws Exception {
        String s = read("application/internal/ClientPortalService.java");
        int std = s.indexOf("TenantEntryRules.portalAllowed(");
        assertTrue(std > s.indexOf("findByPortalToken(token)") && std < s.indexOf("jdbc.queryForList"), "checked before any data is read");
        String block = s.substring(std, s.indexOf("UUID siteId = site.getId();"));
        assertTrue(block.contains("PORTAL_NOT_FOUND") && block.contains("NOT_FOUND"));
        assertTrue(s.contains("!TenantEntryRules.portalAllowed("), "refuse when NOT allowed");
    }

    @Test void apiKeyFilterChecksStandingBeforeScopeAndBeforeSettingTheTenant() throws Exception {
        String s = read("infrastructure/ApiKeyAuthFilter.java");
        int std = s.indexOf("TenantEntryRules.apiKeyAllowed(");
        assertTrue(std > 0 && std < s.indexOf("isPathInScope(apiKey,") && std < s.indexOf("TenantContext.setTenantId("));
        assertTrue(s.contains("tenantAccess.accessFor(apiKey.getTenantId().getValue())"));
    }
}
