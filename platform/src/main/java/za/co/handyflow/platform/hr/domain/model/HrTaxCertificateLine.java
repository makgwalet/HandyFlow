package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One code and amount on an issued certificate. Never changed. */
@Entity
@Table(name = "hr_tax_certificate_lines")
@Getter
@NoArgsConstructor
public class HrTaxCertificateLine {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID tenantId;
    @Column(name = "certificate_id") UUID certificateId;
    String code;
    String description;
    BigDecimal amount;

    public static HrTaxCertificateLine of(UUID tenantId, UUID certificateId, TaxCertificateRules.Line line) {
        HrTaxCertificateLine l = new HrTaxCertificateLine();
        l.id = UUID.randomUUID();
        l.tenantId = tenantId;
        l.certificateId = certificateId;
        l.code = line.code();
        l.description = line.description();
        l.amount = line.amount();
        return l;
    }
}
