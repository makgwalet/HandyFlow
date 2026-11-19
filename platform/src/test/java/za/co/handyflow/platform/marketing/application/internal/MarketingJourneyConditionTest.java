package za.co.handyflow.platform.marketing.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketingJourneyConditionTest {
    @Test void every_condition_has_readable_text() {
        for (String c : new String[]{"CLICKED", "OPENED", "PURCHASED"}) {
            String t = MarketingJourneyService.conditionText(c);
            assertNotNull(t);
            assertFalse(t.isBlank(), c);
        }
    }
    @Test void purchased_text_does_not_talk_about_emails() {
        assertFalse(MarketingJourneyService.conditionText("PURCHASED").toLowerCase().contains("email"));
    }
    @Test void csv_cells_are_quoted_and_formula_safe() {
        assertEquals("\"=cmd\"".replace("=", "'="), MarketingAudienceService.cell("=cmd"));
        assertEquals("\"a\"\"b\"", MarketingAudienceService.cell("a\"b"));
        assertEquals("\"\"", MarketingAudienceService.cell(null));
    }
}
