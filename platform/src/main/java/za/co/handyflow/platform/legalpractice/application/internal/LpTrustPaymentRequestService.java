package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustTransactionService.TrustApproval;
import za.co.handyflow.platform.legalpractice.domain.model.LpTrustPaymentRequest;
import za.co.handyflow.platform.legalpractice.domain.repository.LpClientRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpTrustPaymentRequestRepository;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maker-checker for money leaving trust. One person requests; a different person approves; approval posts the payment
 * through {@link LpTrustTransactionService} in the same transaction, so a request is never APPROVED without a ledger row
 * and a ledger payment never exists without an approved request. If posting fails (balance gone, invoice already paid),
 * everything rolls back and the request stays PENDING for the approver to reject or fix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpTrustPaymentRequestService {

    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED");

    private final LpTrustPaymentRequestRepository requestRepo;
    private final LpClientRepository clientRepo;
    private final LpTrustTransactionService trustService;
    private final LpTrustSettingsService settings;
    private final LpWallGuard walls;

    @Transactional
    public LpTrustPaymentRequestResponse create(TenantId tenantId, UUID clientId, CreateTrustPaymentRequest req,
                                                UUID requestedBy, String requestedByName) {
        String key = req.idempotencyKey() == null || req.idempotencyKey().isBlank() ? null : req.idempotencyKey().trim();
        if (key != null) {
            // The same attempt sent twice returns the first request. The same key on something different is refused.
            var existing = requestRepo.findByIdempotencyKey(tenantId, key);
            if (existing.isPresent()) {
                LpTrustPaymentRequest e = existing.get();
                boolean same = e.getClientId().equals(clientId) && e.getPaymentType().equals(req.paymentType())
                        && req.amount() != null && e.getAmount().compareTo(req.amount()) == 0 && e.isRequestedBy(requestedBy);
                if (!same) throw new IllegalStateException("That request key was already used for a different payment request");
                return toResponse(tenantId, e, null);
            }
        }
        LpTrustPaymentRequest r = LpTrustPaymentRequest.request(tenantId, clientId, req.matterId(), req.paymentType(),
                req.amount(), req.transactionDate(), req.invoiceId(), req.payee(), req.reference(), req.notes(),
                requestedBy, requestedByName, key);
        // A request that could never be paid is refused now rather than left in the queue.
        trustService.preflight(tenantId, clientId, r.getPaymentType(), r.getMatterId(), r.getInvoiceId(), r.getAmount());
        requestRepo.save(r);
        log.info("Trust payment request={} type={} client={} amount={} by={} tenant={}",
                r.getId(), r.getPaymentType(), clientId, r.getAmount(), requestedBy, tenantId);
        return toResponse(tenantId, r, null);
    }

    @Transactional
    public LpTrustPaymentRequestResponse approve(TenantId tenantId, UUID id, UUID approver, String approverName, String selfApprovalReason) {
        LpTrustPaymentRequest r = find(tenantId, id);
        r.requireCanBeApprovedBy(approver, settings.selfApprovalAllowed(tenantId), selfApprovalReason);

        String key = "payment-request:" + r.getId();
        TrustApproval approval = new TrustApproval(r.getId(), approver, approverName);
        // The ledger row is captured by the person who asked and approved by the person who decided.
        LpTrustTransactionResponse posted = switch (r.getPaymentType()) {
            case "TRANSFER_TO_BUSINESS" -> trustService.transferToBusiness(tenantId, r.getClientId(),
                    new TransferToBusinessRequest(r.getInvoiceId(), r.getMatterId(), r.getAmount(), r.getTransactionDate(),
                            r.getReference(), r.getNotes(), key), r.getRequestedBy(), r.getRequestedByName(), approval);
            case "DISBURSEMENT_PAYMENT" -> trustService.payDisbursement(tenantId, r.getClientId(),
                    new PayDisbursementFromTrustRequest(r.getMatterId(), r.getAmount(), r.getTransactionDate(), r.getPayee(),
                            r.getReference(), r.getNotes(), key), r.getRequestedBy(), r.getRequestedByName(), approval);
            case "REFUND" -> trustService.refund(tenantId, r.getClientId(),
                    new RefundTrustRequest(r.getMatterId(), r.getAmount(), r.getTransactionDate(), r.getPayee(),
                            r.getReference(), r.getNotes(), key), r.getRequestedBy(), r.getRequestedByName(), approval);
            default -> throw new IllegalStateException("Unknown payment type " + r.getPaymentType());
        };
        r.markApproved(approver, approverName, selfApprovalReason, posted.id());
        requestRepo.save(r);
        log.info("Trust payment request={} APPROVED by={} selfApproved={} tx={} tenant={}",
                r.getId(), approver, r.isSelfApproved(), posted.id(), tenantId);
        return toResponse(tenantId, r, null);
    }

    @Transactional
    public LpTrustPaymentRequestResponse reject(TenantId tenantId, UUID id, UUID by, String byName, String reason) {
        LpTrustPaymentRequest r = find(tenantId, id);
        r.reject(by, byName, reason);
        requestRepo.save(r);
        return toResponse(tenantId, r, null);
    }

    @Transactional
    public LpTrustPaymentRequestResponse cancel(TenantId tenantId, UUID id, UUID by, String byName) {
        LpTrustPaymentRequest r = find(tenantId, id);
        r.cancel(by, byName);
        requestRepo.save(r);
        return toResponse(tenantId, r, null);
    }

    @Transactional(readOnly = true)
    public Page<LpTrustPaymentRequestResponse> list(TenantId tenantId, String status, Pageable pageable) {
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (s != null && !STATUSES.contains(s)) {
            throw new IllegalArgumentException("Status must be one of " + STATUSES);
        }
        Map<UUID, String> names = new HashMap<>();
        var barred = walls.barred(tenantId);
        return requestRepo.searchExcluding(tenantId, s, barred.matterIds(), barred.clientIds(), pageable).map(r -> toResponse(tenantId, r, names));
    }

    private LpTrustPaymentRequest find(TenantId tenantId, UUID id) {
        LpTrustPaymentRequest request = requestRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LpTrustPaymentRequest", id.toString()));
        walls.requireScopeAccess(tenantId, request.getMatterId(), request.getClientId());
        return request;
    }

    /** {@code names} caches client names across one listing so each client is looked up once. */
    private LpTrustPaymentRequestResponse toResponse(TenantId tenantId, LpTrustPaymentRequest r, Map<UUID, String> names) {
        Map<UUID, String> cache = names != null ? names : new HashMap<>();
        String clientName = cache.computeIfAbsent(r.getClientId(),
                id -> clientRepo.findActiveById(tenantId, id).map(c -> c.getName()).orElse(null));
        return new LpTrustPaymentRequestResponse(r.getId(), r.getClientId(), clientName, r.getMatterId(), r.getPaymentType(), r.getAmount(),
                r.getTransactionDate(), r.getInvoiceId(), r.getPayee(), r.getReference(), r.getNotes(), r.getStatus(),
                r.getRequestedBy(), r.getRequestedByName(), r.getRequestedAt(), r.getDecidedByName(), r.getDecidedAt(),
                r.getDecisionReason(), r.isSelfApproved(), r.getResultTransactionId());
    }
}
