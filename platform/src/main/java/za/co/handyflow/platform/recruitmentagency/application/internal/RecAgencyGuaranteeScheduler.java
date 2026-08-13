package za.co.handyflow.platform.recruitmentagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyPlacement;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyPlacementStageHistory;
import za.co.handyflow.platform.recruitmentagency.domain.repository.RecAgencyPlacementRepository;
import za.co.handyflow.platform.recruitmentagency.domain.repository.RecAgencyPlacementStageHistoryRepository;
import za.co.handyflow.platform.recruitmentagency.domain.repository.RecAgencyProfileRepository;

import java.util.List;
import java.util.UUID;

/**
 * Closes the "happy path" half of the guarantee-period workflow flagged
 * as explicitly not built in the foundation pass (HandyFlow BOS
 * Discovery doc, Section 80) — a placement whose guarantee period
 * elapses without incident should transition to COMPLETED automatically,
 * not sit in PLACED forever waiting for a human to notice the date
 * passed.
 * <p>
 * THE UNHAPPY PATH — a candidate leaving within the guarantee window —
 * is deliberately NOT handled here. There is no signal anywhere in this
 * system that would tell a scheduled job a candidate resigned or was
 * let go; that's a real-world event only a human can report. See
 * RecruitmentAgencyService.failGuarantee() for that side of the
 * workflow, added in the same pass as this scheduler.
 * <p>
 * SCOPE ACROSS ALL TENANTS: unlike the per-tenant-scoped service
 * methods elsewhere in this module, a scheduled job legitimately needs
 * to sweep every tenant using this module — same pattern already
 * established for HandyFlow BOS Discovery doc's other cross-tenant
 * scheduled checks (Payroll Bureau's deadline generation is
 * per-request, not scheduled, but Fleet/Fuel/Earthmoving's compliance
 * schedulers all sweep across tenants the same way this does).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecAgencyGuaranteeScheduler {

    private final RecAgencyPlacementRepository placementRepo;
    private final RecAgencyPlacementStageHistoryRepository stageHistoryRepo;
    private final RecAgencyProfileRepository profileRepo;

    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Johannesburg") // daily at 06:00 SAST
    @Transactional
    public void sweepElapsedGuarantees() {
        // NOTE: iterates every tenant with an agency profile, then every
        // PLACED placement for that tenant — acceptable at the volume a
        // recruitment agency's placement pipeline realistically runs at
        // (this is not a high-cardinality sweep the way, say, POS
        // transaction volume would be). If that assumption stops holding
        // at scale, a single cross-tenant query would be the fix — not
        // done here since it would need TenantId used only for logging
        // and per-tenant transactional boundaries, not the query itself.
        List<UUID> tenantIds = profileRepo.findAll().stream()
                .map(p -> p.getTenantId())
                .toList();

        int completedCount = 0;
        for (UUID tenantId : tenantIds) {
            // FIX: caught before delivery — findAllPlaced() takes a raw
            // UUID (matching RecAgencyPlacement.tenantId's actual field
            // type), not a TenantId object. The earlier draft wrapped it
            // in TenantId.of(tenantId), which both used an unconfirmed
            // factory overload (flagged as a recurring risk elsewhere
            // this session) AND didn't match this repository's real
            // signature at all.
            List<RecAgencyPlacement> placed = placementRepo.findAllPlaced(tenantId);
            for (RecAgencyPlacement placement : placed) {
                if (placement.guaranteePeriodElapsed()) {
                    placement.completeGuaranteePeriod();
                    placementRepo.save(placement);
                    stageHistoryRepo.save(RecAgencyPlacementStageHistory.record(
                            placement.getId(), "PLACED", "COMPLETED",
                            "Guarantee period elapsed without incident (automated)", null));
                    completedCount++;
                }
            }
        }

        if (completedCount > 0) {
            log.info("[RecruitmentAgency] Guarantee sweep completed {} placement(s) across {} tenant(s)",
                    completedCount, tenantIds.size());
        }
    }
}