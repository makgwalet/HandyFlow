package za.co.handyflow.platform.fuel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** F01: licence linkage and evidence. */
class FuelLicenceLinkageTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    static FuelComplianceItem licence() {
        return FuelComplianceItem.create(T, "LICENCE", "Wholesale licence", "WL-1", "COMPANY", null, "Company", null, LocalDate.now().plusDays(90), null);
    }

    @Test @DisplayName("a new item is not linked to anything, so existing items behave as before")
    void unlinkedByDefault() {
        FuelComplianceItem i = licence();
        assertNull(i.getLegalEntityId()); assertNull(i.getLicensedActivity()); assertNull(i.getIssuingAuthority());
    }

    @Test @DisplayName("scope links an entity, an activity (any case) and an authority; blanks become null")
    void applyScope() {
        FuelComplianceItem i = licence();
        UUID e = UUID.randomUUID();
        i.applyScope(e, "wholesale", "  Regulator X ");
        assertEquals(e, i.getLegalEntityId()); assertEquals("WHOLESALE", i.getLicensedActivity()); assertEquals("Regulator X", i.getIssuingAuthority());
        i.applyScope(null, " ", " ");
        assertNull(i.getLegalEntityId()); assertNull(i.getLicensedActivity()); assertNull(i.getIssuingAuthority());
    }

    @Test @DisplayName("an unknown activity is refused")
    void unknownActivity() {
        assertThrows(IllegalArgumentException.class, () -> licence().applyScope(null, "MINING", null));
    }

    @Test @DisplayName("evidence needs a file reference; it is trimmed")
    void evidenceNeedsRef() {
        assertThrows(IllegalArgumentException.class, () -> FuelComplianceEvidence.create(T, UUID.randomUUID(), " ", null, USER));
        FuelComplianceEvidence e = FuelComplianceEvidence.create(T, UUID.randomUUID(), " scans/wl1.pdf ", " ", USER);
        assertEquals("scans/wl1.pdf", e.getFileRef()); assertNull(e.getNote()); assertTrue(e.isActive());
    }

    @Test @DisplayName("evidence is replaced, never edited: a reason is needed, and only once")
    void evidenceSupersede() {
        FuelComplianceEvidence e = FuelComplianceEvidence.create(T, UUID.randomUUID(), "scans/wl1.pdf", null, USER);
        assertThrows(IllegalArgumentException.class, () -> e.supersede("no", USER));
        assertTrue(e.isActive());
        e.supersede("Wrong scan uploaded", USER);
        assertFalse(e.isActive());
        assertEquals("Wrong scan uploaded", e.getSupersededReason());
        assertThrows(IllegalStateException.class, () -> e.supersede("Wrong scan again", USER));
    }
}
