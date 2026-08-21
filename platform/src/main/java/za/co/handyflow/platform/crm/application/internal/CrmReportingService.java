package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.CustomerActivity;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.repository.CustomerActivityRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.FunnelReportResponse;
import za.co.handyflow.platform.crm.dto.FunnelReportResponse.StageFunnelEntry;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * FIX: backlog 4.3 — "no conversion-rate/funnel reporting." CustomerType's
 * own doc comment cites this as a designed-for use case; the underlying
 * data (STAGE_CHANGED activity events) already existed. Reporting-layer
 * work only, exactly as the backlog scoped it — no new entity, no
 * migration, nothing written anywhere. Reads CustomerActivity, computes,
 * returns.
 * <p>
 * WHY NEW ISN'T READ FROM AN ACTIVITY: Customer.create() sets
 * pipelineStage = NEW directly, without going through changeStage() (which
 * is what actually records a STAGE_CHANGED activity) — there's no
 * "transition into NEW" event because every lead starts there by
 * construction, not by a tracked transition. This service treats each
 * lead's own createdAt as its implicit entry into NEW, then walks that
 * lead's real STAGE_CHANGED activities (already sorted per-customer,
 * chronologically, by the repository query) to reconstruct the rest of
 * the journey.
 * <p>
 * WHY "TIME IN STAGE" ONLY COUNTS COMPLETED STAYS: a lead currently
 * sitting in QUALIFIED for one day looks identical, mid-calculation, to
 * one that's been stuck there for six months — until it actually moves.
 * Including still-open stays would let a flood of brand-new leads (all
 * with near-zero time-in-stage so far) drag the average down in a way
 * that doesn't reflect reality. Only stays that have actually ended
 * (the lead moved to a next stage) are averaged.
 * <p>
 * WHY conversionFromPreviousStage USES LeadStage'S DECLARED ENUM ORDER
 * (NEW→CONTACTED→QUALIFIED→WON→LOST) RATHER THAN A BRANCHING FUNNEL
 * MODEL: WON and LOST are really two different exits from QUALIFIED, not
 * sequential steps after each other — a more precise model would treat
 * the funnel as branching at that point rather than linear. The backlog
 * asked for "stage-to-stage conversion rates," not a full branching-
 * funnel visualization; this is the simple, defensible reading of that
 * ask. Worth revisiting if a real branching-funnel view turns out to be
 * wanted later — flagging the simplification rather than presenting it
 * as the only possible interpretation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmReportingService {

    private final CustomerRepository customerRepository;
    private final CustomerActivityRepository activityRepository;

    private record StageEntry(LeadStage stage, Instant enteredAt) {}

    @Transactional(readOnly = true)
    public FunnelReportResponse getFunnelReport(TenantId tenantId) {
        var leads = customerRepository.findAllActiveLeads(tenantId);
        if (leads.isEmpty()) {
            return new FunnelReportResponse(
                    Arrays.stream(LeadStage.values())
                            .map(s -> new StageFunnelEntry(s, 0, s == LeadStage.NEW ? null : BigDecimal.ZERO, null))
                            .toList(),
                    0, 0, 0, 0, null);
        }

        // Group this tenant's STAGE_CHANGED activities by customer —
        // already sorted chronologically within each customer by the
        // repository query (ORDER BY customer.id, createdAt ASC), so a
        // LinkedHashMap keyed by customer id preserves that per-customer
        // order without a second sort here.
        Map<UUID, List<CustomerActivity>> changesByCustomer = new LinkedHashMap<>();
        for (CustomerActivity a : activityRepository.findStageChangesByTenant(tenantId)) {
            changesByCustomer.computeIfAbsent(a.getCustomer().getId(), k -> new ArrayList<>()).add(a);
        }

        // reachedCount[stage] and the completed-stay durations feeding avgDaysInStage
        Map<LeadStage, Long> reached = new EnumMap<>(LeadStage.class);
        Map<LeadStage, List<Double>> completedStayDays = new EnumMap<>(LeadStage.class);
        for (LeadStage s : LeadStage.values()) {
            reached.put(s, 0L);
            completedStayDays.put(s, new ArrayList<>());
        }

        long totalWon = 0, totalLost = 0;

        for (var lead : leads) {
            List<StageEntry> journey = new ArrayList<>();
            journey.add(new StageEntry(LeadStage.NEW, lead.getCreatedAt()));

            for (CustomerActivity activity : changesByCustomer.getOrDefault(lead.getId(), List.of())) {
                Object to = activity.getPayload() != null ? activity.getPayload().get("to") : null;
                if (to == null) continue; // malformed/legacy payload — skip rather than throw on a report
                try {
                    journey.add(new StageEntry(LeadStage.valueOf(to.toString()), activity.getCreatedAt()));
                } catch (IllegalArgumentException e) {
                    log.warn("[CRM] Funnel report: unrecognized stage '{}' in activity={} — skipped", to, activity.getId());
                }
            }

            Set<LeadStage> reachedByThisLead = new HashSet<>();
            for (int i = 0; i < journey.size(); i++) {
                LeadStage stage = journey.get(i).stage();
                reachedByThisLead.add(stage);
                if (i + 1 < journey.size()) {
                    double days = Duration.between(journey.get(i).enteredAt(), journey.get(i + 1).enteredAt())
                            .toMinutes() / (60.0 * 24.0);
                    completedStayDays.get(stage).add(days);
                }
            }
            for (LeadStage s : reachedByThisLead) {
                reached.merge(s, 1L, Long::sum);
            }

            LeadStage finalStage = journey.get(journey.size() - 1).stage();
            if (finalStage == LeadStage.WON) totalWon++;
            if (finalStage == LeadStage.LOST) totalLost++;
        }

        long totalLeads = leads.size();
        long totalStillOpen = totalLeads - totalWon - totalLost;

        LeadStage[] order = LeadStage.values();
        List<StageFunnelEntry> stages = new ArrayList<>();
        for (int i = 0; i < order.length; i++) {
            LeadStage stage = order[i];
            long reachedCount = reached.get(stage);

            BigDecimal conversion = null;
            if (i > 0) {
                long prevReached = reached.get(order[i - 1]);
                conversion = prevReached == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(reachedCount)
                        .divide(BigDecimal.valueOf(prevReached), 4, RoundingMode.HALF_UP);
            }

            List<Double> stays = completedStayDays.get(stage);
            Double avgDays = stays.isEmpty() ? null
                    : stays.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            stages.add(new StageFunnelEntry(stage, reachedCount, conversion, avgDays));
        }

        BigDecimal overallConversionRate = totalLeads == 0 ? null
                : BigDecimal.valueOf(totalWon).divide(BigDecimal.valueOf(totalLeads), 4, RoundingMode.HALF_UP);

        return new FunnelReportResponse(stages, totalLeads, totalWon, totalLost, totalStillOpen, overallConversionRate);
    }
}