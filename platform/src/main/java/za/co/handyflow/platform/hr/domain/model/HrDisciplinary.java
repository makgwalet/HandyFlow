package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "hr_disciplinary")
@Getter
@NoArgsConstructor
public class HrDisciplinary {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    @Column(name = "employee_id")  UUID employeeId;
    @Column(name = "incident_date") LocalDate incidentDate;
    @Column(name = "incident_type") String incidentType;
    String description;
    String outcome;
    @Column(name = "hearing_date")  LocalDate hearingDate;
    @Column(name = "issued_by")     UUID issuedBy;
    boolean acknowledged = false;
    @Column(name = "acknowledged_at") Instant acknowledgedAt;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;

    public static HrDisciplinary create(TenantId tenantId, UUID employeeId,
                                        LocalDate incidentDate, String incidentType,
                                        String description, UUID issuedBy) {
        HrDisciplinary d = new HrDisciplinary();
        d.id           = UUID.randomUUID();
        d.tenantId     = tenantId.getValue();
        d.employeeId   = employeeId;
        d.incidentDate = incidentDate;
        d.incidentType = incidentType;
        d.description  = description;
        d.issuedBy     = issuedBy;
        d.acknowledged = false;
        d.createdAt    = Instant.now();
        d.updatedAt    = Instant.now();
        return d;
    }

    public void acknowledge() {
        this.acknowledged   = true;
        this.acknowledgedAt = Instant.now();
        this.updatedAt      = Instant.now();
    }

    public void setOutcome(String outcome, LocalDate hearingDate) {
        this.outcome     = outcome;
        this.hearingDate = hearingDate;
        this.updatedAt   = Instant.now();
    }
}