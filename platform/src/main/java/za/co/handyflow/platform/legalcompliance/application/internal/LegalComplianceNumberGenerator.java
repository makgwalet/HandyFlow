package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Formats legalcompliance's two numbered document types — litigation matter
 * numbers and DSAR request numbers — as a thin wrapper over
 * TenantSequenceService, exactly the InvoiceNumberGenerator/
 * CreditNoteNumberGenerator/QuoteNumberGenerator pattern (see those classes
 * in invoicing.application.internal): atomic read-and-increment, not a
 * count()+1 race.
 * <p>
 * Sequence names: "LEGALCOMPLIANCE_MATTER" and "LEGALCOMPLIANCE_DSAR" — both
 * well under TenantSequenceService's confirmed 50-char VARCHAR guard on
 * tenant_number_sequences.sequence_name (see that class's own Javadoc for
 * why that guard exists — two prior real production crashes from
 * over-length composed sequence names).
 */
@Component
@RequiredArgsConstructor
public class LegalComplianceNumberGenerator {

    private static final String MATTER_SEQUENCE = "LEGALCOMPLIANCE_MATTER";
    private static final String DSAR_SEQUENCE = "LEGALCOMPLIANCE_DSAR";

    private final TenantSequenceService sequenceService;

    public String nextMatterNumber(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, MATTER_SEQUENCE);
        return "LM-%05d".formatted(next);
    }

    public String nextDsarNumber(TenantId tenantId) {
        long next = sequenceService.nextValue(tenantId, DSAR_SEQUENCE);
        return "DSAR-%05d".formatted(next);
    }
}
