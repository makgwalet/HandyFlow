package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.CompetencyChecks.Cert;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompetencyChecksTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static Cert cert(String cat, int daysFromToday, boolean verified) { return new Cert(cat, TODAY.plusDays(daysFromToday), verified); }

    @Test void categoryFromRegisterText() {
        assertEquals("HANDGUN", CompetencyChecks.categoryOf("Handgun"));
        assertEquals("HANDGUN", CompetencyChecks.categoryOf("9mm Pistol"));
        assertEquals("HANDGUN", CompetencyChecks.categoryOf("Revolver .38"));
        assertEquals("SHOTGUN", CompetencyChecks.categoryOf("Pump Shotgun"));
        assertEquals("RIFLE", CompetencyChecks.categoryOf("Rifle"));
        assertEquals("RIFLE", CompetencyChecks.categoryOf("Semi-auto carbine"));
        assertNull(CompetencyChecks.categoryOf("Taser"));
        assertNull(CompetencyChecks.categoryOf(null));
    }

    @Test void parseCategory() {
        assertEquals("RIFLE", CompetencyChecks.parseCategory(" rifle "));
        assertNull(CompetencyChecks.parseCategory(" "));
        assertNull(CompetencyChecks.parseCategory(null));
        assertThrows(IllegalArgumentException.class, () -> CompetencyChecks.parseCategory("CANNON"));
    }

    @Test void matchingCategoryPasses() {
        assertNull(CompetencyChecks.check("HANDGUN", List.of(cert("HANDGUN", 30, false)), false, false, TODAY));
    }
    @Test void otherCategoryOnlyIsRefusedWithSpecificCode() {
        assertEquals("NO_COMPETENCY_FOR_CATEGORY",
                CompetencyChecks.check("RIFLE", List.of(cert("HANDGUN", 30, true)), false, false, TODAY));
    }
    @Test void nothingOnFileIsRefused() {
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check("RIFLE", List.of(), false, false, TODAY));
    }
    @Test void expiredCertificateDoesNotCount() {
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check("HANDGUN", List.of(cert("HANDGUN", -1, true)), false, false, TODAY));
    }
    @Test void expiringTodayStillCounts() {
        assertNull(CompetencyChecks.check("HANDGUN", List.of(cert("HANDGUN", 0, true)), false, false, TODAY));
    }
    @Test void certificateWithoutExpiryDoesNotCount() {
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check("HANDGUN", List.of(new Cert("HANDGUN", null, true)), false, false, TODAY));
    }
    @Test void legacyUncategorisedCertCoversEverythingWhenNotStrict() {
        assertNull(CompetencyChecks.check("SHOTGUN", List.of(cert(null, 30, false)), false, false, TODAY));
    }
    @Test void legacyGuardLevelDateCoversEverythingWhenNotStrict() {
        assertNull(CompetencyChecks.check("SHOTGUN", List.of(), true, false, TODAY));
    }
    @Test void unknownFirearmTypeNeedsAnyValidCertificate() {
        assertNull(CompetencyChecks.check(null, List.of(cert("HANDGUN", 30, false)), false, false, TODAY));
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check(null, List.of(), false, false, TODAY));
    }

    @Test void strictIgnoresUncategorisedCertificates() {
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check("HANDGUN", List.of(cert(null, 30, true)), false, true, TODAY));
    }
    @Test void strictIgnoresTheLegacyGuardLevelDate() {
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check("HANDGUN", List.of(), true, true, TODAY));
    }
    @Test void strictNeedsVerification() {
        assertEquals("NO_FIREARM_COMPETENCY", CompetencyChecks.check("HANDGUN", List.of(cert("HANDGUN", 30, false)), false, true, TODAY));
        assertNull(CompetencyChecks.check("HANDGUN", List.of(cert("HANDGUN", 30, true)), false, true, TODAY));
    }
    @Test void strictWrongCategoryVerifiedGivesCategoryCode() {
        assertEquals("NO_COMPETENCY_FOR_CATEGORY", CompetencyChecks.check("RIFLE", List.of(cert("HANDGUN", 30, true)), false, true, TODAY));
    }
}
