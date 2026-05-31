package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "hr_tax_tables")
@Getter
@NoArgsConstructor
public class HrTaxTable {

    @Id UUID id;
    @Column(name = "tax_year")      int taxYear;
    @Column(name = "income_from")   BigDecimal incomeFrom;
    @Column(name = "income_to")     BigDecimal incomeTo;
    @Column(name = "base_tax")      BigDecimal baseTax;
    @Column(name = "marginal_rate") BigDecimal marginalRate;

    // WHY? Determines if annual income falls within this bracket
    public boolean contains(BigDecimal annualIncome) {
        boolean aboveFloor = annualIncome.compareTo(incomeFrom) >= 0;
        boolean belowCeil  = incomeTo == null || annualIncome.compareTo(incomeTo) < 0;
        return aboveFloor && belowCeil;
    }

    // WHY? Core PAYE formula per SARS:
    // tax = baseTax + (income - incomeFrom) * marginalRate / 100
    public BigDecimal calculateTax(BigDecimal annualIncome) {
        BigDecimal excess = annualIncome.subtract(incomeFrom);
        BigDecimal marginalTax = excess.multiply(marginalRate)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return baseTax.add(marginalTax);
    }
}