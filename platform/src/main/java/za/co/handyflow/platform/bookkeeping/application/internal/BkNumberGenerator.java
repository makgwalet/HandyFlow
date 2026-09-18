package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

/**
 * Flat, tenant-scoped sequences for client codes, journal entry numbers,
 * and invoice numbers — same {@code TenantSequenceService.nextValue()}
 * pattern every other provider module's own number generator in this
 * codebase uses (FmNumberGenerator, TrainProvNumberGenerator).
 * <p>
 * MIGRATED (invoice only): nextInvoiceNumber now goes through
 * TenantNumberingFacade with default type code "CINV" ("client invoice") —
 * this is the actual fix for the collision found while building the Tenant
 * Numbering Engine: this class, InvoiceNumberGenerator (invoicing) and
 * FmNumberGenerator (facilitiesmanagement) each independently produced a
 * document literally labelled "INV-00001" for an unrelated purpose. Output
 * changes from "INV-00001" to "{tenantCode}-CINV-00001", e.g.
 * "FPS-CINV-00001" for a bookkeeping client's invoice, vs.
 * "FPS-INV-00001" for the same tenant's own sales invoice — now
 * distinguishable at a glance. nextClientCode/nextEntryNumber are left on
 * TenantSequenceService directly — CLI-/JE- collisions across modules are
 * lower priority and tracked separately in PLATFORM-ENGINES-PROGRESS.md.
 */
@Component
@RequiredArgsConstructor
public class BkNumberGenerator {

    private static final String CLIENT_SEQUENCE = "BK_CLIENT";
    private static final String JOURNAL_SEQUENCE = "BK_JOURNAL";
    private static final String INVOICE_SEQUENCE = "BK_INVOICE";

    private final TenantSequenceService sequenceService;
    private final TenantNumberingFacade numberingFacade;

    public String nextClientCode(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, CLIENT_SEQUENCE);
        return "CLI-%04d".formatted(seq);
    }

    public String nextEntryNumber(TenantId tenantId) {
        long seq = sequenceService.nextValue(tenantId, JOURNAL_SEQUENCE);
        return "JE-%05d".formatted(seq);
    }

    public String nextInvoiceNumber(TenantId tenantId) {
        return numberingFacade.next(tenantId, INVOICE_SEQUENCE, "CINV");
    }
}
