package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.identity.TenantEmailBrandingFacade;
import za.co.handyflow.platform.identity.domain.model.TenantEmailSignature;
import za.co.handyflow.platform.identity.domain.repository.TenantEmailSignatureRepository;
import za.co.handyflow.platform.shared.TenantId;

/**
 * See {@link TenantEmailBrandingFacade} for the "why a plain String"
 * writeup.
 * <p>
 * Styling is inline and deliberately reuses {@code EmailTemplates.wrap()}'s
 * own colour tokens (#374151 body text, #0F172A headings, #64748B muted,
 * #E2E8F0 border) so the fragment looks native to the surrounding email
 * rather than visually bolted on. Field values are HTML-escaped the same
 * way {@code EmailTemplates.quoteSentToClient} already escapes clientName/
 * companyName, since a signature field is exactly the same kind of
 * tenant-entered free text.
 */
@Service
@RequiredArgsConstructor
class TenantEmailBrandingEngine implements TenantEmailBrandingFacade {

    private final TenantEmailSignatureRepository signatureRepository;

    @Override
    @Transactional(readOnly = true)
    public String renderSignatureHtml(TenantId tenantId) {
        TenantEmailSignature sig = signatureRepository.findByTenantId(tenantId.getValue()).orElse(null);
        if (sig == null || !sig.isEnabled()) {
            return "";
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"margin-top:24px;padding-top:16px;border-top:1px solid #E2E8F0;\">");
        html.append("<p style=\"margin:0;color:#374151;font-size:14px;\">Kind regards,</p>");

        if (hasText(sig.getDisplayName())) {
            html.append("<p style=\"margin:4px 0 0;color:#0F172A;font-size:14px;font-weight:600;\">")
                    .append(escape(sig.getDisplayName()))
                    .append("</p>");
        }
        if (hasText(sig.getJobTitle())) {
            html.append("<p style=\"margin:0;color:#64748B;font-size:12px;\">")
                    .append(escape(sig.getJobTitle()))
                    .append("</p>");
        }

        String contactLine = joinNonBlank(" &middot; ", sig.getPhone(), sig.getEmail(), sig.getWebsite());
        if (!contactLine.isBlank()) {
            html.append("<p style=\"margin:8px 0 0;color:#64748B;font-size:12px;\">")
                    .append(contactLine)
                    .append("</p>");
        }

        html.append("</div>");
        return html.toString();
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String escape(String s) {
        return HtmlUtils.htmlEscape(s);
    }

    private String joinNonBlank(String separator, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (hasText(part)) {
                if (sb.length() > 0) sb.append(separator);
                sb.append(escape(part));
            }
        }
        return sb.toString();
    }
}
