package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.identity.domain.model.TenantEmailSignature;
import za.co.handyflow.platform.identity.domain.repository.TenantEmailSignatureRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for TenantEmailBrandingEngine — no Spring context, matching
 * this codebase's established convention.
 */
@ExtendWith(MockitoExtension.class)
class TenantEmailBrandingEngineTest {

    @Mock
    private TenantEmailSignatureRepository signatureRepository;

    @InjectMocks
    private TenantEmailBrandingEngine engine;

    private final TenantId tenantId = TenantId.generate();

    @Test
    @DisplayName("returns empty string (never null) when no signature row exists")
    void noRow_returnsEmptyString() {
        when(signatureRepository.findByTenantId(tenantId.getValue())).thenReturn(Optional.empty());

        String result = engine.renderSignatureHtml(tenantId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty string when a row exists but is disabled — opt-in, not forced")
    void disabledRow_returnsEmptyString() {
        TenantEmailSignature sig = TenantEmailSignature.create(
                tenantId.getValue(), "Thabang Makgwale", "Managing Director",
                "012 555 1234", "info@fastprint.co.za", "www.fastprint.co.za");
        sig.setEnabled(false);
        when(signatureRepository.findByTenantId(tenantId.getValue())).thenReturn(Optional.of(sig));

        assertThat(engine.renderSignatureHtml(tenantId)).isEmpty();
    }

    @Test
    @DisplayName("enabled row renders name, title and contact line")
    void enabledRow_rendersAllFields() {
        TenantEmailSignature sig = TenantEmailSignature.create(
                tenantId.getValue(), "Thabang Makgwale", "Managing Director",
                "012 555 1234", "info@fastprint.co.za", "www.fastprint.co.za");
        when(signatureRepository.findByTenantId(tenantId.getValue())).thenReturn(Optional.of(sig));

        String html = engine.renderSignatureHtml(tenantId);

        assertThat(html)
                .contains("Kind regards")
                .contains("Thabang Makgwale")
                .contains("Managing Director")
                .contains("012 555 1234")
                .contains("info@fastprint.co.za")
                .contains("www.fastprint.co.za");
    }

    @Test
    @DisplayName("signature field values are HTML-escaped")
    void escapesFieldValues() {
        TenantEmailSignature sig = TenantEmailSignature.create(
                tenantId.getValue(), "<script>alert(1)</script>", null, null, null, null);
        when(signatureRepository.findByTenantId(tenantId.getValue())).thenReturn(Optional.of(sig));

        String html = engine.renderSignatureHtml(tenantId);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("omits contact line entirely when phone/email/website are all blank")
    void omitsEmptyContactLine() {
        TenantEmailSignature sig = TenantEmailSignature.create(
                tenantId.getValue(), "Thabang Makgwale", "Managing Director", null, null, null);
        when(signatureRepository.findByTenantId(tenantId.getValue())).thenReturn(Optional.of(sig));

        String html = engine.renderSignatureHtml(tenantId);

        assertThat(html).contains("Thabang Makgwale");
        // No trailing empty <p> for contact details when nothing to show.
        assertThat(html).doesNotContain("&middot;");
    }
}
