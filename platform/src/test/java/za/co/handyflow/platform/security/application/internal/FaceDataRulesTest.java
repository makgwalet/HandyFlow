package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.security.application.internal.FaceDataRules.Decision.*;

import java.nio.file.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FaceDataRulesTest {
    private static final String BASE = "src/main/java/za/co/handyflow/platform/security/";
    private static String read(String p) throws Exception { return Files.readString(Path.of(BASE + p)); }
    private static String template(int bytes) { return Base64.getEncoder().encodeToString(new byte[bytes]); }

    @Test void consentedValidTemplateIsStored() {
        assertEquals(STORE, FaceDataRules.decide(template(512), true));
    }
    @Test void noConsentMeansNotStored() {
        assertEquals(SKIP_NO_CONSENT, FaceDataRules.decide(template(512), false));
        assertEquals(SKIP_NO_CONSENT, FaceDataRules.decide(template(512), null));
    }
    @Test void nothingSentIsNotAnError() {
        assertEquals(SKIP_NONE, FaceDataRules.decide(null, true));
        assertEquals(SKIP_NONE, FaceDataRules.decide("  ", true));
    }
    @Test void badShapesAreRejected() {
        assertEquals(REJECT_INVALID, FaceDataRules.decide("not base64 !!", true));
        assertEquals(REJECT_INVALID, FaceDataRules.decide(template(64), true));      // too small
        assertEquals(REJECT_INVALID, FaceDataRules.decide(template(8192 + 4), true)); // too large
        assertEquals(REJECT_INVALID, FaceDataRules.decide(template(130), true));      // not whole floats
        assertEquals(STORE, FaceDataRules.decide(template(128), true));
        assertEquals(STORE, FaceDataRules.decide(template(8192), true));
    }
    @Test void leaversMustBeWiped() {
        assertTrue(FaceDataRules.mustWipe("TERMINATED", false));
        assertTrue(FaceDataRules.mustWipe("ACTIVE", true));
        assertFalse(FaceDataRules.mustWipe("ACTIVE", false));
        assertFalse(FaceDataRules.mustWipe("SUSPENDED", false));
        assertFalse(FaceDataRules.mustWipe("ON_LEAVE", false));
    }

    // ---- wiring ----
    @Test void enrolOnlyStoresWithConsent() throws Exception {
        String s = read("application/internal/GuardAuthService.java");
        assertTrue(s.contains("FaceDataRules.decide(req.faceEmbeddingBase64(), req.faceConsent())"));
        assertTrue(s.contains("guard.storeFaceEmbedding(req.faceEmbeddingBase64().trim(), Instant.now())"));
        assertFalse(s.contains("guard.setFaceEmbedding("), "the unchecked setter must not be used");
        assertTrue(s.contains("\"INVALID_FACE_DATA\""));
        assertTrue(s.contains("                faceStored,"), "response must report what was actually stored");
    }
    @Test void leaversAreWipedWhereTheyLeave() throws Exception {
        String s = read("application/internal/GuardService.java");
        int term = s.indexOf("FaceDataRules.mustWipe(req.status(), false)");
        assertTrue(term > 0 && s.indexOf("clearFaceEmbedding()", term) > term);
        int del = s.indexOf("public void deleteGuard");
        int delEnd = s.indexOf("@Transactional", del);
        String body = s.substring(del, delEnd);
        assertTrue(body.contains("guard.clearFaceEmbedding()"), "delete must clear the face template");
        assertTrue(s.contains("\"FACE_DATA_REMOVED\""));
    }
    @Test void sweeperUsesTheLeaverQuery() throws Exception {
        String r = read("domain/repository/GuardRepository.java");
        assertTrue(r.contains("deleted_at IS NOT NULL OR status = 'TERMINATED'"));
        assertTrue(r.contains("face_embedding = NULL, face_consent_at = NULL"));
        String w = read("application/internal/FaceDataRetentionSweeper.java");
        assertTrue(w.contains("guardRepository::wipeFaceDataOfLeavers"));
        assertTrue(w.contains("@Scheduled"));
    }
    @Test void migrationAddsConsentColumn() throws Exception {
        String m = Files.readString(Path.of("src/main/resources/db/migration/V849__security_guard_face_consent.sql"));
        assertTrue(m.contains("face_consent_at TIMESTAMPTZ"));
    }
    @Test void controllerAndApiKeyRules() throws Exception {
        String c = read("api/GuardController.java");
        assertTrue(c.contains("@DeleteMapping(\"/{id}/face\")"));
        assertTrue(c.contains("@GetMapping(\"/{id}/face\")"));
        assertTrue(read("infrastructure/ApiKeyAccess.java").contains("revoke-tokens|reveal|face)"));
    }
    @Test void bulkAccessConstructorStillExists() throws Exception {
        String d = read("dto/GuardEnrollRequest.java");
        assertTrue(d.contains("Boolean faceConsent"));
        assertTrue(d.contains("public GuardEnrollRequest(String pin, String faceEmbeddingBase64, String deviceHardwareId, Integer pinExpiryDays)"));
    }
}
