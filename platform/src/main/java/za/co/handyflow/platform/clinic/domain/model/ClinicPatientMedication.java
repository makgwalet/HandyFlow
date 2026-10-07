package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "clinic_patient_medications")
@Getter
@NoArgsConstructor
public class ClinicPatientMedication {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID   tenantId;
    @Column(name = "patient_id")      UUID   patientId;
    @Column(name = "medicine_name")   String medicineName;
    @Column(name = "nappi_code")      String nappiCode;
    String dose;
    String frequency;
    String status = "ACTIVE";
    String source = "PATIENT_REPORTED";
    @Column(name = "started_on")      LocalDate startedOn;
    @Column(name = "stopped_on")      LocalDate stoppedOn;
    @Column(name = "stop_reason")     String stopReason;
    @Column(name = "prescription_id") UUID   prescriptionId;
    String notes;
    @Column(name = "recorded_by")     UUID    recordedBy;
    @Column(name = "created_at")      Instant createdAt;
    @Column(name = "updated_at")      Instant updatedAt;

    public static ClinicPatientMedication create(TenantId tenantId, UUID patientId, String medicineName,
                                                 String nappiCode, String dose, String frequency,
                                                 String source, LocalDate startedOn, UUID prescriptionId,
                                                 String notes, UUID recordedBy) {
        ClinicPatientMedication m = new ClinicPatientMedication();
        m.id             = UUID.randomUUID();
        m.tenantId       = tenantId.getValue();
        m.patientId      = patientId;
        m.medicineName   = medicineName;
        m.nappiCode      = nappiCode;
        m.dose           = dose;
        m.frequency      = frequency;
        m.source         = source == null ? "PATIENT_REPORTED" : source;
        m.startedOn      = startedOn;
        m.prescriptionId = prescriptionId;
        m.notes          = notes;
        m.recordedBy     = recordedBy;
        m.createdAt      = Instant.now();
        m.updatedAt      = m.createdAt;
        return m;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    /** Null arguments leave the field unchanged. Stopping a medicine stamps stoppedOn if not given. */
    public void update(String dose, String frequency, String status, LocalDate stoppedOn,
                       String stopReason, String notes) {
        if (dose       != null) this.dose       = dose;
        if (frequency  != null) this.frequency  = frequency;
        if (status     != null) this.status     = status;
        if (stoppedOn  != null) this.stoppedOn  = stoppedOn;
        if (stopReason != null) this.stopReason = stopReason;
        if (notes      != null) this.notes      = notes;
        if (("STOPPED".equals(this.status) || "COMPLETED".equals(this.status)) && this.stoppedOn == null) {
            this.stoppedOn = LocalDate.now();
        }
        this.updatedAt = Instant.now();
    }
}
