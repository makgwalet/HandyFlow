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
import java.util.UUID;

/**
 * One message to or from a debtor on a case — the timeline record for SMS, email, letters (and WhatsApp,
 * should it ever be enabled). Phone calls and in-person contact remain in CollectionContactLog; the timeline
 * view merges both.
 * <p>
 * Content is stored as sent (rendered body + template code/version), so what the debtor received is provable
 * even after a template changes. Status only moves forward (see applyProviderStatus) so late or duplicate
 * provider callbacks cannot regress a message. A message refused by policy is kept with status BLOCKED and the
 * reason — blocked attempts are part of the compliance trail. Rows are never edited except for delivery status.
 * <p>
 * processingBasis records the lawful basis / consent reference the sender relied on. It is free text on
 * purpose: mapping each purpose+channel to a POPIA basis is a legal decision still to be made by counsel.
 */
@Entity
@Table(name = "debtcollection_communications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Communication extends AggregateRoot<Communication> {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CommunicationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommunicationDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommunicationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private CommunicationStatus status;

    /** Outbound: address the message went to. Inbound: the sender. For letters: the postal address. */
    @Column(nullable = false, length = 500)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "template_code", length = 60)
    private String templateCode;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "status_detail", length = 500)
    private String statusDetail;

    @Column(name = "processing_basis", length = 100)
    private String processingBasis;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_method", length = 20)
    private LetterDispatchMethod dispatchMethod;

    @Column(name = "tracking_reference", length = 100)
    private String trackingReference;

    /** Optional link to an Evidence record (proof of dispatch/delivery, copy of the letter) — id only. */
    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_by_user_name", length = 255)
    private String createdByUserName;

    public static Communication outbound(TenantId tenantId, UUID caseId, CommunicationChannel channel,
                                         CommunicationPurpose purpose, String recipient, String subject, String body,
                                         String templateCode, Integer templateVersion, String processingBasis,
                                         String idempotencyKey, UUID actorId, String actorName) {
        Communication c = base(tenantId, caseId, channel, CommunicationDirection.OUTBOUND, purpose, recipient, actorId, actorName);
        c.status = CommunicationStatus.QUEUED;
        c.subject = subject;
        c.body = body;
        c.templateCode = templateCode;
        c.templateVersion = templateVersion;
        c.processingBasis = processingBasis;
        c.idempotencyKey = idempotencyKey;
        return c;
    }

    public static Communication inbound(TenantId tenantId, UUID caseId, CommunicationChannel channel, String from,
                                        String body, Instant receivedAt, String providerMessageId,
                                        UUID actorId, String actorName) {
        Communication c = base(tenantId, caseId, channel, CommunicationDirection.INBOUND, CommunicationPurpose.OTHER,
                from, actorId, actorName);
        c.status = CommunicationStatus.RECEIVED;
        c.body = body;
        c.receivedAt = receivedAt != null ? receivedAt : Instant.now();
        c.providerMessageId = providerMessageId;
        return c;
    }

    /** A letter physically dispatched by staff — a record of fact, so it starts as SENT. */
    public static Communication letterSent(TenantId tenantId, UUID caseId, CommunicationPurpose purpose,
                                           String postalAddress, String subject, String summary,
                                           LetterDispatchMethod method, String trackingReference, UUID evidenceId,
                                           Instant sentAt, String templateCode, Integer templateVersion,
                                           UUID actorId, String actorName) {
        Communication c = base(tenantId, caseId, CommunicationChannel.LETTER, CommunicationDirection.OUTBOUND, purpose,
                postalAddress, actorId, actorName);
        c.status = CommunicationStatus.SENT;
        c.subject = subject;
        c.body = summary;
        c.dispatchMethod = method;
        c.trackingReference = trackingReference;
        c.evidenceId = evidenceId;
        c.sentAt = sentAt != null ? sentAt : Instant.now();
        c.templateCode = templateCode;
        c.templateVersion = templateVersion;
        return c;
    }

    private static Communication base(TenantId tenantId, UUID caseId, CommunicationChannel channel,
                                      CommunicationDirection direction, CommunicationPurpose purpose, String recipient,
                                      UUID actorId, String actorName) {
        if (caseId == null || channel == null || purpose == null) {
            throw new IllegalArgumentException("caseId, channel and purpose are required");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient/sender is required");
        }
        Communication c = new Communication();
        c.initTenantId(tenantId);
        c.caseId = caseId;
        c.channel = channel;
        c.direction = direction;
        c.purpose = purpose;
        c.recipient = recipient;
        c.createdByUserId = actorId;
        c.createdByUserName = actorName;
        return c;
    }

    public void markBlocked(String reason) {
        requireStatus(CommunicationStatus.QUEUED);
        this.status = CommunicationStatus.BLOCKED;
        this.statusDetail = truncate(reason);
    }

    public void markSent(String providerMessageId, Instant at) {
        requireStatus(CommunicationStatus.QUEUED);
        this.status = CommunicationStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = at != null ? at : Instant.now();
    }

    public void markFailed(String detail) {
        requireStatus(CommunicationStatus.QUEUED);
        this.status = CommunicationStatus.FAILED;
        this.statusDetail = truncate(detail);
    }

    /**
     * Applies a delivery update from a provider callback or a staff letter update. Forward-only:
     * QUEUED &lt; SENT &lt; DELIVERED; FAILED/BOUNCED only before delivery; RETURNED after SENT or DELIVERED
     * (a delivered-then-returned letter is possible). Anything else is ignored. Returns true if state changed.
     */
    public boolean applyProviderStatus(CommunicationStatus next, String detail, Instant at) {
        if (direction != CommunicationDirection.OUTBOUND) return false;
        boolean ok = switch (next) {
            case SENT -> status == CommunicationStatus.QUEUED;
            case DELIVERED -> status == CommunicationStatus.QUEUED || status == CommunicationStatus.SENT;
            case FAILED, BOUNCED -> status == CommunicationStatus.QUEUED || status == CommunicationStatus.SENT;
            case RETURNED -> status == CommunicationStatus.SENT || status == CommunicationStatus.DELIVERED;
            default -> false;
        };
        if (!ok) return false;
        Instant when = at != null ? at : Instant.now();
        this.status = next;
        if (next == CommunicationStatus.SENT && sentAt == null) sentAt = when;
        if (next == CommunicationStatus.DELIVERED) {
            if (sentAt == null) sentAt = when;
            deliveredAt = when;
        }
        if (detail != null && !detail.isBlank()) this.statusDetail = truncate(detail);
        return true;
    }

    private void requireStatus(CommunicationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Communication is " + status + ", expected " + expected);
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 500 ? s.substring(0, 500) : s;
    }
}
