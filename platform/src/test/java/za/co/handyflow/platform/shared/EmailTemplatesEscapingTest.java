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

    // ── Third batch: a wider manual scan of every "public static String"
    // method in this file (not just the two batches above) turned up 19
    // more of the same bug across the auth, contracting, and property/lease
    // modules. Same fix pattern throughout; each verified against the
    // original .formatted() call to confirm argument order/count was
    // preserved.

    @Test
    @DisplayName("passwordReset escapes firstName")
    void passwordReset_escapesFirstName() {
        String html = EmailTemplates.passwordReset(PAYLOAD, "https://example.com/reset");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("passwordChanged escapes firstName")
    void passwordChanged_escapesFirstName() {
        String html = EmailTemplates.passwordChanged(PAYLOAD);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("pilotCountdown escapes firstName")
    void pilotCountdown_escapesFirstName() {
        String html = EmailTemplates.pilotCountdown(PAYLOAD, 5, "Pro", "https://example.com");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("contractSigningInvitation escapes partyName")
    void contractSigningInvitation_escapesPartyName() {
        String html = EmailTemplates.contractSigningInvitation(
                PAYLOAD, "Sample NDA", "CTR-00001", "NON-DISCLOSURE AGREEMENT", "https://example.com/sign");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("contractFullyExecuted escapes partyName")
    void contractFullyExecuted_escapesPartyName() {
        String html = EmailTemplates.contractFullyExecuted(
                PAYLOAD, "Sample NDA", "CTR-00001", "1 Jan 2026", "https://example.com");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("contractTerminated escapes partyName")
    void contractTerminated_escapesPartyName() {
        String html = EmailTemplates.contractTerminated(
                PAYLOAD, "Sample NDA", "CTR-00001", "No longer needed", "1 Jan 2026", "https://example.com");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("contractDeclined escapes ownerName and partyName")
    void contractDeclined_escapesNames() {
        String html = EmailTemplates.contractDeclined(
                PAYLOAD, PAYLOAD, "Sample NDA", "CTR-00001", "Not agreed", "https://example.com");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("contractAmendmentRequested escapes ownerName and partyName")
    void contractAmendmentRequested_escapesNames() {
        String html = EmailTemplates.contractAmendmentRequested(
                PAYLOAD, PAYLOAD, "Sample NDA", "CTR-00001", "Clause 4", "Please revise", "https://example.com");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("contractSigningTurnNotification escapes partyName")
    void contractSigningTurnNotification_escapesPartyName() {
        String html = EmailTemplates.contractSigningTurnNotification(
                PAYLOAD, "Sample NDA", "CTR-00001", "https://example.com/sign");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("leaseCreated escapes lesseeName and propertyName")
    void leaseCreated_escapesNames() {
        String html = EmailTemplates.leaseCreated(
                PAYLOAD, PAYLOAD, "Unit 4B", "1 Jan 2026", "31 Dec 2026", "8,500.00", 1);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("leaseTerminated escapes lesseeName and reason")
    void leaseTerminated_escapesLesseeNameAndReason() {
        String html = EmailTemplates.leaseTerminated(PAYLOAD, PAYLOAD);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("leaseRenewed escapes lesseeName")
    void leaseRenewed_escapesLesseeName() {
        String html = EmailTemplates.leaseRenewed(PAYLOAD, "31 Dec 2027", "9,000.00");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("leaseExpiringTenant escapes lesseeName and propertyName")
    void leaseExpiringTenant_escapesNames() {
        String html = EmailTemplates.leaseExpiringTenant(PAYLOAD, PAYLOAD, "Unit 4B", "31 Dec 2026", 30);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("leaseExpiringLandlord escapes lesseeName and propertyName")
    void leaseExpiringLandlord_escapesNames() {
        String html = EmailTemplates.leaseExpiringLandlord(PAYLOAD, PAYLOAD, "Unit 4B", "31 Dec 2026", 30);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("rentEscalation escapes lesseeName")
    void rentEscalation_escapesLesseeName() {
        String html = EmailTemplates.rentEscalation(PAYLOAD, "8,000.00", "8,500.00", "1 Mar 2026");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("rentReceipt escapes lesseeName")
    void rentReceipt_escapesLesseeName() {
        String html = EmailTemplates.rentReceipt(PAYLOAD, "8,500.00", "March 2026", "1 Mar 2026", "REF123");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("rentPartialPayment escapes lesseeName")
    void rentPartialPayment_escapesLesseeName() {
        String html = EmailTemplates.rentPartialPayment(
                PAYLOAD, "4,000.00", "4,500.00", "March 2026", "1 Mar 2026", "REF123");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("rentOverdueReminder escapes lesseeName")
    void rentOverdueReminder_escapesLesseeName() {
        String html = EmailTemplates.rentOverdueReminder(PAYLOAD, "8,500.00", "March 2026", "5");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("documentRequestCreated escapes firmName and description")
    void documentRequestCreated_escapesFirmNameAndDescription() {
        String html = EmailTemplates.documentRequestCreated(
                "Some Client", PAYLOAD, PAYLOAD, List.of("ID document"), "31 Jan 2026");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }
}
