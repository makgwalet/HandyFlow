package za.co.handyflow.platform.legalcompliance.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Plain JUnit on the library entities' own state rules. */
class RegulatoryLibraryDomainTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID instrument = UUID.randomUUID();

    private RegulatoryVersion draft() {
        return RegulatoryVersion.createDraft(tenant, instrument, "2026 consolidation", LocalDate.of(2026, 1, 1), null, null, null, UUID.randomUUID());
    }

    @Test
    @DisplayName("a new version starts as DRAFT and can be edited")
    void draftEditable() {
        RegulatoryVersion v = draft();
        assertEquals(VersionStatus.DRAFT, v.getStatus());
        v.editDraft("Renamed", LocalDate.of(2026, 2, 1), null, null, "s");
        assertEquals("Renamed", v.getVersionLabel());
    }

    @Test
    @DisplayName("publishing needs an effective date")
    void publishNeedsEffectiveDate() {
        RegulatoryVersion v = RegulatoryVersion.createDraft(tenant, instrument, "x", null, null, null, null, null);
        assertThrows(IllegalStateException.class, () -> v.publish(UUID.randomUUID(), "A"));
    }

    @Test
    @DisplayName("a published version is CURRENT and can no longer be edited")
    void publishedIsImmutable() {
        RegulatoryVersion v = draft();
        v.publish(UUID.randomUUID(), "Amina");
        assertEquals(VersionStatus.CURRENT, v.getStatus());
        assertNotNull(v.getPublishedAt());
        assertThrows(IllegalStateException.class, () -> v.editDraft("x", LocalDate.now(), null, null, null));
        assertThrows(IllegalStateException.class, () -> v.publish(UUID.randomUUID(), "again"));
    }

    @Test
    @DisplayName("only a CURRENT version can be superseded; only a published one repealed")
    void supersedeAndRepeal() {
        RegulatoryVersion v = draft();
        assertThrows(IllegalStateException.class, v::supersede);
        assertThrows(IllegalStateException.class, () -> v.repeal(LocalDate.now()));
        v.publish(UUID.randomUUID(), "A");
        v.supersede();
        assertEquals(VersionStatus.SUPERSEDED, v.getStatus());
        v.repeal(LocalDate.of(2026, 6, 1));
        assertEquals(VersionStatus.REPEALED, v.getStatus());
        assertEquals(LocalDate.of(2026, 6, 1), v.getRepealedDate());
    }

    @Test
    @DisplayName("carrying a requirement forward keeps the text and remembers where it came from")
    void carryForward() {
        RegulatoryRequirement r = RegulatoryRequirement.create(tenant, instrument, UUID.randomUUID(), "s19", "Register",
                "Register with the regulator", ObligationCategory.POPIA, RecurrenceInterval.ANNUALLY, 10,
                List.of("retail"), List.of("a"), "Certificate", null, UUID.randomUUID());
        UUID newVersion = UUID.randomUUID();
        RegulatoryRequirement copy = r.carryForwardTo(newVersion, UUID.randomUUID());
        assertNotEquals(r.getId(), copy.getId());
        assertEquals(newVersion, copy.getVersionId());
        assertEquals(r.getId(), copy.getPreviousRequirementId());
        assertEquals(r.getRequirementText(), copy.getRequirementText());
        assertEquals(List.of("RETAIL"), copy.getIndustries());
    }

    @Test
    @DisplayName("industries and activities are normalised: upper-cased, trimmed, de-duplicated, blanks dropped")
    void tagsNormalised() {
        RegulatoryRequirement r = RegulatoryRequirement.create(tenant, instrument, UUID.randomUUID(), "r", "t", "text long enough",
                ObligationCategory.OTHER, null, null, java.util.Arrays.asList(" retail", "RETAIL", "", null, "mining"), null, null, null, null);
        assertEquals(List.of("RETAIL", "MINING"), r.getIndustries());
        assertEquals(RecurrenceInterval.ANNUALLY, r.getRecurrence());
    }

    @Test
    @DisplayName("editing an instrument clears its review status; platform rows are identified by a null tenant")
    void instrumentReviewReset() {
        RegulatoryInstrument i = RegulatoryInstrument.create(tenant, "Act 1", "Title", InstrumentType.ACT, JurisdictionLevel.NATIONAL,
                null, ObligationCategory.OTHER, null, null, null, null);
        i.markReviewed(UUID.randomUUID(), "Reviewer");
        assertTrue(i.isReviewed());
        i.update("Act 1", "Title 2", InstrumentType.ACT, JurisdictionLevel.NATIONAL, null, ObligationCategory.OTHER, null, null);
        assertFalse(i.isReviewed());
        assertNull(i.getReviewedAt());
        assertFalse(i.isPlatform());
        assertTrue(RegulatoryInstrument.create(null, "A", "T", InstrumentType.ACT, JurisdictionLevel.NATIONAL, null,
                ObligationCategory.OTHER, null, null, null, null).isPlatform());
    }
}
