package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPerformanceResponse;
import za.co.handyflow.platform.shared.MaintenanceAnalytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Counts SLA outcomes for a set of work orders using {@link FmSlaStatus}. One tally for the per-client report and
 * the portfolio report, so the two can never disagree. "Open" means not yet met or breached (on track, at risk or
 * paused); "NONE" (no SLA, or withdrawn) is not counted.
 */
final class FmSlaTally {

    long workOrders;
    long responseMet, responseBreached, responseOpen;
    long resolutionMet, resolutionBreached, resolutionOpen;
    private final long[][] perPriority = new long[FmServiceAgreement.PRIORITIES.size()][3]; // total, responseBreached, resolutionBreached

    void add(FmWorkOrder w, Instant now) {
        workOrders++;
        String r = FmSlaStatus.response(w, now);
        String s = FmSlaStatus.resolution(w, now);
        switch (r) {
            case "MET" -> responseMet++;
            case "BREACHED" -> responseBreached++;
            case "NONE" -> { }
            default -> responseOpen++;
        }
        switch (s) {
            case "MET" -> resolutionMet++;
            case "BREACHED" -> resolutionBreached++;
            case "NONE" -> { }
            default -> resolutionOpen++;
        }
        int idx = FmServiceAgreement.PRIORITIES.indexOf(w.getPriority());
        if (idx >= 0) {
            perPriority[idx][0]++;
            if ("BREACHED".equals(r)) perPriority[idx][1]++;
            if ("BREACHED".equals(s)) perPriority[idx][2]++;
        }
    }

    BigDecimal responseCompliance() { return MaintenanceAnalytics.compliance(responseMet, responseBreached); }

    BigDecimal resolutionCompliance() { return MaintenanceAnalytics.compliance(resolutionMet, resolutionBreached); }

    List<FmSlaPerformanceResponse.PriorityBreakdown> byPriority() {
        List<FmSlaPerformanceResponse.PriorityBreakdown> out = new ArrayList<>();
        for (int i = 0; i < perPriority.length; i++) {
            if (perPriority[i][0] > 0) {
                out.add(new FmSlaPerformanceResponse.PriorityBreakdown(
                        FmServiceAgreement.PRIORITIES.get(i), perPriority[i][0], perPriority[i][1], perPriority[i][2]));
            }
        }
        return out;
    }
}
