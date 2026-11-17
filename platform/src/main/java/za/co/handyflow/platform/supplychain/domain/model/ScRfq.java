package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.RfqStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A request for quotation sent to several suppliers, with the weighted criteria used to compare their answers. */
@Entity
@Table(name = "sc_rfqs")
@Getter
@NoArgsConstructor
public class ScRfq {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "rfq_number", nullable = false, length = 20) String rfqNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12) RfqStatus status = RfqStatus.DRAFT;
    @Column(nullable = false, length = 200) String title;
    @Column(columnDefinition = "TEXT") String description;
    @Column(name = "requisition_id") UUID requisitionId;
    @Column(name = "deliver_to_location") UUID deliverToLocation;
    @Column(name = "required_by_date") LocalDate requiredByDate;
    @Column(name = "closes_at") Instant closesAt;
    @Column(nullable = false, length = 3) String currency = "ZAR";
    @Column(name = "weight_price", nullable = false) int weightPrice;
    @Column(name = "weight_lead_time", nullable = false) int weightLeadTime;
    @Column(name = "weight_technical", nullable = false) int weightTechnical;
    @Column(name = "weight_performance", nullable = false) int weightPerformance;
    @Column(name = "weight_bbbee", nullable = false) int weightBbbee;
    @Column(name = "sole_source_justification", length = 500) String soleSourceJustification;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_by_name", length = 255) String createdByName;
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "closed_at") Instant closedAt;
    @Column(name = "awarded_quotation_id") UUID awardedQuotationId;
    @Column(name = "award_reason", length = 1000) String awardReason;
    @Column(name = "overrides_recommendation", nullable = false) boolean overridesRecommendation;
    @Column(name = "awarded_by") UUID awardedBy;
    @Column(name = "awarded_by_name", length = 255) String awardedByName;
    @Column(name = "awarded_at") Instant awardedAt;
    @Column(name = "po_id") UUID poId;
    @Column(name = "cancel_reason", length = 500) String cancelReason;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScRfq create(UUID tenantId, String number, String title, String description, UUID requisitionId,
                               UUID deliverToLocation, LocalDate requiredByDate, Instant closesAt, String currency,
                               int[] weights, UUID by, String byName) {
        ScRfq r = new ScRfq();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.rfqNumber = number;
        r.requisitionId = requisitionId;
        r.createdBy = by;
        r.createdByName = byName;
        r.createdAt = Instant.now();
        r.edit(title, description, deliverToLocation, requiredByDate, closesAt, currency, weights);
        return r;
    }

    /** Header edit — DRAFT only. weights = {price, leadTime, technical, performance, bbbee}. */
    public void edit(String title, String description, UUID deliverToLocation, LocalDate requiredByDate,
                     Instant closesAt, String currency, int[] w) {
        if (status != null && status != RfqStatus.DRAFT)
            throw new IllegalStateException("Only a DRAFT RFQ can be edited — current: " + status);
        this.title = title;
        this.description = description;
        this.deliverToLocation = deliverToLocation;
        this.requiredByDate = requiredByDate;
        this.closesAt = closesAt;
        this.currency = currency != null && !currency.isBlank() ? currency : "ZAR";
        this.weightPrice = w[0];
        this.weightLeadTime = w[1];
        this.weightTechnical = w[2];
        this.weightPerformance = w[3];
        this.weightBbbee = w[4];
        this.status = RfqStatus.DRAFT;
        this.updatedAt = Instant.now();
    }

    public void publish(String soleSourceJustification) {
        require(RfqStatus.DRAFT, "published");
        this.status = RfqStatus.OPEN;
        this.publishedAt = Instant.now();
        this.soleSourceJustification = soleSourceJustification;
        touch();
    }

    public void close() {
        require(RfqStatus.OPEN, "closed");
        this.status = RfqStatus.CLOSED;
        this.closedAt = Instant.now();
        touch();
    }

    /** Extends (or re-opens a closed) RFQ with a new closing time. Never once awarded. */
    public void reopen(Instant newClosesAt) {
        if (status != RfqStatus.OPEN && status != RfqStatus.CLOSED)
            throw new IllegalStateException("Only an OPEN or CLOSED RFQ can be extended — current: " + status);
        this.status = RfqStatus.OPEN;
        this.closesAt = newClosesAt;
        this.closedAt = null;
        touch();
    }

    public void cancel(String reason) {
        if (status == RfqStatus.AWARDED || status == RfqStatus.CANCELLED)
            throw new IllegalStateException("A " + status + " RFQ cannot be cancelled");
        this.status = RfqStatus.CANCELLED;
        this.cancelReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        touch();
    }

    public void award(UUID quotationId, String reason, boolean overridesRecommendation, UUID by, String byName) {
        require(RfqStatus.CLOSED, "awarded");
        this.status = RfqStatus.AWARDED;
        this.awardedQuotationId = quotationId;
        this.awardReason = reason.length() > 1000 ? reason.substring(0, 1000) : reason;
        this.overridesRecommendation = overridesRecommendation;
        this.awardedBy = by;
        this.awardedByName = byName;
        this.awardedAt = Instant.now();
        touch();
    }

    public void linkPo(UUID poId) {
        if (status != RfqStatus.AWARDED) throw new IllegalStateException("Only an AWARDED RFQ can produce a purchase order");
        if (this.poId != null) throw new IllegalStateException("A purchase order has already been created from this RFQ");
        this.poId = poId;
        touch();
    }

    public boolean isOpenForQuotes(Instant now) {
        return status == RfqStatus.OPEN && (closesAt == null || closesAt.isAfter(now));
    }

    private void require(RfqStatus expected, String verb) {
        if (status != expected)
            throw new IllegalStateException("Only a " + expected + " RFQ can be " + verb + " — current: " + status);
    }

    private void touch() { updatedAt = Instant.now(); }
}