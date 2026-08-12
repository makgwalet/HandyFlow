package za.co.handyflow.platform.catalogue;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public entry point for other modules that need catalogue item data.
 * Already correctly used by invoicing.application.internal.QuoteService.
 * <p>
 * ADDED: findItemByBarcode(). Previously only searchItems()/findItemById()
 * existed here, so pos.application.internal.PosService reached directly
 * into catalogue.domain.repository.CatalogueItemRepository and
 * catalogue.domain.model.CatalogueItem instead — a real boundary
 * violation (see HandyFlow BOS Discovery doc, Section 27.2/34), and one
 * that also relied on reflection (cat.getClass().getMethod("getName")
 * etc.) to read fields off the entity it wasn't supposed to have direct
 * access to in the first place. That reflection already silently broke
 * once for isVatExempt()/vatRate (see PosService's own code comment on
 * that fix) and was silently broken a second time for a nonexistent
 * getSku() method the whole time. Using this facade's typed
 * CatalogueItemSummary fixes both the boundary violation and the
 * reflection fragility in one move.
 */
public interface CatalogueFacade {

    List<CatalogueItemSummary> searchItems(TenantId tenantId, String query);

    Optional<CatalogueItemSummary> findItemById(TenantId tenantId, UUID itemId);

    /**
     * For POS terminal barcode scanning. Matches
     * CatalogueItemRepository.findByTenantIdAndBarcode()'s existing
     * behaviour exactly — same native query, same "not yet a real Java
     * field, ALTER TABLE'd column read via native SQL" situation that
     * query's own comment documents. Not this facade's job to fix that
     * underlying TODO; just to stop letting callers reach past it directly.
     */
    Optional<CatalogueItemSummary> findItemByBarcode(TenantId tenantId, String barcode);
}