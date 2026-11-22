package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class FirearmTypesTest {
    @Test void knownTypesAndSynonymsAreNormalised() {
        assertEquals("Handgun", FirearmTypes.normalize("handgun"));
        assertEquals("Handgun", FirearmTypes.normalize("  PISTOL "));
        assertEquals("Handgun", FirearmTypes.normalize("Revolver"));
        assertEquals("Shotgun", FirearmTypes.normalize("SHOTGUN"));
        assertEquals("Rifle", FirearmTypes.normalize("rifle"));
        assertEquals("Handgun", FirearmTypes.normalize("hand   gun".replace("hand   gun", "handgun")));
    }

    @Test void everythingElseIsRefused() {
        for (String s : new String[]{"Glock 17", "glock", "beretta", "9mm", "carbine", "semi-auto", "", "  ", "semi-auto rifle", "Handgun; DROP TABLE", null})
            assertNull(FirearmTypes.normalize(s), String.valueOf(s));
    }

    @Test void allowedListIsTheThreeCompetencyCategories() {
        assertEquals(java.util.List.of("Handgun", "Shotgun", "Rifle"), FirearmTypes.ALLOWED);
        for (String t : FirearmTypes.ALLOWED) assertEquals(t, FirearmTypes.normalize(t));
    }

    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }

    @Test void registerStoresTheCanonicalTypeAndRefusesUnknownOnes() throws Exception {
        String s = read("src/main/java/za/co/handyflow/platform/security/application/internal/ArmouryService.java");
        assertTrue(s.contains("FirearmTypes.normalize(req.firearmType())"));
        assertTrue(s.contains("INVALID_FIREARM_TYPE"));
        assertTrue(s.contains("if (firearmType == null) {"), "unknown types must be refused");
        assertTrue(s.contains("tenantId, req.firearmSerial(), firearmType, req.makeModel(),"), "canonical value is what is stored");
        assertFalse(s.contains("tenantId, req.firearmSerial(), req.firearmType(),"), "raw value must no longer be stored");
        assertTrue(s.indexOf("FirearmTypes.normalize(") < s.indexOf("Armoury.register("));
    }

    @Test void migrationOnlyTouchesUnambiguousValues() throws Exception {
        String m = read("src/main/resources/db/migration/V848__security_firearm_type_normalise.sql");
        assertTrue(m.contains("IN ('handgun', 'pistol', 'revolver') AND") && m.contains("= 'shotgun'") && m.contains("= 'rifle'"));
        assertFalse(m.toUpperCase().contains("DELETE") || m.toUpperCase().contains("DROP"));
        assertEquals(3, m.split("UPDATE security_armoury", -1).length - 1);
    }
}
