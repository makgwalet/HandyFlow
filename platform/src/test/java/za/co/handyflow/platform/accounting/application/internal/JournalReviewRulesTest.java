package za.co.handyflow.platform.accounting.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;

/** Finance patch F2: the maker of a journal cannot post it; one reversal per journal, posted by someone else. */
class JournalReviewRulesTest {
    UUID maker = UUID.randomUUID(), checker = UUID.randomUUID();

    @Test void makerCannotPost() { assertNotNull(JournalReviewRules.postProblem(maker, maker)); }
    @Test void anotherPersonMayPost() { assertNull(JournalReviewRules.postProblem(maker, checker)); }
    @Test void unknownPosterRefusedForPersonMadeJournal() { assertNotNull(JournalReviewRules.postProblem(maker, null)); }
    @Test void systemJournalNeedsNoSecondPerson() {
        assertNull(JournalReviewRules.postProblem(null, null));
        assertNull(JournalReviewRules.postProblem(null, checker));
    }
    @Test void onlyPostedJournalsReverse() {
        assertNotNull(JournalReviewRules.reversalProblem("DRAFT", null));
        assertNotNull(JournalReviewRules.reversalProblem("REVERSED", null));
        assertNull(JournalReviewRules.reversalProblem("POSTED", null));
    }
    @Test void secondReversalRefused() {
        String p = JournalReviewRules.reversalProblem("POSTED", "JE-0042");
        assertNotNull(p);
        assertTrue(p.contains("JE-0042"));
    }
    @Test void reversalPostsOnlyAgainstPostedOriginal() {
        assertNull(JournalReviewRules.postReversalProblem("POSTED"));
        assertNotNull(JournalReviewRules.postReversalProblem("REVERSED"));
        assertNotNull(JournalReviewRules.postReversalProblem(null));
    }
}
