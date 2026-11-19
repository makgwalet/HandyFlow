package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Which clauses need the reviewer's signed-off review attached before they can be published. */
class ClauseEvidencePolicyTest {

    LocalDate today = LocalDate.now();

    private ContractClause approved(String category, String kind, String source) {
        ContractClause c = ContractClause.create(UUID.randomUUID(), "T", category, "<p>Reviewer wording.</p>", null, null, null);
        c.classify(kind, source);
        c.approve("A Attorney", "Admitted attorney", today.minusDays(1), null, null, today);
        return c;
    }

    @Test
    void anOrdinaryStandardClauseMayBePublishedWithoutEvidence() {
        ContractClause c = approved("DOMICILIUM", null, null);
        assertFalse(c.evidenceRequired());
        assertNull(c.evidenceReason());
        c.publish(today, false);
        assertEquals("PUBLISHED", c.getStatus());
    }

    @Test
    void customRegulatorySensitiveAndSourcedClausesNeedEvidence() {
        for (String kind : new String[]{"CUSTOM", "REGULATORY", "SENSITIVE"}) {
            ContractClause c = approved("OTHER", kind, null);
            assertTrue(c.evidenceRequired(), kind);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> c.publish(today, false), kind);
            assertTrue(e.getMessage().contains(kind.toLowerCase()), e.getMessage());
            assertEquals("APPROVED", c.getStatus(), "a refused publish changes nothing");
        }
        ContractClause sourced = approved("OTHER", "SOURCED", "Law Society precedent, 2024");
        assertThrows(IllegalStateException.class, () -> sourced.publish(today, false));
    }

    @Test
    void sensitiveCategoriesNeedEvidenceWhateverTheKind() {
        for (String category : new String[]{"DATA_PROTECTION", "LIABILITY"}) {
            ContractClause c = approved(category, "STANDARD", null);
            assertTrue(c.evidenceRequired(), category);
            assertThrows(IllegalStateException.class, () -> c.publish(today, false), category);
        }
    }

    @Test
    void withEvidenceOnFileTheyPublish() {
        ContractClause c = approved("LIABILITY", "CUSTOM", null);
        c.attachEvidence(UUID.randomUUID());
        c.publish(today);
        assertEquals("PUBLISHED", c.getStatus());
    }

    @Test
    void evidenceThatIsLinkedButNoLongerStoredDoesNotCount() {
        ContractClause c = approved("OTHER", "CUSTOM", null);
        c.attachEvidence(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> c.publish(today, false));
    }

    @Test
    void asourcedClauseSaysWhereItCameFrom() {
        ContractClause c = ContractClause.create(UUID.randomUUID(), "T", "OTHER", "<p>x</p>", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> c.classify("SOURCED", "  "));
        assertThrows(IllegalArgumentException.class, () -> c.classify("SOURCED", null));
        c.classify("SOURCED", " Industry body model clause ");
        assertEquals("Industry body model clause", c.getSourceReference());
    }

    @Test
    void anUnknownKindIsRejectedAndNoKindMeansStandard() {
        ContractClause c = ContractClause.create(UUID.randomUUID(), "T", "OTHER", "<p>x</p>", null, null, null);
        assertEquals("STANDARD", c.getClauseKind());
        assertThrows(IllegalArgumentException.class, () -> c.classify("FANCY", null));
        c.classify("custom", null);
        assertEquals("CUSTOM", c.getClauseKind());
        c.classify(" ", "ignored source");
        assertEquals("STANDARD", c.getClauseKind());
    }

    @Test
    void onlyADraftCanBeReclassifiedSoAReviewedClauseCannotBeDowngraded() {
        ContractClause c = approved("OTHER", "CUSTOM", null);
        assertThrows(IllegalStateException.class, () -> c.classify("STANDARD", null));
        assertEquals("CUSTOM", c.getClauseKind());
    }

    @Test
    void aNewVersionKeepsTheKindAndSource() {
        ContractClause c = approved("OTHER", "SOURCED", "Model clause");
        c.attachEvidence(UUID.randomUUID());
        c.publish(today);
        ContractClause next = c.newVersion(2, null);
        assertEquals("SOURCED", next.getClauseKind());
        assertEquals("Model clause", next.getSourceReference());
        assertNull(next.getEvidenceDocumentId(), "the new wording needs its own review evidence");
        assertTrue(next.evidenceRequired());
    }
}
