package za.co.handyflow.platform.contracting.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.contracting.application.internal.ApprovalEscalationService;
import za.co.handyflow.platform.contracting.domain.model.ContractEscalationSettings;
import za.co.handyflow.platform.contracting.domain.repository.ContractEscalationSettingsRepository;
import za.co.handyflow.platform.shared.SchedulerLease;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.util.UUID;

/**
 * Every fifteen minutes, looks at the waiting approval requests of each organisation that has switched escalation on and takes any reminder or
 * escalation step that is due. It only follows requests up; it never approves, rejects or cancels one. Each request is handled in its own
 * transaction (through the service, so the transaction applies) and a failure on one is logged and does not hold up the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalEscalationScheduler {

    static final String LOCK = "contracting-approval-escalation";

    private final SchedulerLease lease;
    private final ContractEscalationSettingsRepository settingsRepo;
    private final ApprovalEscalationService service;

    @Scheduled(cron = "0 */15 * * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        lease.runExclusively(LOCK, Duration.ofMinutes(10), this::run);
    }

    void run() {
        int steps = 0, failures = 0;
        for (ContractEscalationSettings s : settingsRepo.findAllEnabled()) {
            TenantId tenant = TenantId.of(s.getTenantId());
            try {
                for (UUID id : service.pendingIds(s.getTenantId())) {
                    try {
                        if (service.process(tenant, id)) steps++;
                    } catch (Exception e) {
                        failures++;
                        log.warn("[Contracting] Escalation failed for approval {} tenant={}: {}", id, s.getTenantId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                failures++;
                log.warn("[Contracting] Escalation sweep failed for tenant={}: {}", s.getTenantId(), e.getMessage());
            }
        }
        if (steps > 0 || failures > 0) log.info("[Contracting] Approval escalation sweep: {} step(s) taken, {} failure(s)", steps, failures);
    }
}
