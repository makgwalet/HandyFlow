package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.debtcollection.application.LegalHandoverStatusSync;
import za.co.handyflow.platform.debtcollection.domain.model.HandoverStatus;
import za.co.handyflow.platform.debtcollection.domain.model.LegalHandover;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemType;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalHandoverRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a legal-side status change to the linked handover (audited via LegalHandoverService) and, when the
 * matter reaches a terminal state (SETTLED / WITHDRAWN / CLOSED), raises a work item so a collector updates the
 * collection case — recording the payment, write-off or closure is a human decision, never automatic.
 * <p>
 * Each step runs in its own REQUIRES_NEW transaction, so a failure (e.g. the case was closed meanwhile) is
 * logged and contained: it cannot roll back the sync, and nothing here can affect the caller's transaction.
 */
@Slf4j
@Component
public class LegalHandoverStatusSyncImpl implements LegalHandoverStatusSync {

    private static final Set<String> TERMINAL = Set.of("SETTLED", "WITHDRAWN", "CLOSED");

    private final LegalHandoverRepository repository;
    private final LegalHandoverService handoverService;
    private final WorkItemService workItemService;
    private final TransactionTemplate isolated;

    public LegalHandoverStatusSyncImpl(LegalHandoverRepository repository, LegalHandoverService handoverService,
                                       WorkItemService workItemService, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.handoverService = handoverService;
        this.workItemService = workItemService;
        this.isolated = new TransactionTemplate(txManager);
        this.isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean syncByMatter(TenantId tenantId, UUID matterId, String legalStatus, String detail) {
        LegalHandover linked = isolated.execute(s -> {
            LegalHandover h = repository.findByLegalMatterId(tenantId, matterId).orElse(null);
            if (h == null || h.getStatus() != HandoverStatus.TRANSFERRED) return null;
            handoverService.syncLegalStatus(tenantId, h.getId(), legalStatus, detail, null, "legal-practice");
            return h;
        });
        if (linked == null) return false;

        if (legalStatus != null && TERMINAL.contains(legalStatus.toUpperCase())) {
            try {
                isolated.executeWithoutResult(s -> workItemService.raise(tenantId, linked.getCaseId(),
                        WorkItemType.FOLLOW_UP_DUE,
                        "Legal matter " + linked.getLegalMatterNumber() + " is " + legalStatus.toUpperCase()
                                + " — update the collection case (record payment, write off or close)",
                        LocalDate.now(), "LEGAL_STATUS:" + linked.getId() + ":" + legalStatus.toUpperCase(), null, "system"));
            } catch (Exception e) {
                log.warn("[Debt Collection] could not raise legal-outcome work item for handover {}: {}",
                        linked.getId(), e.getMessage());
            }
        }
        return true;
    }
}
