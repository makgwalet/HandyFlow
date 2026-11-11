package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.repository.EventGuestRepository;
import za.co.handyflow.platform.events.domain.repository.EventPaymentRepository;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.dto.BudgetSummaryResponse;
import za.co.handyflow.platform.events.dto.ProfitabilityResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Revenue against cost. Revenue figures come from payment rows (money actually received,
 * refunds actually returned); costs come from the same BudgetCalculator the budget screens
 * use, so profit can never be computed from numbers that disagree with them.
 * <p>
 * Tickets registered the old way — staff typing an amount against a guest, with no payment
 * record behind it — are reported SEPARATELY as unverified rather than dropped, because
 * leaving them out would understate revenue and folding them in silently would overstate
 * confidence in it.
 */
@Service
@RequiredArgsConstructor
public class EventProfitabilityService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final EventRepository        eventRepo;
    private final EventGuestRepository   guestRepo;
    private final EventPaymentRepository paymentRepo;
    private final EventBudgetService     budgetService;

    @Transactional(readOnly = true)
    public ProfitabilityResponse get(TenantId tenantId, UUID eventId) {
        eventRepo.findActiveById(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        BigDecimal viaCheckout = nz(paymentRepo.sumReceived(eventId));
        BigDecimal staffRecorded = nz(guestRepo.sumAmountPaidWithoutPayment(eventId));
        BigDecimal refunds = nz(paymentRepo.sumRefunded(eventId));
        BigDecimal fees = nz(paymentRepo.sumFees(eventId));
        BigDecimal pending = nz(paymentRepo.sumPendingAmount(eventId, Instant.now()));
        BigDecimal owed = nz(paymentRepo.sumRefundsOwed(eventId));

        BudgetSummaryResponse budget = budgetService.getSummary(tenantId, eventId);

        BigDecimal gross = viaCheckout.add(staffRecorded);
        BigDecimal net = gross.subtract(refunds).subtract(fees);
        BigDecimal forecastProfit = net.subtract(budget.forecast());
        BigDecimal actualProfit = net.subtract(budget.invoiced());

        long checkedIn = guestRepo.countCheckedIn(eventId);
        return new ProfitabilityResponse(gross, viaCheckout, staffRecorded, refunds, fees, net, pending, owed,
                budget.forecast(), budget.invoiced(), forecastProfit, actualProfit,
                net.signum() > 0 ? forecastProfit.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP) : null,
                checkedIn > 0 ? net.divide(BigDecimal.valueOf(checkedIn), 2, RoundingMode.HALF_UP) : null,
                budget.costPerCheckedIn(), budget.budgetStatus());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}