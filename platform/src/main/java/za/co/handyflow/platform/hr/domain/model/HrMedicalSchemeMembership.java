package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One stretch of an employee's medical scheme membership. Never edited to rewrite history: a change closes this row
 * and adds a new one, so an old pay period can be recalculated with the cover it really had.
 */
@Entity
@Table(name = "hr_medical_scheme_memberships")
@Getter
@NoArgsConstructor
public class HrMedicalSchemeMembership {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID tenantId;
    @Column(name = "employee_id")    UUID employeeId;
    @Column(name = "scheme_name")    String schemeName;
    @Column(name = "member_number")  String memberNumber;
    @Column(name = "dependants")     int dependants;
    @Column(name = "effective_from") LocalDate effectiveFrom;
    @Column(name = "effective_to")   LocalDate effectiveTo;
    @Column(name = "notes")          String notes;
    @Column(name = "created_by")     UUID createdBy;
    @Column(name = "created_at")     Instant createdAt;

    public static HrMedicalSchemeMembership create(TenantId tenantId, UUID employeeId, String schemeName, String memberNumber,
                                                   int dependants, LocalDate from, String notes, UUID createdBy) {
        HrMedicalSchemeMembership m = new HrMedicalSchemeMembership();
        m.id = UUID.randomUUID();
        m.tenantId = tenantId.getValue();
        m.employeeId = employeeId;
        m.schemeName = schemeName;
        m.memberNumber = memberNumber;
        m.dependants = dependants;
        m.effectiveFrom = from;
        m.notes = notes;
        m.createdBy = createdBy;
        m.createdAt = Instant.now();
        return m;
    }

    public void closeOn(LocalDate lastDay) { this.effectiveTo = lastDay; }

    public MedicalSchemeRules.Cover toCover() {
        return new MedicalSchemeRules.Cover(effectiveFrom, effectiveTo, dependants);
    }
}
