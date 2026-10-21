package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.domain.model.HrStatutoryTaxYear;
import za.co.handyflow.platform.hr.domain.repository.HrStatutoryTaxYearRepository;
import za.co.handyflow.platform.hr.dto.TaxYearResponse;
import za.co.handyflow.platform.shared.SarsTaxRebate;
import za.co.handyflow.platform.shared.SarsTaxRebateRepository;
import za.co.handyflow.platform.shared.SarsTaxTable;
import za.co.handyflow.platform.shared.SarsTaxTableRepository;
import za.co.handyflow.platform.shared.StatutoryYearRules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gives payroll the approved statutory rules of a tax year, and refuses when there are none. There is no fallback to
 * another year's figures and no silent zero: a payslip with R0 tax because a table was missing is worse than no payslip.
 */
@Service
@RequiredArgsConstructor
public class StatutoryRulesService {

    private final HrStatutoryTaxYearRepository years;
    private final SarsTaxTableRepository tables;
    private final SarsTaxRebateRepository rebates;

    /** The rules of {@code taxYear}, or a plain 409 explaining why payroll cannot go ahead. */
    @Transactional(readOnly = true)
    public StatutoryYearRules rulesFor(int taxYear) {
        return find(taxYear).orElseThrow(() -> new IllegalStateException(
                "There are no approved statutory rules for tax year " + describe(taxYear)
                        + ". Payroll cannot use this period until they are loaded and approved."));
    }

    @Transactional(readOnly = true)
    public Optional<StatutoryYearRules> find(int taxYear) {
        HrStatutoryTaxYear y = years.findById(taxYear).orElse(null);
        if (y == null || !y.isApproved()) return Optional.empty();
        List<SarsTaxTable> lines = tables.findByTaxYear(taxYear);
        if (lines.isEmpty()) return Optional.empty();
        BigDecimal primary = rebate(taxYear, "PRIMARY"), secondary = rebate(taxYear, "SECONDARY"), tertiary = rebate(taxYear, "TERTIARY");
        if (primary == null || secondary == null || tertiary == null) return Optional.empty();
        List<StatutoryYearRules.Bracket> brackets = lines.stream()
                .map(t -> new StatutoryYearRules.Bracket(t.getIncomeFrom(), t.getIncomeTo(), t.getBaseTax(), t.getMarginalRate()))
                .toList();   // the query orders them by their floor
        return Optional.of(new StatutoryYearRules(taxYear, y.getEffectiveFrom(), y.getEffectiveTo(), brackets,
                primary, secondary, tertiary, y.getMtcTaxpayer(), y.getMtcFirstDependant(), y.getMtcAdditional(),
                y.getUifMonthlyCeiling(), y.getUifRateEmployee(), y.getUifRateEmployer(), y.getSdlRate(), y.getSdlThreshold(),
                y.getRetirementCapAmount(), y.getRetirementCapPct(), y.getTravelTaxablePct()));
    }

    @Transactional(readOnly = true)
    public List<TaxYearResponse> list() {
        return years.allNewestFirst().stream().map(this::toResponse).toList();
    }

    private TaxYearResponse toResponse(HrStatutoryTaxYear y) {
        List<String> problems = new ArrayList<>();
        List<SarsTaxTable> lines = tables.findByTaxYear(y.getTaxYear());
        if (lines.isEmpty()) problems.add("No tax brackets are loaded for this year.");
        BigDecimal primary = rebate(y.getTaxYear(), "PRIMARY"), secondary = rebate(y.getTaxYear(), "SECONDARY"), tertiary = rebate(y.getTaxYear(), "TERTIARY");
        if (primary == null || secondary == null || tertiary == null) problems.add("One or more rebates are missing for this year.");
        if (!y.isApproved()) problems.add("This year has not been approved.");
        return new TaxYearResponse(y.getTaxYear(), y.getLabel(), y.getEffectiveFrom(), y.getEffectiveTo(), y.getStatus(),
                problems.isEmpty(), problems, lines.size(), primary, secondary, tertiary,
                y.getMtcTaxpayer(), y.getMtcFirstDependant(), y.getMtcAdditional(), y.getUifMonthlyCeiling(),
                y.getRetirementCapAmount(), y.getRetirementCapPct(), y.getSourceNote(), y.getApprovedAt(), y.getApprovalNote());
    }

    private BigDecimal rebate(int taxYear, String type) {
        return rebates.findByTaxYear(taxYear).stream()
                .filter(r -> type.equals(r.getRebateType())).map(SarsTaxRebate::getAmount).findFirst().orElse(null);
    }

    /** "2027 (1 March 2026 to 28 February 2027)" */
    static String describe(int taxYear) {
        return taxYear + " (1 March " + (taxYear - 1) + " to the end of February " + taxYear + ")";
    }
}
