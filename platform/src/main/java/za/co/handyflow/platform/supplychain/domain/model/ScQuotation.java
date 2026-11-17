package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One supplier's response to an RFQ (one per supplier; a revision replaces the lines and is audited).
 * landedCost = lines after discount + delivery + other charges, EX VAT — the figure comparison uses.
 */
@Entity
@Table(name = "sc_quotations")
@Getter
@NoArgsConstructor
public class ScQuotation {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "rfq_id", nullable = false) UUID rfqId;
    @Column(name = "supplier_id", nullable = false) UUID supplierId;
    @Column(name = "supplier_name", nullable = false, length = 255) String supplierName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 14) QuotationStatus status = QuotationStatus.SUBMITTED;
    @Column(name = "quote_reference", length = 100) String quoteReference;
    @Column(name = "received_at", nullable = false) Instant receivedAt;
    @Column(name = "valid_until") LocalDate validUntil;
    @Column(name = "lead_time_days", nullable = false) int leadTimeDays;
    @Column(name = "warranty_months") Integer warrantyMonths;
    @Column(name = "payment_terms_days") Integer paymentTermsDays;
    @Column(name = "delivery_cost", nullable = false, precision = 15, scale = 2) BigDecimal deliveryCost = BigDecimal.ZERO;
    @Column(name = "other_charges", nullable = false, precision = 15, scale = 2) BigDecimal otherCharges = BigDecimal.ZERO;
    @Column(nullable = false, precision = 15, scale = 2) BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "landed_cost", nullable = false, precision = 15, scale = 2) BigDecimal landedCost = BigDecimal.ZERO;
    @Column(name = "total_incl_vat", nullable = false, precision = 15, scale = 2) BigDecimal totalInclVat = BigDecimal.ZERO;
    @Column(name = "technical_score") Integer technicalScore;
    @Column(name = "technical_notes", length = 1000) String technicalNotes;
    @Column(name = "evaluated_by") UUID evaluatedBy;
    @Column(name = "evaluated_by_name", length = 255) String evaluatedByName;
    @Column(name = "status_reason", length = 500) String statusReason;
    @Column(length = 1000) String notes;
    @Column(name = "evidence_id") UUID evidenceId;
    @Column(name = "file_name", length = 300) String fileName;
    @Column(name = "content_type", length = 100) String contentType;
    @Column(name = "captured_by") UUID capturedBy;
    @Column(name = "captured_by_name", length = 255) String capturedByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScQuotation create(UUID tenantId, UUID rfqId, UUID supplierId, String supplierName, UUID by, String byName) {
        return createWithId(UUID.randomUUID(), tenantId, rfqId, supplierId, supplierName, by, byName);
    }

    /** The id is chosen up front because the quotation's lines are built (and reference it) before the row is saved. */
    public static ScQuotation createWithId(UUID id, UUID tenantId, UUID rfqId, UUID supplierId, String supplierName, UUID by, String byName) {
        ScQuotation q = new ScQuotation();
        q.id = id;
        q.tenantId = tenantId;
        q.rfqId = rfqId;
        q.supplierId = supplierId;
        q.supplierName = supplierName;
        q.capturedBy = by;
        q.capturedByName = byName;
        q.createdAt = Instant.now();
        q.receivedAt = Instant.now();
        q.updatedAt = Instant.now();
        return q;
    }

    /** Sets the commercial terms and totals; also used to apply a revision (resets technical scoring — the offer changed). */
    public void setOffer(String reference, LocalDate validUntil, int leadTimeDays, Integer warrantyMonths,
                         Integer paymentTermsDays, BigDecimal deliveryCost, BigDecimal otherCharges,
                         BigDecimal subtotal, BigDecimal totalInclVat, String notes, UUID by, String byName) {
        this.quoteReference = reference;
        this.validUntil = validUntil;
        this.leadTimeDays = leadTimeDays;
        this.warrantyMonths = warrantyMonths;
        this.paymentTermsDays = paymentTermsDays;
        this.deliveryCost = deliveryCost;
        this.otherCharges = otherCharges;
        this.subtotal = subtotal;
        this.landedCost = subtotal.add(deliveryCost).add(otherCharges);
        this.totalInclVat = totalInclVat;
        this.notes = notes;
        this.capturedBy = by;
        this.capturedByName = byName;
        this.status = QuotationStatus.SUBMITTED;
        this.statusReason = null;
        this.technicalScore = null;
        this.technicalNotes = null;
        this.evaluatedBy = null;
        this.evaluatedByName = null;
        this.receivedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void evaluate(int score, String notes, UUID by, String byName) {
        if (status != QuotationStatus.SUBMITTED) throw new IllegalStateException("Only a SUBMITTED quotation can be scored — current: " + status);
        if (score < 0 || score > 100) throw new IllegalArgumentException("Technical score must be between 0 and 100");
        this.technicalScore = score;
        this.technicalNotes = notes != null && notes.length() > 1000 ? notes.substring(0, 1000) : notes;
        this.evaluatedBy = by;
        this.evaluatedByName = byName;
        this.updatedAt = Instant.now();
    }

    public void disqualify(String reason) {
        if (status != QuotationStatus.SUBMITTED) throw new IllegalStateException("Only a SUBMITTED quotation can be disqualified — current: " + status);
        this.status = QuotationStatus.DISQUALIFIED;
        this.statusReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.updatedAt = Instant.now();
    }

    public void withdraw(String reason) {
        if (status != QuotationStatus.SUBMITTED) throw new IllegalStateException("Only a SUBMITTED quotation can be withdrawn — current: " + status);
        this.status = QuotationStatus.WITHDRAWN;
        this.statusReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.updatedAt = Instant.now();
    }

    public void attachFile(UUID evidenceId, String fileName, String contentType) {
        this.evidenceId = evidenceId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.updatedAt = Instant.now();
    }
}