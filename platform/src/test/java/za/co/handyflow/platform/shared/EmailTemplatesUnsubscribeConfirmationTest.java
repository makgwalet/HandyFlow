package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for EmailTemplates.unsubscribeConfirmation — migrated from
 * MarketingService's own inline HTML now that tenant branding is a
 * resolved platform decision (strategic roadmap backlog, Part 0,
 * Decision 2) and marketing's module boundary was widened specifically
 * to allow it. Correctly built on wrapForTenant() — this confirmation is
 * sent from the tenant's own marketing list.
 */
class EmailTemplatesUnsubscribeConfirmationTest {

    @Test
    @DisplayName("shows the tenant's own company name in the header, not HandyFlow")
    void showsTenantBrandedHeader() {
        String html = EmailTemplates.unsubscribeConfirmation("Acme Ltd", "Jane Doe");

        assertThat(html).contains("<h1>Acme Ltd</h1>");
        assertThat(html).doesNotContain("<h1>HandyFlow</h1>");
        assertThat(html).contains("You've been unsubscribed.");
    }

    @Test
    @DisplayName("greets the contact by first name when one is given")
    void greetsContactByFirstName() {
        String html = EmailTemplates.unsubscribeConfirmation("Acme Ltd", "Jane Doe");
        assertThat(html).contains("Hi Jane,");
    }

    @Test
    @DisplayName("falls back to a bare greeting when no contact name is given")
    void fallsBackToBareGreeting() {
        String html = EmailTemplates.unsubscribeConfirmation("Acme Ltd", null);
        assertThat(html).contains("<p>Hi,</p>");
    }

    @Test
    @DisplayName("escapes the contact's name and the tenant's company name")
    void escapesNames() {
        String payload = "<script>alert(1)</script>";
        String html = EmailTemplates.unsubscribeConfirmation(payload, payload);

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
