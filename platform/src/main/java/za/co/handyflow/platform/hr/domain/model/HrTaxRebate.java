package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "hr_tax_rebates")
@Getter
@NoArgsConstructor
public class HrTaxRebate {

    @Id UUID id;
    @Column(name = "tax_year")    int taxYear;
    @Column(name = "rebate_type") String rebateType;
    BigDecimal amount;
}