package za.co.handyflow.platform.clinic.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A register entry for a stored document: uploaded from outside, or issued by the clinic (sick note, referral). */
@Entity
@Table(name = "clinic_patient_documents")
@Getter
@NoArgsConstructor
public class ClinicPatientDocument {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID      tenantId;
    @Column(name = "patient_id")      UUID      patientId;
    @Column(name = "consultation_id") UUID      consultationId;
    @Column(name = "doc_type")        String    docType;
    String source;
    String title;
    @Column(name = "document_date")   LocalDate documentDate;
    String notes;
    @Column(name = "storage_key")     String    storageKey;
    @Column(name = "file_name")       String    fileName;
    @Column(name = "content_type")    String    contentType;
    @Column(name = "size_bytes")      long      sizeBytes;
    @Column(name = "created_by")      UUID      createdBy;
    @Column(name = "created_at")      Instant   createdAt;
    @Column(name = "voided_at")       Instant   voidedAt;
    @Column(name = "voided_by")       UUID      voidedBy;
    @Column(name = "void_reason")     String    voidReason;

    public static ClinicPatientDocument create(TenantId t, UUID patientId, UUID consultationId, String docType, String source,
                                               String title, LocalDate documentDate, String notes, String storageKey,
                                               String fileName, String contentType, long sizeBytes, UUID createdBy) {
        var d = new ClinicPatientDocument();
        d.id = UUID.randomUUID(); d.tenantId = t.getValue(); d.patientId = patientId; d.consultationId = consultationId;
        d.docType = docType; d.source = source; d.title = title; d.documentDate = documentDate; d.notes = notes;
        d.storageKey = storageKey; d.fileName = fileName; d.contentType = contentType; d.sizeBytes = sizeBytes;
        d.createdBy = createdBy; d.createdAt = Instant.now();
        return d;
    }

    public boolean isVoided() { return voidedAt != null; }

    public void voidIt(UUID by, String reason) {
        if (isVoided()) throw new IllegalStateException("This document is already removed");
        this.voidedAt = Instant.now(); this.voidedBy = by; this.voidReason = reason;
    }
}
