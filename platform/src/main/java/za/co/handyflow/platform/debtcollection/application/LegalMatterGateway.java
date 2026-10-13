package za.co.handyflow.platform.debtcollection.application;

import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * The seam between Debt Collection and Legal Practice. Debt Collection never reaches into the legal module's
 * internals: Legal Practice implements this interface (as a Spring bean) and creates its own matter from the
 * reviewed package. That implementing module needs "debtcollection" in its allowedDependencies, mirroring how
 * `invoicing.application.InvoicingFacade` is consumed here.
 * <p>
 * CONTRACT
 * <ul>
 *   <li>Idempotent on handoverId: calling again for the same handoverId must return the SAME matter, never a second
 *   one. Debt Collection retries a transfer if its own bookkeeping failed after the call succeeded.</li>
 *   <li>Preserve provenance: store handoverId, caseId, packageHash and the package JSON with the matter, and
 *   build the matter from the package rather than asking staff to re-capture it.</li>
 *   <li>After creation, report progress back through LegalHandoverService.syncLegalStatus(...).</li>
 * </ul>
 * If no bean implements this, approval still works but transfer fails with a clear message — nothing is faked.
 */
public interface LegalMatterGateway {

    MatterRef createMatterFromHandover(HandoverTransfer transfer);

    record HandoverTransfer(TenantId tenantId, UUID handoverId, UUID caseId, String caseNumber, String debtorName,
                            UUID customerId, BigDecimal balanceSnapshot, String agreementType, String packageJson,
                            String packageHash, UUID linkedContractId, Set<UUID> evidenceIds, UUID requestedBy) {}

    record MatterRef(UUID matterId, String matterNumber) {}
}
