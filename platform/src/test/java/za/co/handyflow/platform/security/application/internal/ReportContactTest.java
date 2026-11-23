package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.application.internal.ReportEmailRules.Contact;
import za.co.handyflow.platform.security.application.internal.ReportEmailRules.Content;
import za.co.handyflow.platform.security.application.internal.ReportEmailRules.Line;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportContactTest {

    private static final Content C = new Content("Head Office", "October 2026", "2026-10-01 to 2026-10-31", List.of(new Line("Cover", "100%")));

    private static String code(Runnable r) {
        try { r.run(); } catch (HandyFlowException e) { return e.getErrorCode(); }
        return null;
    }

    @Test
    void aReportCannotBeSentWithoutAContactAddress() {
        assertEquals("CONTACT_NOT_SET", code(() -> ReportEmailRules.requireContact(null)));
        assertEquals("CONTACT_NOT_SET", code(() -> ReportEmailRules.requireContact(new Contact("Ops", "", null))));
        assertEquals("CONTACT_NOT_SET", code(() -> ReportEmailRules.requireContact(new Contact("Ops", null, null))));
        assertNull(code(() -> ReportEmailRules.requireContact(new Contact(null, "ops@firm.co.za", null))));
    }

    @Test
    void theContactAddressIsCheckedLikeARecipient() {
        assertEquals("ops@firm.co.za", ReportEmailRules.cleanEmail("  Ops@Firm.co.za "));
        assertEquals("INVALID_EMAIL", code(() -> ReportEmailRules.cleanEmail("ops@firm")));
        assertEquals("INVALID_EMAIL", code(() -> ReportEmailRules.cleanEmail("a@b.co, c@d.co")));
    }

    @Test
    void nameAndPhoneAreOptionalButCleaned() {
        assertNull(ReportEmailRules.cleanContactName(null));
        assertNull(ReportEmailRules.cleanContactName("   "));
        assertEquals("Control Room", ReportEmailRules.cleanContactName("  Control   Room "));
        assertNull(ReportEmailRules.cleanPhone(null));
        assertNull(ReportEmailRules.cleanPhone(" "));
        assertEquals("+27 11 555 0100", ReportEmailRules.cleanPhone(" +27 11 555 0100 "));
        assertEquals("INVALID_PHONE", code(() -> ReportEmailRules.cleanPhone("call me <b>")));
        assertEquals("INVALID_PHONE", code(() -> ReportEmailRules.cleanPhone("123")));
    }

    @Test
    void theEmailEndsWithWhoToContact() {
        String h = ReportEmailRules.html(C, new Contact("Control Room", "ops@firm.co.za", "+27 11 555 0100"));
        assertTrue(h.contains("Questions about this report?"));
        assertTrue(h.contains("Control Room at <a href=\"mailto:ops@firm.co.za\">ops@firm.co.za</a> or +27 11 555 0100"));
        assertTrue(h.contains("Please do not reply to this email"));
        assertTrue(h.indexOf("Questions about this report?") > h.indexOf("</table>"));
    }

    @Test
    void nameAndPhoneMayBeMissing() {
        String h = ReportEmailRules.html(C, new Contact(null, "ops@firm.co.za", null));
        assertTrue(h.contains("Contact <a href=\"mailto:ops@firm.co.za\">ops@firm.co.za</a>."));
        assertFalse(h.contains(" at <a"));
        assertFalse(h.contains(" or "));
    }

    @Test
    void theContactIsEscaped() {
        String h = ReportEmailRules.html(C, new Contact("<script>x</script>", "ops@firm.co.za", "+27 (11) 555-0100"));
        assertFalse(h.contains("<script>"));
        assertTrue(h.contains("&lt;script&gt;"));
    }

    @Test
    void withoutAContactTheOldEmailIsUnchanged() {
        assertEquals(ReportEmailRules.html(C), ReportEmailRules.html(C, null));
        assertFalse(ReportEmailRules.html(C).contains("Questions about this report?"));
    }

    // ---- wiring, read from the source ----

    private static String src(String rel) throws Exception {
        for (String r : new String[]{"src/main", "platform/src/main", "work/src/main"}) {
            Path p = Path.of(r, rel);
            if (Files.exists(p)) return Files.readString(p);
        }
        fail("missing " + rel); return "";
    }

    private static final String SVC = "java/za/co/handyflow/platform/security/application/internal/MonthlyPackEmailService.java";
    private static final String CTL = "java/za/co/handyflow/platform/security/api/MonthlyPackEmailController.java";

    @Test
    void sendingRequiresTheContactBeforeAnythingIsSentAndUsesItInTheEmail() throws Exception {
        String s = src(SVC);
        int send = s.indexOf("Sent send(TenantId tenantId, UUID siteId, UUID actor, String monthAsked, Instant now)");
        assertTrue(send > 0);
        String m = s.substring(send);
        assertTrue(m.indexOf("ReportEmailRules.requireContact(contact)") > 0);
        assertTrue(m.indexOf("ReportEmailRules.requireContact(contact)") < m.indexOf("mail.send("));
        assertTrue(m.contains("ReportEmailRules.html(content, contact)"));
    }

    @Test
    void oneRefusedMessageIsRecordedAndDoesNotStopTheOthers() throws Exception {
        String m = src(SVC);
        assertTrue(m.contains("catch (RuntimeException e)"));
        assertTrue(m.contains("\"PACK_EMAIL_FAILED\""));
        assertTrue(m.contains("new Sent(to.size() - failed, p.month(), failed)"));
        assertFalse(m.contains("recipientId\\\":\\\"\" + r.email()"));   // the address itself is not written to the log
    }

    @Test
    void theContactIsPerCompanyAndAuditedAndManagerOnly() throws Exception {
        String s = src(SVC);
        assertTrue(s.contains("WHERE tenant_id = ?\","));
        assertTrue(s.contains("ON CONFLICT (tenant_id) DO UPDATE"));
        assertTrue(s.contains("'CONTACT_SET'"));
        String c = src(CTL);
        for (String ep : new String[]{"@GetMapping(\"/api/v1/security/report-settings\")", "@PutMapping(\"/api/v1/security/report-settings\")"}) {
            int i = c.indexOf(ep);
            assertTrue(i >= 0, ep);
            assertTrue(c.substring(i, i + 120).contains("@PreAuthorize(\"hasAuthority('SECURITY_MANAGE')\")"), ep);
        }
    }

    @Test
    void theTableKeepsOneRowPerCompanyAndAValidAddress() throws Exception {
        String m = src("resources/db/migration/V859__security_report_settings.sql");
        assertTrue(m.contains("tenant_id     UUID PRIMARY KEY"));
        assertTrue(m.contains("contact_email VARCHAR(254) NOT NULL"));
        assertTrue(m.contains("CHECK (contact_email ~"));
    }
}
