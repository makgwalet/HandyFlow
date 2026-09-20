package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for EmailTemplates.contractRenewalReminder — migrated
 * from ContractExpiryScheduler.buildRenewalBody (one of the 9 files with
 * independent inline email HTML flagged in PLATFORM-ENGINES-PROGRESS.md).
 * Safe to migrate onto plain wrap(): the original already showed a
 * literal "HandyFlow" header identical to wrap()'s own, so this changes
 * zero branding, only deduplicates the CSS and fixes two unescaped
 * fields.
 */
class EmailTemplatesContractRenewalReminderTest {

    @Test
    @DisplayName("renders contract details and uses wrap()'s HandyFlow header, matching the original")
    void rendersDetails() {
        String html = EmailTemplates.contractRenewalReminder("Sample NDA", "CTR-00001", 15, "5 Oct 2026");

        assertThat(html).contains("Sample NDA");
        assertThat(html).contains("CTR-00001");
        assertThat(html).contains("Expires in <strong>15 days</strong>");
        assertThat(html).contains("5 Oct 2026");
        assertThat(html).contains("<h1>HandyFlow</h1>");
    }

    @Test
    @DisplayName("singular 'day' when daysLeft is 1")
    void singularDay() {
        String html = EmailTemplates.contractRenewalReminder("Sample NDA", "CTR-00001", 1, "21 Sep 2026");
        assertThat(html).contains("Expires in <strong>1 day</strong>");
    }

    @Test
    @DisplayName("escapes title and number")
    void escapesTitleAndNumber() {
        String payload = "<script>alert(1)</script>";
        String html = EmailTemplates.contractRenewalReminder(payload, payload, 15, "5 Oct 2026");

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
