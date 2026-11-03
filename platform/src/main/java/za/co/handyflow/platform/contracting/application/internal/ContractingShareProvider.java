package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractParty;
import za.co.handyflow.platform.contracting.domain.repository.ContractPartyRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Contracting's side of the Secure Document Access Engine.
 * <pre>
 *   share = CONTRACTING / CONTRACT / the contract (context = the contract)
 *   item  = CONTRACTING / EXECUTED_CONTRACT / the contract, with the executed copy's hash frozen at issue
 * </pre>
 * Asked on every view and download: the contract must still exist in the tenant, be signed (in force, expired or terminated),
 * not be disposed of, and still have the same executed copy. Depends only on repositories and the record service, never on the
 * engine, so there is no bean cycle.
 */
@Service
@RequiredArgsConstructor
public class ContractingShareProvider implements SecureShareProvider {

    public static final String SHARE_TYPE = "CONTRACT";
    public static final String ITEM_TYPE = "EXECUTED_CONTRACT";

    private final ContractRepository contracts;
    private final ContractPartyRepository parties;
    private final ContractRecordsService records;

    @Override public String moduleCode() { return ContractRecordsService.MODULE; }

    private Optional<Contract> contract(UUID tenantId, UUID id) {
        return contracts.findActiveById(TenantId.of(tenantId), id);
    }

    @Override
    public ShareHeader describe(UUID tenantId, ShareRef share) {
        Contract c = contract(tenantId, share.sourceId()).orElse(null);
        if (c == null) return null;
        String names = parties.findByContract(c.getId()).stream().filter(p -> "EXTERNAL".equals(p.getPartyType()))
                .map(ContractingShareProvider::partyName).collect(Collectors.joining(", "));
        return new ShareHeader(c.getContractNumber(), c.getTitle(), names.isBlank() ? null : names, "Signed contract", null,
                null, c.getSignedAt());
    }

    @Override
    public Decision authorise(UUID tenantId, ShareRef share, DocumentRef doc, Who who) {
        if (!ContractRecordsService.MODULE.equals(doc.moduleCode()) || !ITEM_TYPE.equals(doc.sourceType())
                || !doc.sourceId().equals(share.sourceId())) {
            return Decision.deny("This document is not available");
        }
        Contract c = contract(tenantId, doc.sourceId()).orElse(null);
        if (c == null) return Decision.deny("This contract is no longer available");
        if (c.isDisposed()) return Decision.deny("This contract was disposed of under the retention policy");
        if (!c.isExecuted()) return Decision.deny("This contract is not signed");
        if (!c.hasExecutedCopy()) return Decision.deny("The signed copy of this contract is not available");
        return Decision.allow();
    }

    @Override
    public ReleasedFile release(UUID tenantId, ShareRef share, DocumentRef doc, String issuedSha256, String actor) {
        Contract c = contract(tenantId, doc.sourceId()).orElseThrow(() -> new IllegalStateException("Contract not found"));
        if (issuedSha256 != null && !issuedSha256.equalsIgnoreCase(c.getExecutedSha256())) {
            throw new IllegalStateException("The executed copy is not the one that was shared");
        }
        byte[] bytes = records.executedCopy(TenantId.of(tenantId), c);
        return new ReleasedFile(ContractRecordsService.fileName(c), "application/pdf", bytes);
    }

    static String partyName(ContractParty p) {
        return p.getCompanyName() != null && !p.getCompanyName().isBlank() ? p.getCompanyName() : p.getFullName();
    }
}
