package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmCreditNoteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientProfitabilityResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Per-client profitability, computed straight from approved invoices and completed work orders — no
 * separate figures to drift out of step. See {@link FmClientProfitabilityResponse} for exact definitions.
 * Limited to the caller's client scope.
 * <p>
 * Caveat: work orders completed before Chunk 7 have their internal cost back-filled equal to their charge
 * (zero margin) because the real cost was never recorded.
 */
@Service
@RequiredArgsConstructor
public class FmProfitabilityService {

    private static final long MAX_SPAN_DAYS = 732; // two years

    private final FmInvoiceRepository invoiceRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmCreditNoteRepository creditNoteRepository;
    private final FmAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public FmClientProfitabilityResponse clientProfitability(TenantId tenantId, UUID clientId, LocalDate from, LocalDate to) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_SPAN_DAYS) {
            throw new IllegalArgumentException("The reporting period cannot exceed " + MAX_SPAN_DAYS + " days");
        }

        Instant fromInstant = from.atStartOfDay(FmTime.ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(FmTime.ZONE).toInstant();

        // Recognised revenue is invoiced revenue less approved credit notes issued in the same period.
        BigDecimal revenue = nz(invoiceRepository.sumRevenueForClient(tenantId, clientId, from, to))
                .subtract(nz(creditNoteRepository.sumApprovedSubtotalForClient(tenantId, clientId, from, to)));
        BigDecimal internalCost = nz(workOrderRepository.sumInternalCostForClient(tenantId, clientId, fromInstant, toExclusive));
        long completed = workOrderRepository.countCompletedForClient(tenantId, clientId, fromInstant, toExclusive);
        BigDecimal unbilled = nz(workOrderRepository.sumUnbilledChargeForClient(tenantId, clientId));

        BigDecimal margin = revenue.subtract(internalCost);
        BigDecimal marginPercent = revenue.signum() > 0
                ? margin.multiply(new BigDecimal("100")).divide(revenue, 2, RoundingMode.HALF_UP) : null;

        return new FmClientProfitabilityResponse(clientId, from, to, revenue, internalCost, margin, marginPercent,
                unbilled, completed);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}