package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPerformanceResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
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

        Instant fromInstant = from.atStartOfDay(FmTime.ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(FmTime.ZONE).toInstant();
        Instant now = Instant.now();
        List<FmWorkOrder> orders = workOrderRepository.findWithSlaForClient(tenantId, clientId, fromInstant, toExclusive);

        FmSlaTally tally = new FmSlaTally();
        for (FmWorkOrder w : orders) tally.add(w, now);

        return new FmSlaPerformanceResponse(clientId, from, to, orders.size(),
                tally.responseMet, tally.responseBreached, tally.responseOpen, tally.responseCompliance(),
                tally.resolutionMet, tally.resolutionBreached, tally.resolutionOpen, tally.resolutionCompliance(),
                tally.byPriority());
    }
}
