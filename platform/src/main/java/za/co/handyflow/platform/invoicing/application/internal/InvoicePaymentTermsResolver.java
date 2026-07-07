package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves an invoice's due date from the tenant's configured payment terms.
 *
 * FIXED: TenantDetails.paymentTerms() is a String (e.g. "30", "Net 30",
 * "NET30", possibly blank/null for tenants who never set it) — not an
 * Integer as originally assumed. This class now parses the first run of
 * digits out of that string and falls back to the 30-day default for
 * anything that doesn't parse (null, blank, or no digits at all), rather
 * than throwing or silently truncating.
 *
 * WHY parse defensively instead of requiring a strict format?
 * This field was almost certainly entered as free text somewhere in a
 * tenant-settings form before this feature existed to consume it — we have
 * no guarantee every existing tenant row has a clean value. Failing to parse
 * should degrade to "use the sane default," not break invoice issuance.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoicePaymentTermsResolver {

    private static final int DEFAULT_PAYMENT_TERM_DAYS = 30;

    // Matches the first run of digits in the string — "30", "Net 30",
    // "NET-30 days", "30 days" all yield "30".
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final TenantFacade tenantFacade;

    public LocalDate resolveDueDate(TenantId tenantId, LocalDate issueDate) {
        String raw = tenantFacade.findTenantDetails(tenantId)
                .map(t -> t.paymentTerms())
                .orElse(null);

        int days = parseDays(raw, tenantId);
        return issueDate.plusDays(days);
    }

    private int parseDays(String raw, TenantId tenantId) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAYMENT_TERM_DAYS;
        }
        Matcher m = DIGITS.matcher(raw);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                log.warn("Tenant={} paymentTerms='{}' matched digits but failed to parse — using default {}d",
                        tenantId, raw, DEFAULT_PAYMENT_TERM_DAYS);
            }
        } else {
            log.warn("Tenant={} paymentTerms='{}' has no parseable digits — using default {}d",
                    tenantId, raw, DEFAULT_PAYMENT_TERM_DAYS);
        }
        return DEFAULT_PAYMENT_TERM_DAYS;
    }
}