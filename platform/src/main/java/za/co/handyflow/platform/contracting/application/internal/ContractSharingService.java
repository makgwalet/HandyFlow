package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.dto.ContractSharingDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.AccessLogView;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.ItemSpec;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.OpenShare;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientSpec;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.RecipientView;
import za.co.handyflow.platform.secureaccess.application.SecureShareService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Contracting's thin facade over the Secure Document Access Engine. It owns only the contract rules: the contract must be signed
 * and not disposed of, and what is shared is its frozen executed copy. Links, codes, sessions, expiry, revocation and the access
 * log are the engine's; recipients open the contract at /secure/share/{token}.
 */
@Service
@RequiredArgsConstructor
public class ContractSharingService {

    private final ContractRepository contracts;
    private final ContractRecordsService records;
    private final SecureShareService engine;

    @Transactional(readOnly = true)
    public SharingView view(TenantId tenantId, UUID contractId) {
        Contract c = contract(tenantId, contractId);
        UUID shareId = engine.findShareId(tenantId.getValue(), ContractRecordsService.MODULE, ContractingShareProvider.SHARE_TYPE, contractId).orElse(null);
        List<RecipientView> recipients = shareId == null ? List.of() : engine.recipientViews(tenantId.getValue(), shareId);
        ExecutedCopy copy = c.hasExecutedCopy() ? new ExecutedCopy(c.getExecutedSha256(), c.getExecutedSizeBytes(), c.getExecutedStoredAt()) : null;
        return new SharingView(c.getId(), c.getContractNumber(), c.getStatus(), c.isExecuted() && !c.isDisposed(), c.isDisposed(),
                copy, shareId, recipients);
    }

    /** Gives one more person a link to the signed contract. The first one stores the executed copy (if needed) and opens the share. */
    @Transactional
    public RecipientView addRecipient(TenantId tenantId, UUID contractId, RecipientRequest req, UUID by, String byName) {
        if (req == null) throw new IllegalArgumentException("Say who to share the contract with.");
        Contract c = records.ensureExecutedCopy(tenantId, contractId);
        UUID shareId = engine.findShareId(tenantId.getValue(), ContractRecordsService.MODULE, ContractingShareProvider.SHARE_TYPE, contractId)
                .orElseGet(() -> engine.openShare(new OpenShare(tenantId.getValue(), ContractRecordsService.MODULE, ContractingShareProvider.SHARE_TYPE,
                        c.getId(), c.getId(), List.of(item(c)), List.of(), null, false, by, byName)).shareId());
        RecipientSpec spec = new RecipientSpec(req.name(), req.organisation(), req.email(),
                req.canDownload() == null || req.canDownload(), req.canAcknowledge() == null || req.canAcknowledge(), null);
        return engine.addRecipient(tenantId.getValue(), shareId, spec, req.expiryDays(), Boolean.TRUE.equals(req.attach()), by, byName);
    }

    @Transactional
    public RecipientView reissue(TenantId tenantId, UUID contractId, UUID recipientId, Integer expiryDays, String byName) {
        Contract c = contract(tenantId, contractId);
        if (c.isDisposed()) throw ContractRecordsService.disposed();
        return engine.reissue(tenantId.getValue(), shareOf(tenantId, contractId), recipientId, expiryDays, byName);
    }

    @Transactional
    public RecipientView revoke(TenantId tenantId, UUID contractId, UUID recipientId, String reason, String byName) {
        contract(tenantId, contractId);
        if (reason == null || reason.trim().length() < 3) throw new IllegalArgumentException("Give a reason to revoke access.");
        return engine.revoke(tenantId.getValue(), shareOf(tenantId, contractId), recipientId, reason, byName);
    }

    @Transactional(readOnly = true)
    public List<AccessLogView> accessLog(TenantId tenantId, UUID contractId) {
        contract(tenantId, contractId);
        return engine.findShareId(tenantId.getValue(), ContractRecordsService.MODULE, ContractingShareProvider.SHARE_TYPE, contractId)
                .map(id -> engine.accessLog(tenantId.getValue(), id)).orElse(List.of());
    }

    private Contract contract(TenantId tenantId, UUID id) {
        return contracts.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Contract", String.valueOf(id)));
    }

    private UUID shareOf(TenantId tenantId, UUID contractId) {
        return engine.findShareId(tenantId.getValue(), ContractRecordsService.MODULE, ContractingShareProvider.SHARE_TYPE, contractId)
                .orElseThrow(() -> new HandyFlowException("Recipient not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    static ItemSpec item(Contract c) {
        return new ItemSpec(ContractRecordsService.MODULE, ContractingShareProvider.ITEM_TYPE, c.getId(), "executed",
                c.getContractNumber(), c.getTitle(), c.getStatus(), ContractRecordsService.fileName(c), c.getExecutedSha256());
    }
}
