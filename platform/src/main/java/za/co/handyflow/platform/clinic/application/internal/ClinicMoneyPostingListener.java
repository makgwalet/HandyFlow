package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.clinic.ClaimMoneyEvent;

/**
 * Listens for claim money events (patch 0185). Runs after the money row is committed, so a posting problem can never undo a payment or a
 * write-off. Every event is recorded first; a write-off is then posted at once, and a scheme payment waits until someone chooses the bank
 * account it was received into.
 */
@Slf4j
@Component
class ClinicMoneyPostingListener {

    private final ClinicMoneyPostingService postings;

    ClinicMoneyPostingListener(ClinicMoneyPostingService postings) { this.postings = postings; }

    @ApplicationModuleListener
    void on(ClaimMoneyEvent e) {
        java.util.UUID id = postings.record(e);
        if (id == null || MoneyPostingRules.needsBank(e.type())) return;
        try {
            postings.post(e.tenantId(), id, null, e.byUserId());
        } catch (Exception ex) {
            log.error("Could not post {} for claim={} tenant={}: {}", e.type(), e.claimId(), e.tenantId(), ex.getMessage(), ex);
            postings.markFailed(e.tenantId(), id, ex.getMessage());
        }
    }
}
