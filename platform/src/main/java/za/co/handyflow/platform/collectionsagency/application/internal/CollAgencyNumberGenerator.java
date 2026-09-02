package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.util.UUID;

/**
 * Commission invoice numbering — sequence keyed per CLIENT, not just per
 * tenant, same "MODULE_INVOICE:" + clientId shape
 * RecruitmentAgencyService/PayrollBureauService already use
 * (RECRUITMENTAGENCY_INVOICE:&lt;clientId&gt;), so each creditor client's
 * commission invoices number independently starting from CI-00001,
 * matching how a real agency would present per-client invoice sequences
 * rather than one shared tenant-wide counter.
 */
@Component
@RequiredArgsConstructor
public class CollAgencyNumberGenerator {

    private static final String INVOICE_SEQUENCE_PREFIX = "COLLECTIONSAGENCY_INVOICE:";

    private final TenantSequenceService sequenceService;

    public String nextCommissionInvoiceNumber(TenantId tenantId, UUID clientId) {
        long next = sequenceService.nextValue(tenantId, INVOICE_SEQUENCE_PREFIX + clientId);
        return "CI" + String.format("%05d", next);
    }
}
