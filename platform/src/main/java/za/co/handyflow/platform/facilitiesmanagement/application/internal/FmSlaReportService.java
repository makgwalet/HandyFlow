package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPerformanceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * SLA performance for a client, computed from the work orders themselves using the same
 * {@link FmSlaStatus} rules as the API and monitor — so the report cannot disagree with what users see.
 * Limited to the caller's client scope.
 */
@Service
@RequiredArgsConstructor
public class FmSlaReportService {

    private static final long MAX_SPAN_DAYS = 732;

    private final FmWorkOrderRepository workOrderRepository;
    private final FmAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public FmSlaPerformanceResponse clientPerformance(TenantId tenantId, UUID clientId, LocalDate from, LocalDate to) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        if (to.isBefore(from)) throw new IllegalArgumentException("'to' must not be before 'from'");
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS)
            throw new IllegalArgumentException("The reporting period cannot exceed " + MAX_SPAN_DAYS + " days");

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant now = Instant.now();
        List<FmWorkOrder> orders = workOrderRepository.findWithSlaForClient(tenantId, clientId, fromInstant, toExclusive);

        long respMet = 0, respBreached = 0, respOpen = 0, resMet = 0, resBreached = 0, resOpen = 0;
        long[][] perPriority = new long[FmServiceAgreement.PRIORITIES.size()][3]; // total, responseBreached, resolutionBreached

        for (FmWorkOrder w : orders) {
            String r = FmSlaStatus.response(w, now);
            String s = FmSlaStatus.resolution(w, now);
            boolean respBreach = "BREACHED".equals(r);
            boolean resBreach = "BREACHED".equals(s);
            switch (r) {
                case "MET" -> respMet++;
                case "BREACHED" -> respBreached++;
                case "NONE" -> { /* no response SLA, or withdrawn */ }
                default -> respOpen++;
            }
            switch (s) {
                case "MET" -> resMet++;
                case "BREACHED" -> resBreached++;
                case "NONE" -> { /* no resolution SLA, or withdrawn */ }
                default -> resOpen++;
            }
            int idx = FmServiceAgreement.PRIORITIES.indexOf(w.getPriority());
            if (idx >= 0) {
                perPriority[idx][0]++;
                if (respBreach) perPriority[idx][1]++;
                if (resBreach) perPriority[idx][2]++;
            }
        }

        List<FmSlaPerformanceResponse.PriorityBreakdown> breakdown = new java.util.ArrayList<>();
        for (int i = 0; i < perPriority.length; i++) {
            if (perPriority[i][0] > 0) {
                breakdown.add(new FmSlaPerformanceResponse.PriorityBreakdown(
                        FmServiceAgreement.PRIORITIES.get(i), perPriority[i][0], perPriority[i][1], perPriority[i][2]));
            }
        }

        return new FmSlaPerformanceResponse(clientId, from, to, orders.size(),
                respMet, respBreached, respOpen, compliance(respMet, respBreached),
                resMet, resBreached, resOpen, compliance(resMet, resBreached), breakdown);
    }

    private BigDecimal compliance(long met, long breached) {
        long concluded = met + breached;
        if (concluded == 0) return null;
        return BigDecimal.valueOf(met).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(concluded), 2, RoundingMode.HALF_UP);
    }
}