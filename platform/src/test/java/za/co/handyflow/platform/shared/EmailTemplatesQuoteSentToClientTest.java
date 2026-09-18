package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reference-migration test: quoteSentToClient is the first of 46
 * EmailTemplates methods migrated to accept a tenant signature (see
 * PLATFORM-ENGINES-PROGRESS.md, Email Engine). Confirms the additive
 * overload doesn't change output for the un-migrated call shape and
 * correctly appends/omits the signature fragment.
 */
class EmailTemplatesQuoteSentToClientTest {

    @Test
    @DisplayName("5-arg overload (no signature) renders identically to before this change")
    void fiveArgOverload_hasNoSignatureMarkup() {
        String html = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00", "https://example.com/q/abc");

        assertThat(html).contains("Please find attached your quote from");
        assertThat(html).doesNotContain("Kind regards");
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
    }

    @Test
    @DisplayName("6-arg overload tolerates a null signatureHtml without throwing")
    void sixArgOverload_nullSignature_doesNotThrow() {
        String html = EmailTemplates.quoteSentToClient(
                "Jane", "QT-00001", "FastPrint Solutions", "R 1,000.00",
                "https://example.com/q/abc", null);

        assertThat(html).contains("Please find attached your quote from");
    }
}
