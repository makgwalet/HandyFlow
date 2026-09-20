package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test found while checking ScmNotificationService as a
 * candidate for the EmailTemplates shared-template migration: this file
 * was NOT migrated (its own explicit "HandyFlow · Supply Chain" amber
 * eyebrow label is very likely an intentional sub-brand, matching the
 * caution already flagged in PLATFORM-ENGINES-PROGRESS.md), but its own
 * kv(key, value) helper escaped key via its own esc() helper and never
 * escaped value — the exact place the real risk is, since supplierName,
 * a free-text "Reason" field, and "Approved By" are all genuinely
 * user-entered strings passed through it. Same bug category as the 34
 * already found and fixed in EmailTemplates this session.
 */
class ScmNotificationServiceEscapingTest {

    @Test
    @DisplayName("kv() escapes both key and value")
    void kvEscapesKeyAndValue() {
        String payload = "<script>alert(1)</script>";

        String html = ScmNotificationService.kv(payload, payload);

        assertThat(html).doesNotContain(payload);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    @DisplayName("kv() renders a plain key/value pair correctly when there's nothing to escape")
    void kvRendersPlainValues() {
        String html = ScmNotificationService.kv("Supplier", "Acme Building Supplies");

        assertThat(html).contains("Supplier");
        assertThat(html).contains("Acme Building Supplies");
    }
}
