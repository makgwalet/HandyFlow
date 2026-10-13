package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.application.internal.LegalNoticeService.Advisory;
import za.co.handyflow.platform.debtcollection.domain.model.LegalNotice;
import za.co.handyflow.platform.debtcollection.domain.model.LetterDispatchMethod;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeDeliveryStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeReviewStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** advisory is present for SECTION_129 notices only and is explicitly not a legal determination. */
public record NoticeResponse(
        UUID id, UUID caseId, NoticeType noticeType, String noticeVersion, String addressUsed, String addressSource,
        LetterDispatchMethod dispatchMethod, LocalDate dispatchedDate, String trackingReference, String postOfficeBranch,
        String collectionNoticeReference, NoticeDeliveryStatus deliveryStatus, LocalDate deliveredDate,
        String deliveryDetail, LocalDate defaultDate, UUID communicationId, UUID evidenceId,
        NoticeReviewStatus reviewStatus, String reviewedByName, Instant reviewedAt, String reviewNote, String notes,
        String recordedByName, Instant createdAt, Advisory advisory
) {
    public static NoticeResponse of(LegalNotice n, Advisory advisory) {
        return new NoticeResponse(n.getId(), n.getCaseId(), n.getNoticeType(), n.getNoticeVersion(), n.getAddressUsed(),
                n.getAddressSource(), n.getDispatchMethod(), n.getDispatchedDate(), n.getTrackingReference(),
                n.getPostOfficeBranch(), n.getCollectionNoticeReference(), n.getDeliveryStatus(), n.getDeliveredDate(),
                n.getDeliveryDetail(), n.getDefaultDate(), n.getCommunicationId(), n.getEvidenceId(), n.getReviewStatus(),
                n.getReviewedByName(), n.getReviewedAt(), n.getReviewNote(), n.getNotes(), n.getRecordedByName(),
                n.getCreatedAt(), advisory);
    }
}
