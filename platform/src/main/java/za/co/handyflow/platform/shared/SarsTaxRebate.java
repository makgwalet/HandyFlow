package za.co.handyflow.platform.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SARS annual tax rebate (PRIMARY/SECONDARY/TERTIARY, age-based) — moved
 * here from hr.domain.model.HrTaxRebate. Same reasoning and same
 * same-physical-table-no-migration approach as SarsTaxTable — see that
 * class's Javadoc for the full explanation.
 */
@Entity
@Table(name = "hr_tax_rebates")
@Getter
@NoArgsConstructor
public class SarsTaxRebate {

    @Id UUID id;
    @Column(name = "tax_year")    int taxYear;
    @Column(name = "rebate_type") String rebateType; // PRIMARY | SECONDARY | TERTIARY
    @Column(name = "amount")      BigDecimal amount;
}