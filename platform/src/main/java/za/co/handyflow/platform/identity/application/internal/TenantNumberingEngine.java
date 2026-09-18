package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.identity.domain.model.TenantNumberingConfig;
import za.co.handyflow.platform.identity.domain.repository.TenantNumberingConfigRepository;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.time.Year;
import java.util.Map;

/**
 * See {@link TenantNumberingFacade} for the "why this exists" writeup.
 * <p>
 * Format produced when no {@link TenantNumberingConfig} row exists:
 * {@code {tenantCode}-{typeCode}-{paddedSeq}} — no year, 5-digit padding.
 * This intentionally matches the current output of InvoiceNumberGenerator /
 * QuoteNumberGenerator / CreditNoteNumberGenerator (e.g. "INV-00001") except
 * for the added tenant code, so migrating a generator to this engine is a
 * visible-but-not-disruptive change: existing documents keep their old
 * numbers, and new numbers stay recognisably the same shape with the
 * tenant's identity now on them.
 * <p>
 * A {@link TenantNumberingConfig} row (created via Settings, not yet built —
 * see platform backlog) can turn on {@code includeYear}, change padding, or
 * override the type code / whole prefix per tenant per document type.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class TenantNumberingEngine implements TenantNumberingFacade {

    /**
     * Built-in type codes for document types already known to collide or
     * worth disambiguating on sight. Anything not listed here falls back to
     * the caller-supplied {@code defaultTypeCode} (see
     * {@link TenantNumberingFacade#next}), which lets a module migrate
     * without this class needing to enumerate every type up front.
     * <p>
     * INVOICE / INVOICE_SEQUENCE deliberately get DIFFERENT codes across
     * modules even though a human might call all three "an invoice number" —
     * this is the actual fix for the collision found in
     * InvoiceNumberGenerator (invoicing), BkNumberGenerator (bookkeeping)
     * and FmNumberGenerator (facilities-management), which today can each
     * independently produce a document literally labelled "INV-00001" for
     * an unrelated purpose (the tenant's own invoice vs. a bookkeeping
     * client's ledger invoice vs. a facilities-management client's
     * invoice). Once a module is migrated, its documents are
     * distinguishable even printed side by side.
     */
    private static final Map<String, String> DEFAULT_TYPE_CODES = Map.ofEntries(
            Map.entry("INVOICE", "INV"),          // invoicing module — tenant's own sales invoices
            Map.entry("QUOTE", "QT"),
            Map.entry("CREDIT_NOTE", "CN")
            // Additional entries added as each remaining generator (bookkeeping
            // INVOICE_SEQUENCE -> "CINV", facilitiesmanagement INVOICE_SEQUENCE
            // -> "FMINV", etc.) is migrated — see gap matrix backlog. Until then
            // those modules keep calling TenantSequenceService directly and are
            // unaffected by this engine.
    );

    private final TenantNumberingConfigRepository configRepository;
    private final TenantSequenceService sequenceService;
    private final TenantDocumentCodeResolver documentCodeResolver;

    @Override
    public String next(TenantId tenantId, String documentType, String defaultTypeCode) {
        long seq = sequenceService.nextValue(tenantId, documentType);

        // Delegated to a separate bean (not called as this.resolveDocumentCode(...))
        // deliberately: an in-class self-invocation would bypass Spring's
        // transactional proxy entirely, silently turning the REQUIRES_NEW
        // guarantee below into a no-op. See TenantDocumentCodeResolver.
        String tenantCode = documentCodeResolver.resolveDocumentCode(tenantId);

        var config = configRepository
                .findByTenantIdAndDocumentType(tenantId.getValue(), documentType)
                .orElse(null);

        String typeCode = (config != null && config.getTypeCode() != null)
                ? config.getTypeCode()
                : DEFAULT_TYPE_CODES.getOrDefault(documentType, defaultTypeCode);

        int padding = (config != null) ? config.getPadding() : 5;
        boolean includeYear = (config != null) && config.isIncludeYear();

        String prefix = (config != null && config.getPrefixOverride() != null)
                ? config.getPrefixOverride()
                : tenantCode + "-" + typeCode;

        String seqPart = String.format("%0" + padding + "d", seq);

        return includeYear
                ? prefix + "-" + Year.now().getValue() + "-" + seqPart
                : prefix + "-" + seqPart;
    }

}
