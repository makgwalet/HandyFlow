package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinic_lab_results")
@Getter
@NoArgsConstructor
public class ClinicLabResult {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID   tenantId;
    @Column(name = "patient_id")      UUID   patientId;
    @Column(name = "consultation_id") UUID   consultationId;
    String source;
    @Column(name = "lab_reference")   String labReference;
    @Column(name = "collected_at")    Instant collectedAt;
    @Column(name = "received_at")     Instant receivedAt;
    @Column(name = "pdf_url")         String pdfUrl;
    @Column(name = "pdf_filename")    String pdfFilename;
    String status = "UNREVIEWED";
    @Column(name = "reviewed_by")     UUID   reviewedBy;
    @Column(name = "reviewed_at")     Instant reviewedAt;
    @Column(name = "patient_name_raw") String patientNameRaw;

    // Store parsed markers as JSON string — use TEXT column, read as String,
    // frontend parses JSON. Avoids JsonType dependency; upgradeable later.
    @Column(name = "parsed_markers", columnDefinition = "jsonb")
    String parsedMarkersJson;  // raw JSON string, e.g. "[{"marker":"HbA1c",...}]"

    String interpretation;
    boolean notified = false;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    public static ClinicLabResult create(TenantId tenantId, String source,
                                         String pdfUrl, String pdfFilename,
                                         String patientNameRaw, String labReference) {
        ClinicLabResult r = new ClinicLabResult();
        r.id             = UUID.randomUUID();
        r.tenantId       = tenantId.getValue();
        r.source         = source;
        r.pdfUrl         = pdfUrl;
        r.pdfFilename    = pdfFilename;
        r.patientNameRaw = patientNameRaw;
        r.labReference   = labReference;
        r.status         = "UNREVIEWED";
        r.notified       = false;
        r.receivedAt     = Instant.now();
        r.createdAt      = Instant.now();
        r.updatedAt      = Instant.now();
        return r;
    }

    // Added so ClinicLabService.uploadResult() can persist the collection date
    public void setCollectedAt(java.time.Instant collectedAt) {
        this.collectedAt = collectedAt;
        this.updatedAt   = Instant.now();
    }

    public void matchPatient(UUID patientId) {
        this.patientId  = patientId;
        this.updatedAt  = Instant.now();
    }

    public void setInterpretation(String interpretation) {
        this.interpretation = interpretation;
        this.updatedAt      = Instant.now();
    }

    public void markReviewed(UUID reviewedBy) {
        this.status     = "REVIEWED";
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
        this.updatedAt  = Instant.now();
    }

    public void file(UUID consultationId) {
        this.consultationId = consultationId;
        this.status         = "FILED";
        this.updatedAt      = Instant.now();
    }

    /**
     * FIX: "no lab result email" gap — notified existed as a real column
     * but nothing in this codebase ever set it (confirmed in an earlier
     * audit of this module). Called only after clinician review — never
     * on upload — since sending an unreviewed result straight to a
     * patient with no clinical context is a safety concern, not just a
     * technical one.
     */
    public void markNotified() {
        this.notified  = true;
        this.updatedAt = Instant.now();
    }
}