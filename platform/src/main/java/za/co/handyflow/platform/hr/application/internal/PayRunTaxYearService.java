package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.hr.domain.model.HrPayRunEvent;
import za.co.handyflow.platform.hr.domain.model.HrPayslip;
import za.co.handyflow.platform.hr.domain.model.PayRunTaxYearRules;
import za.co.handyflow.platform.hr.domain.model.PayRunTaxYearRules.Verdict;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunEventRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayRunRepository;
import za.co.handyflow.platform.hr.domain.repository.HrPayslipRepository;
import za.co.handyflow.platform.hr.dto.PayRunTaxYearDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Corrects the tax-year LABEL on pay runs whose pay date says otherwise. Never recalculates anything: PAYE, UIF, SDL, net pay,
 * payslips and the run's status are left exactly as they are. Every correction leaves a pay-run event holding the original label.
 * Safe to repeat: a run that already agrees with its pay date is not touched again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayRunTaxYearService {

    static final String ACTION = "TAX_YEAR_CORRECTED";

    private final HrPayRunRepository runs;
    private final HrPayslipRepository payslips;
    private final HrPayRunEventRepository events;

    @Transactional(readOnly = true)
    public Preview preview(TenantId tenantId) {
        List<Row> rows = new ArrayList<>();
        int ok = 0, wrong = 0, review = 0;
        for (HrPayRun r : runs.findEveryRun(tenantId)) {
            if ("CANCELLED".equals(r.getStatus())) continue;
            var c = PayRunTaxYearRules.check(r.getPayDate(), r.getPeriodStart(), r.getPeriodEnd(), r.getTaxYear());
            List<HrPayslip> slips = payslips.findByPayRun(r.getId());
            int other = (int) slips.stream().filter(s -> s.getTaxYear() != null && s.getTaxYear() != c.proposedYear()).count();
            switch (c.verdict()) { case CORRECT -> ok++; case WRONG_LABEL -> wrong++; default -> review++; }
            rows.add(new Row(r.getId(), r.getPayRunNumber(), r.getPayDate(), r.getStatus(), isLocked(r.getStatus()),
                    r.getTaxYear(), PayRunTaxYearRules.label(r.getTaxYear()), c.proposedYear(), PayRunTaxYearRules.label(c.proposedYear()),
                    c.verdict().name(), c.reason(), slips.size(), other));
        }
        return new Preview(rows.size(), ok, wrong, review, rows);
    }

    @Transactional
    public Applied apply(TenantId tenantId, List<UUID> ids, String reason, UUID actor) {
        String ref = "TYL-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        int done = 0;
        List<String> skipped = new ArrayList<>();
        for (UUID id : ids.stream().distinct().toList()) {
            HrPayRun r = runs.findByTenantAndId(tenantId, id).orElse(null);
            if (r == null) { skipped.add(id + ": pay run not found"); continue; }
            var c = PayRunTaxYearRules.check(r.getPayDate(), r.getPeriodStart(), r.getPeriodEnd(), r.getTaxYear());
            if (c.verdict() == Verdict.CORRECT) { skipped.add(r.getPayRunNumber() + ": already agrees with its pay date"); continue; }
            if (c.verdict() == Verdict.NEEDS_REVIEW) { skipped.add(r.getPayRunNumber() + ": needs manual review. " + c.reason()); continue; }
            int from = r.getTaxYear();
            r.correctTaxYearLabel(c.proposedYear());
            runs.save(r);
            events.save(HrPayRunEvent.of(tenantId.getValue(), r.getId(), ACTION, r.getStatus(), r.getStatus(), actor,
                    "Tax year label " + PayRunTaxYearRules.label(from) + " corrected to " + PayRunTaxYearRules.label(c.proposedYear())
                            + ". Pay date " + r.getPayDate() + ". Amounts unchanged. Ref " + ref + ". " + reason.trim()));
            done++;
        }
        log.info("Pay run tax-year label correction ref={} corrected={} skipped={} tenant={}", ref, done, skipped.size(), tenantId);
        return new Applied(ref, done, skipped.size(), skipped);
    }

    private static boolean isLocked(String status) {
        return "FINALISED".equals(status) || "PAID".equals(status) || "ISSUED".equals(status);
    }
}
