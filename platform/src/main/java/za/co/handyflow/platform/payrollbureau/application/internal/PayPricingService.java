package za.co.handyflow.platform.payrollbureau.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.payrollbureau.domain.model.PayBureauProfile;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClient;
import za.co.handyflow.platform.payrollbureau.domain.model.PayClientCharge;
import za.co.handyflow.platform.payrollbureau.domain.model.PayFeeNote;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRun;
import za.co.handyflow.platform.payrollbureau.domain.repository.*;
import za.co.handyflow.platform.payrollbureau.domain.rules.FeeRules;
import za.co.handyflow.platform.payrollbureau.dto.PayPricingDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Per-client price list, the bureau's cost per payslip, and the profitability report. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayPricingService {

    private static final int MAX_REPORT_DAYS = 400;

    private final PayClientRepository clientRepo;
    private final PayClientChargeRepository chargeRepo;
    private final PayBureauProfileRepository profileRepo;
    private final PayRunRepository runRepo;
    private final PayFeeNoteRepository feeNoteRepo;

    @Transactional(readOnly = true)
    public PricingResponse getPricing(TenantId tenantId, UUID clientId) {
        PayClient client = client(tenantId, clientId);
        return toPricing(client, chargeRepo.findActiveByClient(tenantId.getValue(), clientId));
    }

    @Transactional
    public PricingResponse updatePricing(TenantId tenantId, UUID clientId, UpdatePricingRequest req) {
        PayClient client = client(tenantId, clientId);
        String problem = FeeRules.pricingProblem(req.perEmployeeFee(), req.minimumFee());
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_PRICING");
        client.setPricing(req.perEmployeeFee(), req.minimumFee());
        clientRepo.save(client);
        log.info("Payroll bureau pricing changed client={} perEmployee={} minimum={}", clientId, req.perEmployeeFee(), req.minimumFee());
        return toPricing(client, chargeRepo.findActiveByClient(tenantId.getValue(), clientId));
    }

    @Transactional
    public ChargeResponse addCharge(TenantId tenantId, UUID clientId, CreateChargeRequest req) {
        client(tenantId, clientId);
        String problem = FeeRules.chargeProblem(req.description(), req.amount(), req.billing());
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "INVALID_CHARGE");
        PayClientCharge charge = chargeRepo.save(PayClientCharge.create(tenantId.getValue(), clientId,
                req.description().trim(), req.amount(), req.billing()));
        return toCharge(charge);
    }

    @Transactional
    public void archiveCharge(TenantId tenantId, UUID clientId, UUID chargeId) {
        client(tenantId, clientId);
        PayClientCharge charge = chargeRepo.findByTenantAndId(tenantId.getValue(), chargeId)
                .filter(c -> c.getPayClientId().equals(clientId))
                .orElseThrow(() -> new ResourceNotFoundException("PayClientCharge", chargeId.toString()));
        charge.archive();
        chargeRepo.save(charge);
    }

    @Transactional(readOnly = true)
    public CostResponse getCost(TenantId tenantId) {
        return new CostResponse(profileRepo.findByTenantId(tenantId.getValue())
                .map(PayBureauProfile::getCostPerPayslip).orElse(BigDecimal.ZERO));
    }

    @Transactional
    public CostResponse updateCost(TenantId tenantId, UpdateCostRequest req) {
        BigDecimal cost = req.costPerPayslip();
        if (cost.signum() < 0 || cost.compareTo(new BigDecimal("10000")) > 0) {
            throw new HandyFlowException("The cost per payslip must be between 0 and 10 000.", HttpStatus.BAD_REQUEST, "INVALID_COST");
        }
        PayBureauProfile profile = profileRepo.findByTenantId(tenantId.getValue())
                .orElseThrow(() -> new HandyFlowException("Set up the bureau profile first.", HttpStatus.BAD_REQUEST, "NO_PROFILE"));
        profile.setCostPerPayslip(cost.setScale(2, java.math.RoundingMode.HALF_UP));
        profileRepo.save(profile);
        return new CostResponse(profile.getCostPerPayslip());
    }

    /**
     * Per client: payslips and cost for runs paid in the period, revenue from fee notes invoiced in the period, and the
     * finalised runs in the period that have no active fee note yet. Revenue and cost use different dates, so a run paid
     * at the end of one month and invoiced at the start of the next appears in two different periods.
     */
    @Transactional(readOnly = true)
    public ProfitabilityResponse profitability(TenantId tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new HandyFlowException("Choose a valid period.", HttpStatus.BAD_REQUEST, "INVALID_PERIOD");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_REPORT_DAYS) {
            throw new HandyFlowException("The period can be at most about 13 months.", HttpStatus.BAD_REQUEST, "PERIOD_TOO_LONG");
        }
        UUID tid = tenantId.getValue();
        BigDecimal costPer = getCost(tenantId).costPerPayslip();

        Map<UUID, PayClient> clients = new HashMap<>();
        for (PayClient c : clientRepo.findAllActive(tid, PageRequest.of(0, 2000)).getContent()) clients.put(c.getId(), c);

        List<PayRun> runs = runRepo.findFinalisedInRange(tid, from, to);
        List<PayFeeNote> notes = feeNoteRepo.findBilledInRange(tid, from, to);
        Set<UUID> billedRunIds = new HashSet<>();
        if (!runs.isEmpty()) {
            billedRunIds.addAll(feeNoteRepo.findBilledRunIds(tid, runs.stream().map(PayRun::getId).toList()));
        }

        Map<UUID, Acc> byClient = new LinkedHashMap<>();
        for (PayRun r : runs) {
            Acc a = byClient.computeIfAbsent(r.getPayClientId(), k -> new Acc());
            int n = r.getEmployeeCount() == null ? 0 : r.getEmployeeCount();
            a.runs++;
            a.payslips += n;
            if (!billedRunIds.contains(r.getId())) {
                PayClient c = clients.get(r.getPayClientId());
                a.unbilledRuns++;
                if (c != null) a.unbilled = a.unbilled.add(FeeRules.runFee(c.getPerEmployeeFee(), n, c.getMinimumFeePerRun()).amount());
            }
        }
        for (PayFeeNote f : notes) {
            byClient.computeIfAbsent(f.getPayClientId(), k -> new Acc()).revenue = byClient.get(f.getPayClientId()).revenue.add(f.getSubtotal());
        }

        List<ProfitabilityClient> rows = new ArrayList<>();
        BigDecimal tRev = BigDecimal.ZERO, tCost = BigDecimal.ZERO, tUnbilled = BigDecimal.ZERO;
        int tUnbilledRuns = 0;
        for (Map.Entry<UUID, Acc> e : byClient.entrySet()) {
            Acc a = e.getValue();
            BigDecimal cost = costPer.multiply(BigDecimal.valueOf(a.payslips)).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal revenue = a.revenue.setScale(2, java.math.RoundingMode.HALF_UP);
            PayClient c = clients.get(e.getKey());
            rows.add(new ProfitabilityClient(e.getKey(), c == null ? "(removed client)" : c.getTradingName(), a.runs, a.payslips,
                    revenue, cost, revenue.subtract(cost), FeeRules.marginPercent(revenue, cost), a.unbilledRuns, a.unbilled));
            tRev = tRev.add(revenue);
            tCost = tCost.add(cost);
            tUnbilled = tUnbilled.add(a.unbilled);
            tUnbilledRuns += a.unbilledRuns;
        }
        rows.sort(Comparator.comparing(ProfitabilityClient::margin));
        return new ProfitabilityResponse(from, to, costPer, tRev, tCost, tRev.subtract(tCost),
                FeeRules.marginPercent(tRev, tCost), tUnbilledRuns, tUnbilled, rows);
    }

    private static final class Acc {
        int runs, payslips, unbilledRuns;
        BigDecimal revenue = BigDecimal.ZERO, unbilled = BigDecimal.ZERO;
    }

    private PayClient client(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId.getValue(), clientId)
                .orElseThrow(() -> new ResourceNotFoundException("PayClient", clientId.toString()));
    }

    private PricingResponse toPricing(PayClient c, List<PayClientCharge> charges) {
        return new PricingResponse(c.getPerEmployeeFee(), c.getMinimumFeePerRun(), charges.stream().map(this::toCharge).toList());
    }

    private ChargeResponse toCharge(PayClientCharge c) {
        return new ChargeResponse(c.getId(), c.getDescription(), c.getAmount(), c.getBilling());
    }
}
