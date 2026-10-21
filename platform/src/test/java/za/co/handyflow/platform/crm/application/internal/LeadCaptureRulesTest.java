package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.LeadCaptureRules.Clean;
import za.co.handyflow.platform.crm.domain.model.LeadSource;

class LeadCaptureRulesTest {
    private static Clean clean(String name, String company, String email, String phone, String message, String source) {
        return LeadCaptureRules.clean(name, company, email, phone, message, source, LeadSource.WEBSITE);
    }
    private static void refuses(Runnable r, String msg) { assertThatThrownBy(r::run).isInstanceOf(IllegalArgumentException.class).hasMessage(msg); }

    @Test
    void aPersonSendsAName() {
        var c = clean("  Thandi  ", null, " Thandi@Example.COM ", "082 123 4567", "Need a quote", null);
        assertThat(c.recordName()).isEqualTo("Thandi");
        assertThat(c.email()).isEqualTo("thandi@example.com");
        assertThat(c.phone()).isEqualTo("082 123 4567");
        assertThat(c.notes()).contains("Message: Need a quote").doesNotContain("Contact:");
        assertThat(c.source()).isEqualTo(LeadSource.WEBSITE);
    }

    @Test
    void aCompanyBecomesTheRecordAndThePersonIsKeptInTheNotes() {
        var c = clean("Thandi", "Cape Wines", null, null, null, null);
        assertThat(c.recordName()).isEqualTo("Cape Wines");
        assertThat(c.notes()).contains("Contact: Thandi");
        assertThat(clean(null, "Cape Wines", null, null, null, null).recordName()).isEqualTo("Cape Wines");
    }

    @Test
    void badInputIsRefusedWithAReadableReason() {
        refuses(() -> clean(null, "  ", null, null, null, null), "Send a name");
        refuses(() -> clean("x".repeat(256), null, null, null, null, null), "The name can be at most 255 characters");
        refuses(() -> clean("A", null, "not-an-email", null, null, null), "That email address is not valid");
        refuses(() -> clean("A", null, null, "12345", null, null), "Phone must start with + or 0 and have 7 to 20 digits, spaces or hyphens");
        refuses(() -> clean("A", null, null, null, "x".repeat(2001), null), "The message can be at most 2000 characters");
        clean("A", null, null, "+27 82 123 4567", "x".repeat(2000), null);
    }

    @Test
    void theSenderMaySayWhereTheLeadCameFromOtherwiseTheKeyDecides() {
        assertThat(LeadCaptureRules.sourceOf("referral", LeadSource.WEBSITE)).isEqualTo(LeadSource.REFERRAL);
        assertThat(LeadCaptureRules.sourceOf("Social Media", LeadSource.WEBSITE)).isEqualTo(LeadSource.SOCIAL_MEDIA);
        assertThat(LeadCaptureRules.sourceOf("walk-in", LeadSource.WEBSITE)).isEqualTo(LeadSource.WALK_IN);
        assertThat(LeadCaptureRules.sourceOf("carrier pigeon", LeadSource.PARTNER)).isEqualTo(LeadSource.PARTNER);
        assertThat(LeadCaptureRules.sourceOf(null, null)).isEqualTo(LeadSource.WEBSITE);
    }

    @Test
    void aFilledInRobotTrapIsSpamAndKeyNamesAreChecked() {
        assertThat(LeadCaptureRules.isSpam(null)).isFalse();
        assertThat(LeadCaptureRules.isSpam("  ")).isFalse();
        assertThat(LeadCaptureRules.isSpam("http://spam.example")).isTrue();
        refuses(() -> LeadCaptureRules.checkKeyName(" "), "Name the key");
        refuses(() -> LeadCaptureRules.checkKeyName("x".repeat(121)), "The name can be at most 120 characters");
        LeadCaptureRules.checkKeyName("Website contact form");
    }
}
