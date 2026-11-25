package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ProfitDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * Hours, cost and billing side by side, for administrators. Cost is labour only, and an hour with no cost rate is counted apart and never
 * priced by guesswork. Revenue is what bills raised from agreements brought in, without VAT; it does not include one-off work not billed
 * through an agreement. Nothing here is a ledger or changes one.
 */
@Service
@RequiredArgsConstructor
public class ComplianceProfitabilityService {

    public static final String BASIS = "Labour cost only, from recorded hours and each person's cost rate. Revenue is bills raised from agreements, without VAT, by billing date. "
            + "Hours with no cost rate are shown apart and are not priced.";

    private final ComplianceCostRateRepository rates;
    private final ComplianceTimeEntryRepository entries;
    private final ComplianceBillingEventRepository billing;
    private final ComplianceClientRepository clients;
    private final InvoicingFacade invoicing;

    @Transactional(readOnly = true)
    public List<CostRateView> costRates(TenantId tenantId) {
        List<CostRateView> out = new ArrayList<>();
        for (ComplianceCostRate r : rates.findAllForTenant(tenantId)) out.add(view(r));
        return out;
    }

    @Transactional
    public CostRateView setRate(TenantId tenantId, CostRateRequest r, UUID by) {
        if (rates.countOnDay(tenantId, r.userId(), r.effectiveFrom()) > 0) {
            throw new HandyFlowException("This person already has a rate from that day. Use a later day for a new rate", HttpStatus.CONFLICT, "COST_RATE_EXISTS");
        }
        ComplianceCostRate rate = guard(() -> ComplianceCostRate.of(tenantId, r.userId(), r.hourlyCost(), r.effectiveFrom(), by));
        return view(rates.save(rate));
    }

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new HandyFlowException("Choose a start day that is not after the end day", HttpStatus.BAD_REQUEST, "PROFIT_RANGE");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw new HandyFlowException("Choose a period of a year or less", HttpStatus.BAD_REQUEST, "PROFIT_RANGE");

        Map<UUID, List<ProfitRules.Rate>> ratesByUser = new HashMap<>();
        for (ComplianceCostRate r : rates.findAllForTenant(tenantId)) ratesByUser.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(new ProfitRules.Rate(r.getEffectiveFrom(), r.getHourlyCost()));

        Map<UUID, Acc> byClient = new LinkedHashMap<>();
        Map<String, Acc> byService = new TreeMap<>();
        Map<UUID, Acc> byPerson = new LinkedHashMap<>();
        Acc total = new Acc();
        for (ComplianceTimeEntry e : entries.findActiveBetween(tenantId, from, to)) {
            BigDecimal rate = ProfitRules.rateOn(ratesByUser.getOrDefault(e.getUserId(), List.of()), e.getWorkDate());
            BigDecimal cost = rate == null ? null : ProfitRules.costOf(e.getMinutes(), rate);
            for (Acc a : List.of(total, byClient.computeIfAbsent(e.getClientId(), k -> new Acc()), byService.computeIfAbsent(e.getServiceCode() == null ? "" : e.getServiceCode(), k -> new Acc()),
                    byPerson.computeIfAbsent(e.getUserId(), k -> new Acc()))) a.add(e, cost);
        }

        Map<UUID, ProfitRules.Revenue> revenue = revenue(tenantId, from, to);
        ProfitRules.Revenue all = ProfitRules.Revenue.none();
        for (ProfitRules.Revenue r : revenue.values()) all = all.add(r);

        Map<UUID, String> names = new HashMap<>();
        for (ComplianceClient c : clients.findAll(tenantId, null, Pageable.unpaged()).getContent()) names.put(c.getId(), c.getName());

        Set<UUID> clientIds = new LinkedHashSet<>(byClient.keySet());
        clientIds.addAll(revenue.keySet());
        List<ClientLine> lines = new ArrayList<>();
        for (UUID id : clientIds) {
            Acc a = byClient.getOrDefault(id, new Acc());
            ProfitRules.Revenue r = revenue.getOrDefault(id, ProfitRules.Revenue.none());
            BigDecimal cost = ProfitRules.money(a.cost);
            lines.add(new ClientLine(id, names.getOrDefault(id, "Client"), a.minutes, a.billable, a.rework, a.unplanned, a.unrated, a.unrated == 0, cost, r.invoiced(), r.collected(),
                    ProfitRules.money(r.invoiced().subtract(cost)), ProfitRules.marginPct(r.invoiced(), cost)));
        }
        lines.sort((x, y) -> y.margin().compareTo(x.margin()));

        List<ServiceLine> services = new ArrayList<>();
        byService.forEach((code, a) -> services.add(new ServiceLine(code.isEmpty() ? null : code, a.minutes, a.unrated, ProfitRules.money(a.cost))));
        services.sort((x, y) -> Integer.compare(y.minutes(), x.minutes()));
        List<PersonLine> people = new ArrayList<>();
        byPerson.forEach((id, a) -> people.add(new PersonLine(id, a.minutes, a.billable, a.unrated, ProfitRules.utilisationPct(a.billable, a.minutes), ProfitRules.money(a.cost))));
        people.sort((x, y) -> Integer.compare(y.minutes(), x.minutes()));

        return new Report(from, to, total.minutes, total.unrated, ProfitRules.money(total.cost), all.invoiced(), all.collected(), lines, services, people, BASIS);
    }

    /** Bills raised in the period, by client, valued from what Invoicing says about each invoice. */
    private Map<UUID, ProfitRules.Revenue> revenue(TenantId tenantId, LocalDate from, LocalDate to) {
        List<ComplianceBillingEvent> events = billing.findInvoicedBetween(tenantId, from, to);
        List<UUID> ids = new ArrayList<>();
        for (ComplianceBillingEvent e : events) ids.add(e.getInvoiceId());
        Map<UUID, InvoicingFacade.InvoiceSummary> byInvoice = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 400) {
            for (InvoicingFacade.InvoiceSummary s : invoicing.findInvoiceSummaries(tenantId, ids.subList(i, Math.min(ids.size(), i + 400)))) byInvoice.put(s.id(), s);
        }
        Map<UUID, ProfitRules.Revenue> out = new LinkedHashMap<>();
        for (ComplianceBillingEvent e : events) {
            InvoicingFacade.InvoiceSummary s = byInvoice.get(e.getInvoiceId());
            if (s == null) continue;
            ProfitRules.Revenue r = ProfitRules.revenueOf(e.getAmount(), s.status(), s.total(), s.amountPaid(), s.creditedAmount());
            out.merge(e.getClientId(), r, ProfitRules.Revenue::add);
        }
        return out;
    }

    /** Running totals for one group of entries. */
    private static final class Acc {
        int minutes, billable, rework, unplanned, unrated;
        BigDecimal cost = BigDecimal.ZERO;

        void add(ComplianceTimeEntry e, BigDecimal entryCost) {
            minutes += e.getMinutes();
            if (e.isBillable()) billable += e.getMinutes();
            if ("REWORK".equals(e.getKind())) rework += e.getMinutes();
            if ("UNPLANNED".equals(e.getKind())) unplanned += e.getMinutes();
            if (entryCost == null) unrated += e.getMinutes(); else cost = cost.add(entryCost);
        }
    }

    private static CostRateView view(ComplianceCostRate r) {
        return new CostRateView(r.getId(), r.getUserId(), r.getHourlyCost(), r.getEffectiveFrom(), r.getSetBy(), r.getSetAt());
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "COST_RATE_INVALID");
        }
    }
}
