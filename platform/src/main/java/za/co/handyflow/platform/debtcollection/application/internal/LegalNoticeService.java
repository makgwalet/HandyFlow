package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.domain.model.LegalEvent;
import za.co.handyflow.platform.debtcollection.domain.model.LegalNotice;
import za.co.handyflow.platform.debtcollection.domain.model.LetterDispatchMethod;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeDeliveryStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeReviewStatus;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeType;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalEventRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalNoticeRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Records and reviews statutory/formal notices (notably section 129). Evidence in, human judgement out: the
 * service tracks dispatch and delivery facts and enforces four-eyes review, but never declares a notice valid.
 * <p>
 * Linking a LETTER Communication (communicationId) pre-fills address, method, tracking, dispatch date and the
 * delivery outcome from what was already recorded there, so staff don't type it twice; syncFromCommunication()
 * refreshes it later. ANY change to delivery facts resets an earlier review — a reviewer's acceptance applies to
 * the evidence they saw.
 * <p>
 * Advisory dates (never gates): section 130 conditions, as summarised in the product research, include at least 10
 * business days since delivery of the notice and at least 20 business days in default, alongside other
 * requirements. advisory() counts weekdays minus configured public holidays and is labelled advisory because the
 * correct counting rules and the other conditions are for counsel to confirm.
 */
@Service
@RequiredArgsConstructor
public class LegalNoticeService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    public record Advisory(LocalDate tenBusinessDaysAfterDelivery, LocalDate twentyBusinessDaysAfterDefault,
                           LocalDate earliestIfBothConditionsMet, boolean publicHolidaysConfigured, String caveat) {}

    private final LegalNoticeRepository repository;
    private final LegalEventRepository eventRepository;
    private final CommunicationRepository communicationRepository;
    private final DebtCollectionCaseService caseService;
    private final LegalProperties properties;

    @Transactional
    public LegalNotice record(TenantId tenantId, UUID caseId, NoticeType type, String noticeVersion, String addressUsed,
                              String addressSource, LetterDispatchMethod method, LocalDate dispatchedDate,
                              String trackingReference, String postOfficeBranch, String collectionNoticeReference,
                              LocalDate defaultDate, UUID communicationId, UUID evidenceId, String notes,
                              UUID actorId, String actorName) {
        caseService.get(tenantId, caseId);
        Communication comm = null;
        if (communicationId != null) {
            comm = communicationRepository.findByTenantIdAndId(tenantId, communicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Communication", communicationId.toString()));
            if (!comm.getCaseId().equals(caseId) || comm.getChannel() != CommunicationChannel.LETTER) {
                throw new IllegalArgumentException("The linked communication must be a LETTER on the same case");
            }
        }
        String address = addressUsed != null && !addressUsed.isBlank() ? addressUsed : (comm != null ? comm.getRecipient() : null);
        LetterDispatchMethod m = method != null ? method : (comm != null ? comm.getDispatchMethod() : null);
        LocalDate dispatched = dispatchedDate != null ? dispatchedDate
                : (comm != null && comm.getSentAt() != null ? LocalDate.ofInstant(comm.getSentAt(), ZONE) : null);
        String tracking = trackingReference != null ? trackingReference : (comm != null ? comm.getTrackingReference() : null);
        UUID evidence = evidenceId != null ? evidenceId : (comm != null ? comm.getEvidenceId() : null);

        LegalNotice n = LegalNotice.record(tenantId, caseId, type, noticeVersion, address, addressSource, m, dispatched,
                tracking, postOfficeBranch, collectionNoticeReference, defaultDate, communicationId, evidence, notes,
                actorId, actorName);
        if (comm != null) applyCommunication(n, comm);
        n = repository.save(n);
        audit(n, "RECORDED", type + " " + noticeVersion + " to " + address, actorId, actorName);
        return n;
    }

    @Transactional
    public LegalNotice recordDelivery(TenantId tenantId, UUID id, NoticeDeliveryStatus status, LocalDate deliveredDate,
                                      String detail, String postOfficeBranch, String collectionNoticeReference,
                                      UUID actorId, String actorName) {
        LegalNotice n = find(tenantId, id);
        n.recordDelivery(status, deliveredDate, detail, postOfficeBranch, collectionNoticeReference);
        audit(n, "DELIVERY_RECORDED", status + (deliveredDate != null ? " on " + deliveredDate : "")
                + (detail != null && !detail.isBlank() ? " — " + detail : "") + " (review reset)", actorId, actorName);
        return repository.save(n);
    }

    @Transactional
    public LegalNotice syncFromCommunication(TenantId tenantId, UUID id, UUID actorId, String actorName) {
        LegalNotice n = find(tenantId, id);
        if (n.getCommunicationId() == null) throw new IllegalStateException("This notice has no linked communication");
        Communication comm = communicationRepository.findByTenantIdAndId(tenantId, n.getCommunicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Communication", n.getCommunicationId().toString()));
        applyCommunication(n, comm);
        audit(n, "SYNCED_FROM_COMMUNICATION", "delivery now " + n.getDeliveryStatus() + " (review reset)", actorId, actorName);
        return repository.save(n);
    }

    @Transactional
    public LegalNotice review(TenantId tenantId, UUID id, NoticeReviewStatus outcome, String note, UUID actorId, String actorName) {
        LegalNotice n = find(tenantId, id);
        n.review(outcome, note, actorId, actorName);
        audit(n, "REVIEWED", outcome + ": " + note, actorId, actorName);
        return repository.save(n);
    }

    @Transactional(readOnly = true)
    public LegalNotice get(TenantId tenantId, UUID id) {
        return find(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<LegalNotice> listForCase(TenantId tenantId, UUID caseId) {
        caseService.get(tenantId, caseId);
        return repository.findByCaseId(tenantId, caseId);
    }

    @Transactional(readOnly = true)
    public List<LegalEvent> history(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return eventRepository.findBySubjectId(tenantId, id);
    }

    /** Only for SECTION_129 notices; null otherwise. */
    public Advisory advisory(LegalNotice n) {
        if (n.getNoticeType() != NoticeType.SECTION_129) return null;
        Set<LocalDate> holidays = new HashSet<>(properties.getPublicHolidays());
        LocalDate afterDelivery = n.getDeliveredDate() == null ? null : BusinessDays.addBusinessDays(n.getDeliveredDate(), 10, holidays);
        LocalDate afterDefault = n.getDefaultDate() == null ? null : BusinessDays.addBusinessDays(n.getDefaultDate(), 20, holidays);
        LocalDate earliest = afterDelivery != null && afterDefault != null
                ? (afterDelivery.isAfter(afterDefault) ? afterDelivery : afterDefault) : null;
        return new Advisory(afterDelivery, afterDefault, earliest, !holidays.isEmpty(),
                "Advisory only: weekdays" + (holidays.isEmpty() ? " (NO public holidays configured — dates may be too early)" : " minus configured public holidays")
                        + ". Other statutory conditions apply and counsel must confirm counting rules and sufficiency of delivery.");
    }

    private void applyCommunication(LegalNotice n, Communication comm) {
        NoticeDeliveryStatus status = switch (comm.getStatus()) {
            case DELIVERED -> NoticeDeliveryStatus.DELIVERED;
            case RETURNED -> NoticeDeliveryStatus.RETURNED_UNCOLLECTED;
            case FAILED, BOUNCED -> NoticeDeliveryStatus.FAILED;
            default -> NoticeDeliveryStatus.PENDING;
        };
        n.syncFrom(comm.getSentAt() != null ? LocalDate.ofInstant(comm.getSentAt(), ZONE) : null, comm.getDispatchMethod(),
                comm.getTrackingReference(), status,
                comm.getStatus() == CommunicationStatus.DELIVERED && comm.getDeliveredAt() != null
                        ? LocalDate.ofInstant(comm.getDeliveredAt(), ZONE) : null);
    }

    private LegalNotice find(TenantId tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LegalNotice", id.toString()));
    }

    private void audit(LegalNotice n, String type, String detail, UUID actorId, String actorName) {
        eventRepository.save(LegalEvent.of(n.getTenantId(), "NOTICE", n.getId(), n.getCaseId(), type, detail, actorId, actorName));
    }
}
