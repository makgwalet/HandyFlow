package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test found while checking PmNotificationService as a
 * candidate for the EmailTemplates shared-template migration: this file
 * was NOT migrated (its own explicit "HandyFlow · Project Management"
 * eyebrow label, same as ScmNotificationService's confirmed intentional
 * "Supply Chain" sub-brand). Its own kv(key, value) helper had the exact
 * same bug ScmNotificationService's did: key was escaped via esc(), value
 * never was — and value is where the real risk is (coNumber, approvedBy,
 * milestoneTitle, riskTitle, rfiNumber, rfiTitle, respondedBy are all
 * genuinely user-entered strings). One call site (the risk "Rating"
 * badge) legitimately needs to pass through pre-built HTML and was moved
 * to a separate kvRawValue() helper instead of being broken by the fix.
 */
class PmNotificationServiceEscapingTest {

    @Test
    @DisplayName("kv() escapes both key and value")
    void kvEscapesKeyAndValue() {
        String payload = "<script>alert(1)</script>";

        String html = PmNotificationService.kv(payload, payload);

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("kv() renders a plain key/value pair correctly when there's nothing to escape")
    void kvRendersPlainValues() {
        String html = PmNotificationService.kv("Change Order", "CO-00012");

        assertThat(html).contains("Change Order");
        assertThat(html).contains("CO-00012");
    }
}
