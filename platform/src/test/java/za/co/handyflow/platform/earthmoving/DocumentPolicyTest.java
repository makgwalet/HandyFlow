package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.earthmoving.domain.model.DocumentPolicy;
import za.co.handyflow.platform.earthmoving.domain.model.DocumentPolicy.Stage;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentPolicyTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test void ownersAndCategoriesAreChecked() {
        assertEquals("EARTH_ASSET", DocumentPolicy.owner(" asset "));
        assertEquals("EARTH_OPERATOR", DocumentPolicy.owner("OPERATOR"));
        assertThrows(IllegalArgumentException.class, () -> DocumentPolicy.owner("SITE"));
        assertEquals("INSURANCE", DocumentPolicy.category("EARTH_ASSET", "insurance"));
        assertThrows(IllegalArgumentException.class, () -> DocumentPolicy.category("EARTH_ASSET", "OPERATOR_CERTIFICATE"));
        assertEquals("OPERATOR_CERTIFICATE", DocumentPolicy.category("EARTH_OPERATOR", "OPERATOR_CERTIFICATE"));
        assertThrows(IllegalArgumentException.class, () -> DocumentPolicy.category("EARTH_OPERATOR", "PHOTO"));
        assertEquals("DISPOSAL_EVIDENCE", DocumentPolicy.category("EARTH_DISPOSAL", "DISPOSAL_EVIDENCE"));
        assertThrows(IllegalArgumentException.class, () -> DocumentPolicy.category("EARTH_DISPOSAL", "PHOTO"));
    }

    @Test void sensitiveCategoriesNeedAdminAndAnExpiryDate() {
        assertEquals("EARTHMOVING_ADMIN", DocumentPolicy.authority("INSURANCE", false));
        assertEquals("EARTHMOVING_ADMIN", DocumentPolicy.authority("OPERATOR_CERTIFICATE", true));
        assertEquals("EARTHMOVING_READ", DocumentPolicy.authority("MANUAL", false));
        assertEquals("EARTHMOVING_WRITE", DocumentPolicy.authority("PHOTO", true));
        assertThrows(IllegalArgumentException.class, () -> DocumentPolicy.requireExpiry("WARRANTY", null));
        assertEquals(TODAY, DocumentPolicy.requireExpiry("WARRANTY", TODAY));
        assertNull(DocumentPolicy.requireExpiry("MANUAL", null));
    }

    @Test void expiryStagesFollowTheWarningDays() {
        assertEquals(Stage.NONE, DocumentPolicy.stage(null, TODAY));
        assertEquals(Stage.NONE, DocumentPolicy.stage(TODAY.plusDays(31), TODAY));
        assertEquals(Stage.DUE_30, DocumentPolicy.stage(TODAY.plusDays(30), TODAY));
        assertEquals(Stage.DUE_30, DocumentPolicy.stage(TODAY.plusDays(8), TODAY));
        assertEquals(Stage.DUE_7, DocumentPolicy.stage(TODAY.plusDays(7), TODAY));
        assertEquals(Stage.DUE_7, DocumentPolicy.stage(TODAY, TODAY));
        assertEquals(Stage.EXPIRED, DocumentPolicy.stage(TODAY.minusDays(1), TODAY));
    }

    @Test void onlyExpiredInsuranceAndCertificatesAreCritical() {
        assertEquals("CRITICAL", DocumentPolicy.severity("INSURANCE", Stage.EXPIRED));
        assertEquals("CRITICAL", DocumentPolicy.severity("OPERATOR_CERTIFICATE", Stage.EXPIRED));
        assertEquals("WARNING", DocumentPolicy.severity("WARRANTY", Stage.EXPIRED));
        assertEquals("WARNING", DocumentPolicy.severity("INSURANCE", Stage.DUE_7));
    }

    @Test void theAlertKeyHoldsTheExpiryDateSoARenewalStartsClean() {
        UUID id = UUID.randomUUID();
        String a = DocumentPolicy.alertKey(id, TODAY, Stage.DUE_7);
        assertTrue(a.length() <= 120);
        assertNotEquals(a, DocumentPolicy.alertKey(id, TODAY.plusYears(1), Stage.DUE_7));
        assertNotEquals(a, DocumentPolicy.alertKey(id, TODAY, Stage.EXPIRED));
    }

    @Test void theWordingSaysHowLongIsLeft() {
        assertEquals("expires in 7 days (2026-10-27)", DocumentPolicy.describe(Stage.DUE_7, TODAY.plusDays(7), TODAY));
        assertEquals("expires today", DocumentPolicy.describe(Stage.DUE_7, TODAY, TODAY));
        assertEquals("expires in 1 day (2026-10-21)", DocumentPolicy.describe(Stage.DUE_7, TODAY.plusDays(1), TODAY));
        assertEquals("expired on 2026-10-10", DocumentPolicy.describe(Stage.EXPIRED, TODAY.minusDays(10), TODAY));
    }
}
