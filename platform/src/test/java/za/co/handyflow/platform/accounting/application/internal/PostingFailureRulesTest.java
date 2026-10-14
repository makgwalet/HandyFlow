package za.co.handyflow.platform.accounting.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Finance patch F4: the failed-postings queue. */
class PostingFailureRulesTest {

    @Test void reasonIsTheRootCauseMessage() {
        Exception e = new RuntimeException("wrapper", new IllegalStateException("Journal does not balance"));
        assertEquals("Journal does not balance", PostingFailureRules.reason(e));
    }
    @Test void reasonFallsBackToTypeWhenNothingSaid() {
        assertEquals("NullPointerException", PostingFailureRules.reason(new NullPointerException()));
        assertEquals("outer", PostingFailureRules.reason(new RuntimeException("outer", new NullPointerException())));
        assertEquals("Unknown error", PostingFailureRules.reason(null));
    }
    @Test void reasonAndSummaryAreCut() {
        assertEquals(PostingFailureRules.MAX_REASON, PostingFailureRules.reason(new RuntimeException("x".repeat(5000))).length());
        assertEquals(PostingFailureRules.MAX_SUMMARY, PostingFailureRules.summary("y".repeat(900)).length());
        assertEquals("Ledger posting", PostingFailureRules.summary("  "));
    }
    @Test void moduleIsTheKeyPrefix() {
        assertEquals("invoicing", PostingFailureRules.module("invoicing:invoice-issued:1"));
        assertNull(PostingFailureRules.module(null));
    }
    @Test void retryNeedsOpenAndAJournal() {
        assertNull(PostingFailureRules.retryProblem("OPEN", true));
        assertNotNull(PostingFailureRules.retryProblem("OPEN", false));
        assertNotNull(PostingFailureRules.retryProblem("RESOLVED", true));
    }
    @Test void dismissNeedsOpenAndAReason() {
        assertNull(PostingFailureRules.dismissProblem("OPEN", "Posted by hand as JE-0042"));
        assertNotNull(PostingFailureRules.dismissProblem("OPEN", " ok "));
        assertNotNull(PostingFailureRules.dismissProblem("OPEN", null));
        assertNotNull(PostingFailureRules.dismissProblem("OPEN", "z".repeat(501)));
        assertNotNull(PostingFailureRules.dismissProblem("DISMISSED", "Posted by hand"));
    }
}
