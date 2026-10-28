package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** The employer's tax reference numbers and the kind of retirement fund its employees belong to. */
@Entity
@Table(name = "hr_tax_settings")
@Getter
@NoArgsConstructor
public class HrTaxSettings {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "paye_reference")       String payeReference;
    @Column(name = "sdl_reference")        String sdlReference;
    @Column(name = "uif_reference")        String uifReference;
    @Column(name = "retirement_fund_type") String retirementFundType = TaxCertificateRules.PENSION;
    @Column(name = "contact_first_name")   String contactFirstName;
    @Column(name = "contact_surname")      String contactSurname;
    @Column(name = "sic7_code")            String sic7Code;
    @Column(name = "updated_by")           UUID updatedBy;
    @Column(name = "updated_at")           Instant updatedAt;

    public static HrTaxSettings of(UUID tenantId) {
        HrTaxSettings s = new HrTaxSettings();
        s.tenantId = tenantId;
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(String payeRef, String sdlRef, String uifRef, String fundType, UUID actor) {
        this.payeReference = blank(payeRef);
        this.sdlReference = blank(sdlRef);
        this.uifReference = blank(uifRef);
        this.retirementFundType = fundType;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    /** The contact person and industry code SARS asks for in the e@syFile import file. */
    public void updateFileDetails(String contactFirstName, String contactSurname, String sic7Code) {
        this.contactFirstName = contactFirstName == null || contactFirstName.isBlank() ? null : contactFirstName.trim();
        this.contactSurname = contactSurname == null || contactSurname.isBlank() ? null : contactSurname.trim();
        this.sic7Code = sic7Code == null || sic7Code.isBlank() ? null : sic7Code.replaceAll("\\s", "").toUpperCase();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.replaceAll("\\s", ""); }
}
