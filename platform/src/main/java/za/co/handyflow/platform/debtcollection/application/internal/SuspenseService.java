package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseItem;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseKind;
import za.co.handyflow.platform.debtcollection.domain.model.SuspenseStatus;
import za.co.handyflow.platform.debtcollection.domain.repository.SuspenseItemRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Works the suspense queue. resolve() runs the normal ledger / inbound path for the chosen case, THEN marks the item
 * resolved. It is deliberately not one transaction (the downstream services manage their own): both paths are
 * idempotent on the external reference, so if marking fails after the payment was recorded, resolving again converges
 * on the same ledger entry instead of double-counting.
 */
@Service
@RequiredArgsConstructor
public class SuspenseService {

    private final SuspenseItemRepository repository;
    private final RecoveryLedgerService ledgerService;
    private final CommunicationService communicationService;

    @Transactional(readOnly = true)
    public List<SuspenseItem> list(TenantId tenantId, SuspenseStatus status) {
        return repository.findByStatus(tenantId, status != null ? status : SuspenseStatus.PENDING);
    }

    public SuspenseItem resolve(TenantId tenantId, UUID id, UUID caseId, String note, UUID actorId, String actorName) {
        SuspenseItem s = find(tenantId, id);
        if (s.getStatus() != SuspenseStatus.PENDING) throw new IllegalStateException("Item is already " + s.getStatus());
        if (s.getKind() == SuspenseKind.PAYMENT) {
            ledgerService.recordPayment(tenantId, caseId, s.getAmount(), s.getEventDate(), source(s.getPaymentSource()),
                    s.getExternalReference(), null, null, "Allocated from suspense" + (note != null ? ": " + note : ""),
                    actorId, actorName);
        } else {
            Instant at = s.getEventDate() == null ? null : s.getEventDate().atStartOfDay().toInstant(ZoneOffset.UTC);
            communicationService.recordInbound(tenantId, caseId, s.getChannel(), s.getCounterparty(), s.getBody(), at,
                    s.getExternalReference(), actorId, actorName);
        }
        return markResolved(tenantId, id, caseId, note, actorId);
    }

    @Transactional
    public SuspenseItem dismiss(TenantId tenantId, UUID id, String reason, UUID actorId) {
        SuspenseItem s = find(tenantId, id);
        s.dismiss(reason, actorId);
        return repository.save(s);
    }

    private SuspenseItem markResolved(TenantId tenantId, UUID id, UUID caseId, String note, UUID actorId) {
        SuspenseItem s = find(tenantId, id);
        s.resolve(caseId, note, actorId);
        return repository.save(s);
    }

    private SuspenseItem find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("SuspenseItem", id.toString()));
    }

    private static PaymentSource source(String s) {
        try {
            return s == null ? PaymentSource.GATEWAY : PaymentSource.valueOf(s);
        } catch (IllegalArgumentException e) {
            return PaymentSource.GATEWAY;
        }
    }
}
