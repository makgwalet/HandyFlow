package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The statutory parameters of one tax year (national, so no tenant). The tax year is the year it ENDS, as SARS names it.
 * Only APPROVED years are used by payroll; the brackets and rebates for the year live in hr_tax_tables / hr_tax_rebates.
 * Rows are loaded by migration; changing a figure means a new migration or an approved change, never a silent edit.
 */
@Entity
@Table(name = "hr_statutory_tax_years")
@Getter
@NoArgsConstructor
public class HrStatutoryTaxYear {

    @Id @Column(name = "tax_year") int taxYear;
    @Column(name = "label")                 String label;
    @Column(name = "effective_from")        LocalDate effectiveFrom;
    @Column(name = "effective_to")          LocalDate effectiveTo;
    @Column(name = "uif_monthly_ceiling")   BigDecimal uifMonthlyCeiling;
    @Column(name = "uif_rate_employee")     BigDecimal uifRateEmployee;
    @Column(name = "uif_rate_employer")     BigDecimal uifRateEmployer;
    @Column(name = "sdl_rate")              BigDecimal sdlRate;
    @Column(name = "sdl_threshold")         BigDecimal sdlThreshold;
    @Column(name = "retirement_cap_amount") BigDecimal retirementCapAmount;
    @Column(name = "retirement_cap_pct")    BigDecimal retirementCapPct;
    @Column(name = "travel_taxable_pct")    BigDecimal travelTaxablePct;
    @Column(name = "mtc_taxpayer")          BigDecimal mtcTaxpayer;
    @Column(name = "mtc_first_dependant")   BigDecimal mtcFirstDependant;
    @Column(name = "mtc_additional")        BigDecimal mtcAdditional;
    @Column(name = "status")                String status;
    @Column(name = "source_note")           String sourceNote;
    @Column(name = "approved_by")           UUID approvedBy;
    @Column(name = "approved_at")           Instant approvedAt;
    @Column(name = "approval_note")         String approvalNote;
    @Column(name = "created_at")            Instant createdAt;

    public boolean isApproved() { return "APPROVED".equals(status); }
}
