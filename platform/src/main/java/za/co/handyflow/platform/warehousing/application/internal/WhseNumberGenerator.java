package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.util.UUID;

/**
 * Billing invoice numbering — sequence keyed per CLIENT, not just per
 * tenant, same "MODULE_INVOICE:" + clientId shape as
 * CollAgencyNumberGenerator/RecruitmentAgencyService/PayrollBureauService,
 * so each 3PL client's invoices number independently starting from
 * WHI-00001.
 */
@Component
@RequiredArgsConstructor
public class WhseNumberGenerator {

    private static final String INVOICE_SEQUENCE_PREFIX = "WAREHOUSING_INVOICE:";

    private static final String MANIFEST_SEQUENCE = "WAREHOUSING_MANIFEST";

    private final TenantSequenceService sequenceService;

    /** Dispatch manifests number per warehouse: MAN00001, MAN00002, ... */
    public String nextManifestNumber(TenantId tenantId) {
        return "MAN" + String.format("%05d", sequenceService.nextValue(tenantId, MANIFEST_SEQUENCE));
    }

    public String nextInvoiceNumber(TenantId tenantId, UUID clientId) {
        long next = sequenceService.nextValue(tenantId, INVOICE_SEQUENCE_PREFIX + clientId);
        return "WHI" + String.format("%05d", next);
    }
}
