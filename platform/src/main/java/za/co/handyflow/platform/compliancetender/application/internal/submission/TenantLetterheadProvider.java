package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.TenantId;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** The letterhead from the tenant's details: name, address, phone, email, VAT number and logo (stored as a data URI). */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantLetterheadProvider implements LetterheadProvider {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final TenantFacade tenantFacade;

    @Override
    public Letterhead forTender(TenantId tenantId, Tender tender) {
        TenantDetails d = tenantFacade.findTenantDetails(tenantId).orElse(null);
        String closing = tender.getClosingDate() == null ? null : tender.getClosingDate().format(DAY);
        if (d == null) {
            return new Letterhead(null, List.of(), null, tender.getTenderNumber(), tender.getName(), tender.getTenderAuthority(),
                    tender.getAuthorityReferenceNumber(), closing);
        }
        List<String> lines = new ArrayList<>();
        if (d.address() != null && !d.address().isEmpty()) {
            String address = String.join(", ", d.address().values().stream().filter(v -> v != null && !v.isBlank()).toList());
            if (!address.isBlank()) lines.add(address);
        }
        if (notBlank(d.phone())) lines.add("Tel: " + d.phone().trim());
        if (notBlank(d.email())) lines.add(d.email().trim());
        if (notBlank(d.vatNumber())) lines.add("VAT: " + d.vatNumber().trim());
        return new Letterhead(d.companyName(), lines, logoBytes(d.logoUrl()), tender.getTenderNumber(), tender.getName(),
                tender.getTenderAuthority(), tender.getAuthorityReferenceNumber(), closing);
    }

    /** The logo is a data URI; anything else, or anything malformed, means no logo rather than a failed build. */
    static byte[] logoBytes(String logoUrl) {
        if (logoUrl == null || !logoUrl.startsWith("data:")) return null;
        try {
            int comma = logoUrl.indexOf(',');
            if (comma < 0) return null;
            return Base64.getDecoder().decode(logoUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            log.warn("The tenant logo could not be decoded; the letterhead will show the name only.");
            return null;
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
