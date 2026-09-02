package za.co.handyflow.platform.insurancebrokerage.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.util.UUID;

/**
 * Policy numbers are tenant-wide (a brokerage's own internal reference —
 * distinct from the insurer's own policy number captured at bind()).
 * Commission invoice numbers are sequenced PER CLIENT, same
 * "MODULE_INVOICE:" + clientId shape CollAgencyNumberGenerator/
 * WhseNumberGenerator already use, so each client's commission invoices
 * number independently starting from IB-CI-00001.
 * <p>
 * Sequence-name length: TenantSequenceService.nextValue() enforces a
 * 50-char cap on the composed sequence name (see its own Javadoc — two
 * separate real crashes already happened elsewhere in this codebase from
 * overly verbose prefixes). "IB_INV:" (7 chars) + a 36-char UUID = 43,
 * safely under the cap — deliberately kept short, not copied verbatim
 * from CollAgencyNumberGenerator's longer "COLLECTIONSAGENCY_INVOICE:"
 * prefix, which would itself overflow.
 */
@Component
@RequiredArgsConstructor
public class InsBrokNumberGenerator {

    private static final String POLICY_SEQUENCE = "IB_POLICY";
    private static final String INVOICE_SEQUENCE_PREFIX = "IB_INV:";

    private final TenantSequenceService sequenceService;

    public String nextPolicyReference(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, POLICY_SEQUENCE);
        return "IB-POL-%05d".formatted(next);
    }

    public String nextCommissionInvoiceNumber(TenantId tenantId, UUID clientId) {
        long next = sequenceService.nextValue(tenantId, INVOICE_SEQUENCE_PREFIX + clientId);
        return "IB-CI-%05d".formatted(next);
    }
}
