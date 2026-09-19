package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reference-migration test: quoteSentToClient is the first of 46
 * EmailTemplates methods migrated to accept a tenant signature AND to use
 * the tenant-branded wrapForTenant() header instead of wrap()'s
 * "HandyFlow" header (see PLATFORM-ENGINES-PROGRESS.md, Email Engine).
 * Confirms both overloads now show the tenant's own name in the header,
 * and that the signature fragment is correctly appended/omitted.
 */
class EmailTemplatesQuoteSentToClientTest {

    @Test
    @DisplayName("5-arg overload shows the tenant's company name in the header, not HandyFlow")
    void fiveArgOverload_showsTenantBrandedHeader() {
        String html = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00", "https://example.com/q/abc");

        assertThat(html).contains("Please find attached your quote from");
        assertThat(html).contains("<h1>FastPrint Solutions</h1>");
        assertThat(html).doesNotContain("<h1>HandyFlow</h1>");
        assertThat(html).doesNotContain("Kind regards");
        // Footer still credits the underlying platform — that's a
        // deliberate design choice (see wrapForTenant's Javadoc), not an
        // oversight; only the *header* branding changed.
        assertThat(html).contains("Powered by");
    }

    @Test
    @DisplayName("6-arg overload with empty signatureHtml renders identically to the 5-arg overload")
    void sixArgOverload_withEmptySignature_matchesFiveArgOverload() {
        String withEmpty = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00", "https://example.com/q/abc", "");
        String fiveArg = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00", "https://example.com/q/abc");

        assertThat(withEmpty).isEqualTo(fiveArg);
    }

    @Test
    @DisplayName("6-arg overload with a signature fragment appends it into the body")
    void sixArgOverload_withSignature_appendsIt() {
        String signature = "<div>Kind regards,<br/>Thabang Makgwale</div>";

        String html = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00",
                "https://example.com/q/abc", signature);

        assertThat(html).contains(signature);
        assertThat(html).contains("Please find attached your quote from");
        assertThat(html).contains("<h1>FastPrint Solutions</h1>");
    }

    @Test
    @DisplayName("6-arg overload tolerates a null signatureHtml without throwing")
    void sixArgOverload_nullSignature_doesNotThrow() {
        String html = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00",
                "https://example.com/q/abc", null);

        assertThat(html).contains("Please find attached your quote from");
    }

    @Test
    @DisplayName("company name in the header is HTML-escaped")
    void headerEscapesCompanyName() {
        String html = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "<script>alert(1)</script>", "R 1,000.00", "https://example.com/q/abc");

        assertThat(html).doesNotContain("<h1><script>");
        assertThat(html).contains("&lt;script&gt;");
    }
}
