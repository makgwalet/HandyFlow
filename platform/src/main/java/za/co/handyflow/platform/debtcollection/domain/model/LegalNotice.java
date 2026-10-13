package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Proof-of-notice tracking for a statutory or formal notice (notably an NCA section 129 notice) — more than
 * "sent": which wording (noticeVersion), which address and where it came from, how it was dispatched, tracking,
 * the registered-post details a court may ask about (post-office branch, collection-notification reference),
 * delivery or returned/uncollected outcome, key dates, and a human legal review.
 * <p>
 * This records evidence and supports review; it does NOT decide that a notice was validly delivered — that is a
 * legal judgement, captured as reviewStatus by a reviewer who must differ from the person who recorded it.
 * The optional communicationId links the LETTER Communication that actually dispatched it.
 */
@Entity
@Table(name = "debtcollection_legal_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalNotice extends AggregateRoot<LegalNotice> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 20)
    private NoticeType noticeType;

    /** Identifies the exact wording/template version relied on (e.g. "S129-2026-03"). */
    @Column(name = "notice_version", nullable = false, length = 60)
    private String noticeVersion;

    @Column(name = "address_used", nullable = false, length = 500)
    private String addressUsed;

    /** Where the address came from (CONTRACT, CRM, DEBTOR_NOMINATED, TRACING, OTHER) — matters if delivery is disputed. */
    @Column(name = "address_source", length = 30)
    private String addressSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_method", length = 20)
    private LetterDispatchMethod dispatchMethod;

    @Column(name = "dispatched_date")
    private LocalDate dispatchedDate;

    @Column(name = "tracking_reference", length = 100)
    private String trackingReference;

    @Column(name = "post_office_branch", length = 150)
    private String postOfficeBranch;

    @Column(name = "collection_notice_reference", length = 100)
    private String collectionNoticeReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 25)
    private NoticeDeliveryStatus deliveryStatus;

    @Column(name = "delivered_date")
    private LocalDate deliveredDate;

    @Column(name = "delivery_detail", length = 500)
    private String deliveryDetail;

    /** Date the consumer fell into default, if known — input to the advisory date calculation only. */
    @Column(name = "default_date")
    private LocalDate defaultDate;

    @Column(name = "communication_id")
    private UUID communicationId;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 15)
    private NoticeReviewStatus reviewStatus;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_by_name", length = 255)
    private String reviewedByName;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "recorded_by_name", length = 255)
    private String recordedByName;

    public static LegalNotice record(TenantId tenantId, UUID caseId, NoticeType type, String noticeVersion,
                                     String addressUsed, String addressSource, LetterDispatchMethod method,
                                     LocalDate dispatchedDate, String trackingReference, String postOfficeBranch,
                                     String collectionNoticeReference, LocalDate defaultDate, UUID communicationId,
                                     UUID evidenceId, String notes, UUID recordedBy, String recordedByName) {
        if (type == null) throw new IllegalArgumentException("noticeType is required");
        if (noticeVersion == null || noticeVersion.isBlank()) throw new IllegalArgumentException("noticeVersion is required");
        if (addressUsed == null || addressUsed.isBlank()) throw new IllegalArgumentException("addressUsed is required");
        if (recordedBy == null) throw new IllegalArgumentException("recordedBy is required");
        LegalNotice n = new LegalNotice();
        n.initTenantId(tenantId);
        n.caseId = caseId;
        n.noticeType = type;
        n.noticeVersion = noticeVersion;
        n.addressUsed = addressUsed;
        n.addressSource = addressSource;
        n.dispatchMethod = method;
        n.dispatchedDate = dispatchedDate;
        n.trackingReference = trackingReference;
        n.postOfficeBranch = postOfficeBranch;
        n.collectionNoticeReference = collectionNoticeReference;
        n.defaultDate = defaultDate;
        n.communicationId = communicationId;
        n.evidenceId = evidenceId;
        n.notes = notes;
        n.deliveryStatus = NoticeDeliveryStatus.PENDING;
        n.reviewStatus = NoticeReviewStatus.NOT_REVIEWED;
        n.recordedBy = recordedBy;
        n.recordedByName = recordedByName;
        return n;
    }

    /** Any change to delivery facts invalidates an earlier review, which must be redone against the new evidence. */
    public void recordDelivery(NoticeDeliveryStatus status, LocalDate deliveredDate, String detail,
                               String postOfficeBranch, String collectionNoticeReference) {
        if (status == null) throw new IllegalArgumentException("delivery status is required");
        if (status == NoticeDeliveryStatus.DELIVERED && deliveredDate == null) {
            throw new IllegalArgumentException("deliveredDate is required when the notice is DELIVERED");
        }
        this.deliveryStatus = status;
        this.deliveredDate = status == NoticeDeliveryStatus.DELIVERED ? deliveredDate : null;
        this.deliveryDetail = detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail;
        if (postOfficeBranch != null) this.postOfficeBranch = postOfficeBranch;
        if (collectionNoticeReference != null) this.collectionNoticeReference = collectionNoticeReference;
        resetReview();
    }

    /** Refreshes dispatch/delivery facts from the linked LETTER communication; also resets review. */
    public void syncFrom(LocalDate dispatchedDate, LetterDispatchMethod method, String trackingReference,
                         NoticeDeliveryStatus status, LocalDate deliveredDate) {
        if (dispatchedDate != null) this.dispatchedDate = dispatchedDate;
        if (method != null) this.dispatchMethod = method;
        if (trackingReference != null) this.trackingReference = trackingReference;
        this.deliveryStatus = status;
        this.deliveredDate = status == NoticeDeliveryStatus.DELIVERED ? deliveredDate : null;
        resetReview();
    }

    /** Four-eyes: the reviewer must not be the person who recorded the notice. */
    public void review(NoticeReviewStatus outcome, String note, UUID by, String byName) {
        if (outcome == null || outcome == NoticeReviewStatus.NOT_REVIEWED) {
            throw new IllegalArgumentException("A review outcome of ACCEPTED or REJECTED is required");
        }
        if (note == null || note.isBlank()) throw new IllegalArgumentException("A review note is required");
        if (by == null || by.equals(recordedBy)) {
            throw new IllegalStateException("A notice must be reviewed by someone other than the person who recorded it");
        }
        this.reviewStatus = outcome;
        this.reviewNote = note;
        this.reviewedBy = by;
        this.reviewedByName = byName;
        this.reviewedAt = Instant.now();
    }

    private void resetReview() {
        this.reviewStatus = NoticeReviewStatus.NOT_REVIEWED;
        this.reviewedBy = null;
        this.reviewedByName = null;
        this.reviewedAt = null;
        this.reviewNote = null;
    }
}
