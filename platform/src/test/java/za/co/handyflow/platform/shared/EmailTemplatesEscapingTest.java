package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real, systemic bug found while continuing the
 * Email Engine work: several {@code EmailTemplates} methods interpolated
 * a client/company/first name directly into HTML with no escaping at
 * all — unlike the majority of this file, which already escapes
 * tenant-entered strings consistently (see {@code quoteSentToClient} for
 * the established pattern). A maliciously- or just carelessly-named
 * client/contact/company record (e.g. containing a stray {@code <a href=}
 * or a script tag) would have rendered as live HTML in a real customer's
 * inbox — a genuine content-injection / phishing-enablement risk, not a
 * cosmetic one.
 * <p>
 * Fixed in this pass: {@code feeNote}, {@code paymentReceived},
 * {@code clientOnboardingWelcome}, {@code invoiceGeneratedWithPdf},
 * {@code registrationConfirmation}. These were the five confirmed by
 * direct code read; a broader automated scan of this file turned up
 * more candidates but with enough false positives (nested braces in CSS
 * text blocks confuse a simple method-boundary heuristic) that they're
 * tracked as unverified backlog in PLATFORM-ENGINES-PROGRESS.md rather
 * than assumed fixed or assumed broken.
 */
class EmailTemplatesEscapingTest {

    private static final String PAYLOAD = "<script>alert(1)</script>";
    private static final String ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;";

    @Test
    @DisplayName("feeNote escapes clientName")
    void feeNote_escapesClientName() {
        String html = EmailTemplates.feeNote(PAYLOAD, "FN-00001", "1,000.00", "2026-01-31");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("paymentReceived escapes clientName")
    void paymentReceived_escapesClientName() {
        String html = EmailTemplates.paymentReceived(PAYLOAD, "INV-00001", "1,000.00", "2026-01-15");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("clientOnboardingWelcome escapes clientName and firmName")
    void clientOnboardingWelcome_escapesNames() {
        String html = EmailTemplates.clientOnboardingWelcome(PAYLOAD, PAYLOAD, "hello@example.com");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("invoiceGeneratedWithPdf escapes companyName and customerName")
    void invoiceGeneratedWithPdf_escapesNames() {
        String html = EmailTemplates.invoiceGeneratedWithPdf(PAYLOAD, "INV-00001", PAYLOAD, "1,000.00");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("registrationConfirmation escapes firstName and companyName")
    void registrationConfirmation_escapesNames() {
        String html = EmailTemplates.registrationConfirmation(
                PAYLOAD, PAYLOAD, "fastprint", List.of("CRM"), "https://example.com/verify");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    // ── Second batch: found while verifying the 10 candidates flagged in the
    // previous pass as unverified. Of those 10, 5 were false positives
    // (userInvitation, accountSuspended, planChanged, quoteExpiry,
    // invoiceGenerated already escape correctly) and 5 were real, same bug.

    @Test
    @DisplayName("taxDeadlineReminder escapes clientName")
    void taxDeadlineReminder_escapesClientName() {
        String html = EmailTemplates.taxDeadlineReminder(
                PAYLOAD, "VAT201", "2026-02-28", 5, 2026, 1);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("clientDeadlineReminder escapes firmName")
    void clientDeadlineReminder_escapesFirmName() {
        String html = EmailTemplates.clientDeadlineReminder(PAYLOAD, "VAT201", "2026-02-28", 5);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("tcsPinExpiryReminder escapes clientName")
    void tcsPinExpiryReminder_escapesClientName() {
        String html = EmailTemplates.tcsPinExpiryReminder(PAYLOAD, "2026-02-28", 5);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("ficaDocumentExpiryReminder escapes clientName and fileName")
    void ficaDocumentExpiryReminder_escapesNames() {
        String html = EmailTemplates.ficaDocumentExpiryReminder(
                PAYLOAD, "ID_COPY", PAYLOAD, "2026-02-28", 5);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("portalInvite escapes clientName and firmName")
    void portalInvite_escapesNames() {
        String html = EmailTemplates.portalInvite(PAYLOAD, PAYLOAD, "https://example.com/invite");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }
}
