package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.Contract;
import za.co.handyflow.platform.contracting.domain.model.ContractObligation;
import za.co.handyflow.platform.contracting.domain.model.ContractRenewalDecision;
import za.co.handyflow.platform.contracting.domain.repository.ContractObligationRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRenewalDecisionRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractRepository;
import za.co.handyflow.platform.contracting.dto.ContractReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Portfolio reporting over recorded contract, obligation and renewal data. Read-only. */
@Service
@RequiredArgsConstructor
public class ContractReportService {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final int EXPORT_LIMIT = 10_000;

    private final ContractRepository contractRepo;
    private final ContractObligationRepository obligationRepo;
    private final ContractRenewalDecisionRepository decisionRepo;
    private final ContractingService contracting;

    @Transactional(readOnly = true)
    public PortfolioReport portfolio(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusMonths(12);
        if (start.isAfter(end)) throw new IllegalArgumentException("The start date must not be after the end date");
        if (ChronoUnit.DAYS.between(start, end) > 366 * 3) throw new IllegalArgumentException("A report can cover at most three years");
        Instant fromI = start.atStartOfDay(ZONE).toInstant(), toI = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        return new PortfolioReport(start, end,
                turnaround(contractRepo.signingTimes(tenantId, fromI, toI)),
                signedByType(contractRepo.signedPortfolio(tenantId)),
                expiryProfile(contractRepo.signedPortfolio(tenantId), today),
                obligationStats(obligationRepo.findDueBetween(tenantId.getValue(), start, end), today),
                renewalStats(tenantId, fromI, toI, today),
                new TemplateCurrency(contracting.publishedTemplateLegalStatusCounts(tenantId)));
    }

    // ── pure calculations (static so they can be tested without a database) ───

    static Turnaround turnaround(List<Object[]> rows) {
        List<SlowContract> all = new ArrayList<>();
        for (Object[] r : rows) {
            Instant sent = (Instant) r[2], signed = (Instant) r[3];
            if (sent == null || signed == null || signed.isBefore(sent)) continue;
            all.add(new SlowContract((String) r[0], (String) r[1], Duration.between(sent, signed).toMinutes() / 1440.0));
        }
        if (all.isEmpty()) return new Turnaround(0, 0, 0, 0, List.of());
        List<Double> days = all.stream().map(SlowContract::days).sorted().toList();
        int n = days.size();
        double avg = days.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median = n % 2 == 1 ? days.get(n / 2) : (days.get(n / 2 - 1) + days.get(n / 2)) / 2;
        double p90 = days.get((int) Math.ceil(0.9 * n) - 1);
        List<SlowContract> slowest = all.stream().sorted(Comparator.comparingDouble(SlowContract::days).reversed()).limit(10)
                .map(s -> new SlowContract(s.contractNumber(), s.title(), round1(s.days()))).toList();
        return new Turnaround(n, round1(avg), round1(median), round1(p90), slowest);
    }

    static List<TypeValue> signedByType(List<Object[]> rows) {
        Map<String, long[]> counts = new TreeMap<>();
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (Object[] r : rows) {
            String type = (String) r[0];
            BigDecimal v = (BigDecimal) r[1];
            long[] c = counts.computeIfAbsent(type, k -> new long[2]);
            c[0]++;
            if (v == null) c[1]++;
            totals.merge(type, v == null ? BigDecimal.ZERO : v, BigDecimal::add);
        }
        return counts.entrySet().stream().map(e -> new TypeValue(e.getKey(), e.getValue()[0], totals.get(e.getKey()), e.getValue()[1])).toList();
    }

    /** The twelve calendar months starting with the current one; a signed contract counts in the month its end date falls in. */
    static List<ExpiryMonth> expiryProfile(List<Object[]> rows, LocalDate today) {
        YearMonth first = YearMonth.from(today);
        Map<YearMonth, long[]> n = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> v = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) { n.put(first.plusMonths(i), new long[1]); v.put(first.plusMonths(i), BigDecimal.ZERO); }
        for (Object[] r : rows) {
            LocalDate endDate = (LocalDate) r[2];
            if (endDate == null || endDate.isBefore(today)) continue;
            YearMonth m = YearMonth.from(endDate);
            if (!n.containsKey(m)) continue;
            n.get(m)[0]++;
            if (r[1] != null) v.merge(m, (BigDecimal) r[1], BigDecimal::add);
        }
        return n.keySet().stream().map(m -> new ExpiryMonth(m.toString(), n.get(m)[0], v.get(m))).toList();
    }

    static ObligationStats obligationStats(List<ContractObligation> due, LocalDate today) {
        long onTime = 0, late = 0, waived = 0, overdue = 0, notYet = 0;
        for (ContractObligation o : due) {
            switch (o.getStatus()) {
                case "COMPLETED" -> {
                    LocalDate done = o.getCompletedAt() == null ? null : o.getCompletedAt().atZone(ZONE).toLocalDate();
                    if (done != null && done.isAfter(o.getDueDate())) late++; else onTime++;
                }
                case "WAIVED" -> waived++;
                default -> { if (o.getDueDate().isBefore(today)) overdue++; else notYet++; }
            }
        }
        long completed = onTime + late;
        return new ObligationStats(due.size(), onTime, late, waived, overdue, notYet, completed == 0 ? null : round3((double) onTime / completed));
    }

    private RenewalStats renewalStats(TenantId tenantId, Instant from, Instant to, LocalDate today) {
        Map<String, Long> byType = decisionRepo.findDecidedBetween(tenantId.getValue(), from, to).stream()
                .collect(Collectors.groupingBy(ContractRenewalDecision::getDecision, TreeMap::new, Collectors.counting()));
        List<Contract> ending = contractRepo.findSignedEndingBetween(tenantId, today, today.plusDays(365));
        Set<UUID> decided = ending.isEmpty() ? Set.of() : decisionRepo.findByContracts(tenantId.getValue(), ending.stream().map(Contract::getId).toList())
                .stream().map(ContractRenewalDecision::getContractId).collect(Collectors.toSet());
        long passed = ending.stream().filter(c -> !decided.contains(c.getId()) && c.getEndDate().minusDays(c.getRenewalNoticeDays()).isBefore(today)).count();
        return new RenewalStats(byType, passed);
    }

    // ── CSV register ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String registerCsv(TenantId tenantId) {
        StringBuilder sb = new StringBuilder("Contract number,Title,Type,Status,Value,Currency,Start date,End date,Auto-renew,Notice days,Sent at,Signed at\n");
        for (Object[] r : contractRepo.register(tenantId, PageRequest.of(0, EXPORT_LIMIT))) {
            for (int i = 0; i < r.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(csv(r[i]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Quotes a cell and neutralises spreadsheet formulas (a leading = + - @ or tab would otherwise run when the file is opened). */
    static String csv(Object value) {
        if (value == null) return "";
        String s = value instanceof Instant i ? i.atZone(ZONE).toLocalDateTime().toString() : value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(value instanceof Number)) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static double round1(double d) { return Math.round(d * 10.0) / 10.0; }
    private static double round3(double d) { return Math.round(d * 1000.0) / 1000.0; }
}
