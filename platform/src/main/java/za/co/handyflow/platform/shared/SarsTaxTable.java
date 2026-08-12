package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SARS PAYE tax bracket — moved here from hr.domain.model.HrTaxTable.
 * <p>
 * WHY THIS MOVED: this is national tax reference data (the same bracket
 * table applies to every tenant, every employee in South Africa) — not
 * HR-specific business data. It was originally owned by `hr` because
 * PayrollEngine (hr's payroll calculator) was the first thing that
 * needed it, not because it's conceptually HR's data.
 * <p>
 * The payroll bureau module needs the exact same brackets to calculate
 * its clients' payroll correctly. Building a second copy in
 * `payrollbureau` would create a real compliance risk: two
 * independently-maintained tax tables that can silently drift apart
 * after a budget speech update (someone updates hr_tax_tables via the
 * admin UI, forgets the bureau's copy exists, and the bureau starts
 * calculating incorrect PAYE). Moving to `shared` — which every module
 * already declares as a dependency — eliminates that risk at the root:
 * one table, one source of truth, both engines read the same row.
 * <p>
 * SAME PHYSICAL TABLE, NO DATA MIGRATION: @Table still points at
 * hr_tax_tables — only the Java ownership moved, not the underlying
 * data. Existing rows, existing admin-edit endpoints
 * (AdminLookupService.updateTaxBracket(), which already uses raw JDBC
 * against this exact table name) are unaffected.
 */
@Entity
@Table(name = "hr_tax_tables")
@Getter
@NoArgsConstructor
public class SarsTaxTable {

    @Id UUID id;
    @Column(name = "tax_year")      int taxYear;
    @Column(name = "income_from")   BigDecimal incomeFrom;
    @Column(name = "income_to")     BigDecimal incomeTo;
    @Column(name = "base_tax")      BigDecimal baseTax;
    @Column(name = "marginal_rate") BigDecimal marginalRate;

    public boolean contains(BigDecimal annualIncome) {
        boolean aboveFloor = annualIncome.compareTo(incomeFrom) >= 0;
        boolean belowCeil  = incomeTo == null || annualIncome.compareTo(incomeTo) < 0;
        return aboveFloor && belowCeil;
    }

    /** Core PAYE formula per SARS: tax = baseTax + (income - incomeFrom) * marginalRate / 100 */
    public BigDecimal calculateTax(BigDecimal annualIncome) {
        BigDecimal excess = annualIncome.subtract(incomeFrom);
        BigDecimal marginalTax = excess.multiply(marginalRate)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return baseTax.add(marginalTax);
    }
}