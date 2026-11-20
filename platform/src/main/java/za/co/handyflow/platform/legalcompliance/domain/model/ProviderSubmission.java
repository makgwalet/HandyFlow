package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Something a provider sent to a client: an observation, a recommendation or a request for information. The row belongs to the CLIENT tenant; the provider is identified by
 * provider_tenant_id and provider_client_id. SUBMITTED → ACCEPTED (the client will act) → COMPLETED, or DECLINED / COMPLETED straight from SUBMITTED; the provider can WITHDRAW
 * while it is still SUBMITTED. Declining and completing need a reason of at least 10 characters. A submission is never deleted and never edited once sent.
 */
@Entity
@Table(name = "legalcompliance_provider_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderSubmission extends AggregateRoot<ProviderSubmission> {

    public static final int MIN_TITLE = 5, MAX_TITLE = 200, MIN_BODY = 20, MAX_BODY = 4000, MIN_REASON = 10, MAX_REASON = 2000, MAX_RESPOND_DAYS = 180;

    @Column(name = "provider_client_id", nullable = false, updatable = false) private UUID providerClientId;
    @Column(name = "provider_tenant_id", nullable = false, updatable = false) private UUID providerTenantId;
    @Column(name = "provider_name", nullable = false, length = 255, updatable = false) private String providerName;
    @Column(nullable = false, length = 20, updatable = false) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, updatable = false) private SubmissionKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12, updatable = false) private SubmissionArea area;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 6, updatable = false) private SubmissionPriority priority;
    @Column(nullable = false, length = 200, updatable = false) private String title;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false) private String body;
    @Column(name = "respond_by", updatable = false) private LocalDate respondBy;
    @Column(name = "submitted_by_user_id", nullable = false, updatable = false) private UUID submittedByUserId;
    @Column(name = "submitted_by_name", length = 255, updatable = false) private String submittedByName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private SubmissionStatus status = SubmissionStatus.SUBMITTED;
    @Column(columnDefinition = "TEXT") private String response;
    @Column(name = "responded_by_name", length = 255) private String respondedByName;
    @Column(name = "responded_at") private Instant respondedAt;

    public static ProviderSubmission send(TenantId clientTenant, UUID providerClientId, UUID providerTenantId, String providerName, String reference, SubmissionKind kind, SubmissionArea area,
                                          SubmissionPriority priority, String title, String body, LocalDate respondBy, LocalDate today, UUID userId, String userName) {
        if (kind == null) throw new IllegalArgumentException("Choose what kind of submission this is");
        if (area == null) throw new IllegalArgumentException("Choose the area it concerns");
        if (title == null || title.trim().length() < MIN_TITLE) throw new IllegalArgumentException("Give it a title of at least " + MIN_TITLE + " characters");
        if (title.trim().length() > MAX_TITLE) throw new IllegalArgumentException("The title is too long (" + MAX_TITLE + " characters at most)");
        if (body == null || body.trim().length() < MIN_BODY) throw new IllegalArgumentException("Describe it in at least " + MIN_BODY + " characters");
        if (body.trim().length() > MAX_BODY) throw new IllegalArgumentException("The description is too long (" + MAX_BODY + " characters at most)");
        if (respondBy != null && !respondBy.isAfter(today)) throw new IllegalArgumentException("The date you want a reply by must be after today");
        if (respondBy != null && respondBy.isAfter(today.plusDays(MAX_RESPOND_DAYS))) throw new IllegalArgumentException("The date you want a reply by cannot be more than " + MAX_RESPOND_DAYS + " days away");
        if (userId == null) throw new IllegalArgumentException("The sender is required");
        ProviderSubmission s = new ProviderSubmission();
        s.initTenantId(clientTenant);
        s.providerClientId = providerClientId;
        s.providerTenantId = providerTenantId;
        s.providerName = providerName;
        s.reference = reference;
        s.kind = kind;
        s.area = area;
        s.priority = priority == null ? SubmissionPriority.NORMAL : priority;
        s.title = title.trim();
        s.body = body.trim();
        s.respondBy = respondBy;
        s.submittedByUserId = userId;
        s.submittedByName = userName;
        return s;
    }

    /** The client will act on it. A note is optional. */
    public void accept(String note, String byName, Instant now) {
        if (status != SubmissionStatus.SUBMITTED) throw new IllegalStateException("Only a submission that has not been answered can be accepted");
        this.response = clean(note);
        answered(SubmissionStatus.ACCEPTED, byName, now);
    }

    public void decline(String reason, String byName, Instant now) {
        if (status != SubmissionStatus.SUBMITTED && status != SubmissionStatus.ACCEPTED) throw new IllegalStateException("This submission has already been closed");
        this.response = requireReason(reason);
        answered(SubmissionStatus.DECLINED, byName, now);
    }

    /** Done: acted on, or the information was provided. */
    public void complete(String note, String byName, Instant now) {
        if (status != SubmissionStatus.SUBMITTED && status != SubmissionStatus.ACCEPTED) throw new IllegalStateException("This submission has already been closed");
        this.response = requireReason(note);
        answered(SubmissionStatus.COMPLETED, byName, now);
    }

    /** The provider takes it back before the client has answered. */
    public void withdraw() {
        if (status != SubmissionStatus.SUBMITTED) throw new IllegalStateException("Only a submission that has not been answered can be withdrawn");
        this.status = SubmissionStatus.WITHDRAWN;
    }

    public boolean isOpen() { return status == SubmissionStatus.SUBMITTED || status == SubmissionStatus.ACCEPTED; }

    /** Derived, never stored: a reply was asked for by a date that has passed and the client has not closed it. */
    public boolean isOverdue(LocalDate today) { return isOpen() && respondBy != null && today.isAfter(respondBy); }

    private void answered(SubmissionStatus to, String byName, Instant now) {
        this.status = to;
        this.respondedByName = byName;
        this.respondedAt = now;
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        if (s.trim().length() > MAX_REASON) throw new IllegalArgumentException("The note is too long (" + MAX_REASON + " characters at most)");
        return s.trim();
    }

    private static String requireReason(String s) {
        if (s == null || s.trim().length() < MIN_REASON) throw new IllegalArgumentException("Write a reply of at least " + MIN_REASON + " characters");
        return clean(s);
    }
}
