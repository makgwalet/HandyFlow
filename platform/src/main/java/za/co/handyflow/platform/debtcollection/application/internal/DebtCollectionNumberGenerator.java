package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Thin TenantSequenceService wrapper for debt collection case numbers —
 * same InvoiceNumberGenerator/LegalComplianceNumberGenerator pattern.
 * Sequence name "DEBTCOLLECTION_CASE" (20 chars, well under
 * TenantSequenceService's 50-char guard).
 */
@Component
@RequiredArgsConstructor
public class DebtCollectionNumberGenerator {

    private static final String CASE_SEQUENCE = "DEBTCOLLECTION_CASE";

    private final TenantSequenceService sequenceService;

    public String nextCaseNumber(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, CASE_SEQUENCE);
        return "DC-%05d".formatted(next);
    }
}
