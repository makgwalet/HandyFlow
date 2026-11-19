package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A licence, permit, certificate, registration or accreditation. ACTIVE ⇄ RENEWAL_IN_PROGRESS, ACTIVE ⇄ SUSPENDED, and the final states REVOKED / SURRENDERED.
 * Expiry is derived (see LicenceRules), not stored as a status. Renewal: start → submit (date, reference, at least one proof file, snapshotted) → recorded as
 * renewed (new expiry later than both today and the old expiry); every renewal is appended to renewal_history so earlier terms are never lost.
 */
@Entity
@Table(name = "legalcompliance_licences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Licence extends AggregateRoot<Licence> {

    public static final int MIN_TEXT = 15;

    @Column(nullable = false, length = 20) private String reference;
    @Enumerated(EnumType.STRING) @Column(name = "licence_type", nullable = false, length = 15) private LicenceType licenceType;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "issuing_authority", nullable = false, length = 255) private String issuingAuthority;
    @Column(name = "licence_number", length = 100) private String licenceNumber;
    @Column(columnDefinition = "TEXT") private String scope;
    @Column(name = "issue_date") private LocalDate issueDate;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LicenceStatus status = LicenceStatus.ACTIVE;
    @Column(name = "owner_name", nullable = false, length = 255) private String ownerName;
    @Column(name = "renewal_lead_days", nullable = false) private int renewalLeadDays = 90;
    @Column(name = "obligation_id") private UUID obligationId;
    @Column(name = "renewal_started_on") private LocalDate renewalStartedOn;
    @Column(name = "renewal_submitted_on") private LocalDate renewalSubmittedOn;
    @Column(name = "renewal_reference", length = 100) private String renewalReference;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "renewal_proof", columnDefinition = "jsonb") private List<Map<String, Object>> renewalProof;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "renewal_history", nullable = false, columnDefinition = "jsonb") private List<Map<String, Object>> renewalHistory = new ArrayList<>();
    @Column(name = "status_reason", columnDefinition = "TEXT") private String statusReason;
    @Column(columnDefinition = "TEXT") private String notes;

    public static Licence create(TenantId tenantId, String reference, LicenceType type, String title, String authority, String number, String scope, LocalDate issued,
                                 LocalDate expires, String owner, int leadDays, UUID obligationId, String notes) {
        Licence l = new Licence();
        l.initTenantId(tenantId);
        l.reference = reference;
        l.apply(type, title, authority, number, scope, issued, expires, owner, leadDays, obligationId, notes);
        return l;
    }

    public void update(LicenceType type, String title, String authority, String number, String scope, LocalDate issued, LocalDate expires, String owner, int leadDays,
                       UUID obligationId, String notes) {
        if (isFinal()) throw new IllegalStateException("A " + status.name().toLowerCase() + " licence cannot be edited");
        if (status == LicenceStatus.RENEWAL_IN_PROGRESS && !java.util.Objects.equals(expires, expiryDate))
            throw new IllegalStateException("Change the expiry date by recording the renewal, not by editing it");
        apply(type, title, authority, number, scope, issued, expires, owner, leadDays, obligationId, notes);
    }

    private void apply(LicenceType type, String title, String authority, String number, String scope, LocalDate issued, LocalDate expires, String owner, int leadDays,
                       UUID obligationId, String notes) {
        if (type == null) throw new IllegalArgumentException("A type is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (authority == null || authority.isBlank()) throw new IllegalArgumentException("The issuing authority is required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("An owner is required");
        if (leadDays < 1 || leadDays > 365) throw new IllegalArgumentException("The renewal lead time must be between 1 and 365 days");
        if (issued != null && expires != null && expires.isBefore(issued)) throw new IllegalArgumentException("A licence cannot expire before it was issued");
        this.licenceType = type;
        this.title = title.trim();
        this.issuingAuthority = authority.trim();
        this.licenceNumber = blankToNull(number);
        this.scope = blankToNull(scope);
        this.issueDate = issued;
        this.expiryDate = expires;
        this.ownerName = owner.trim();
        this.renewalLeadDays = leadDays;
        this.obligationId = obligationId;
        this.notes = blankToNull(notes);
    }

    public boolean isFinal() { return status == LicenceStatus.REVOKED || status == LicenceStatus.SURRENDERED; }

    public boolean isLive() { return !isFinal(); }

    public void startRenewal(LocalDate today) {
        if (status != LicenceStatus.ACTIVE) throw new IllegalStateException("Only an active licence can start renewal — current: " + status);
        if (expiryDate == null) throw new IllegalStateException("This licence has no expiry date, so there is nothing to renew");
        this.status = LicenceStatus.RENEWAL_IN_PROGRESS;
        this.renewalStartedOn = today;
        clearSubmission();
    }

    public void abandonRenewal(String reason) {
        if (status != LicenceStatus.RENEWAL_IN_PROGRESS) throw new IllegalStateException("No renewal is in progress");
        requireText(reason, "Say why the renewal was abandoned");
        this.notes = append(notes, "Renewal abandoned: " + reason.trim());
        this.status = LicenceStatus.ACTIVE;
        this.renewalStartedOn = null;
        clearSubmission();
    }

    public void submitRenewal(LocalDate on, String reference, List<Map<String, Object>> proof, LocalDate today) {
        if (status != LicenceStatus.RENEWAL_IN_PROGRESS) throw new IllegalStateException("Start the renewal first — current: " + status);
        if (on == null) throw new IllegalArgumentException("The submission date is required");
        if (on.isAfter(today)) throw new IllegalArgumentException("A submission date cannot be in the future");
        if (renewalStartedOn != null && on.isBefore(renewalStartedOn)) throw new IllegalArgumentException("The renewal cannot be submitted before it was started");
        if (proof == null || proof.isEmpty()) throw new IllegalArgumentException("Attach proof that the renewal was submitted first");
        this.renewalSubmittedOn = on;
        this.renewalReference = blankToNull(reference);
        this.renewalProof = proof;
    }

    public void recordRenewed(LocalDate newIssued, LocalDate newExpiry, String newNumber, String by, LocalDate today) {
        if (status != LicenceStatus.RENEWAL_IN_PROGRESS) throw new IllegalStateException("Start the renewal first — current: " + status);
        if (renewalSubmittedOn == null) throw new IllegalStateException("Record the renewal submission first");
        if (newExpiry == null) throw new IllegalArgumentException("The new expiry date is required");
        if (!newExpiry.isAfter(today)) throw new IllegalArgumentException("The new expiry date must be in the future");
        if (expiryDate != null && !newExpiry.isAfter(expiryDate)) throw new IllegalArgumentException("The new expiry date must be later than the current one (" + expiryDate + ")");
        if (newIssued != null && newExpiry.isBefore(newIssued)) throw new IllegalArgumentException("A licence cannot expire before it was issued");
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("renewedOn", today.toString());
        h.put("previousExpiry", expiryDate == null ? null : expiryDate.toString());
        h.put("newExpiry", newExpiry.toString());
        h.put("submittedOn", renewalSubmittedOn.toString());
        h.put("reference", renewalReference);
        h.put("proofFiles", renewalProof == null ? 0 : renewalProof.size());
        h.put("recordedBy", by);
        List<Map<String, Object>> history = new ArrayList<>(renewalHistory);
        history.add(h);
        this.renewalHistory = history;
        this.issueDate = newIssued != null ? newIssued : today;
        this.expiryDate = newExpiry;
        if (newNumber != null && !newNumber.isBlank()) this.licenceNumber = newNumber.trim();
        this.status = LicenceStatus.ACTIVE;
        this.renewalStartedOn = null;
        clearSubmission();
    }

    public void suspend(String reason) {
        if (status != LicenceStatus.ACTIVE && status != LicenceStatus.RENEWAL_IN_PROGRESS) throw new IllegalStateException("Only an active licence can be suspended — current: " + status);
        requireText(reason, "Say why");
        this.statusReason = reason.trim();
        this.status = LicenceStatus.SUSPENDED;
        this.renewalStartedOn = null;
        clearSubmission();
    }

    public void reinstate(String note) {
        if (status != LicenceStatus.SUSPENDED) throw new IllegalStateException("Only a suspended licence can be reinstated — current: " + status);
        requireText(note, "Say on what basis it is reinstated");
        this.notes = append(notes, "Reinstated: " + note.trim());
        this.statusReason = null;
        this.status = LicenceStatus.ACTIVE;
    }

    public void revoke(String reason) { end(LicenceStatus.REVOKED, reason); }

    public void surrender(String reason) { end(LicenceStatus.SURRENDERED, reason); }

    private void end(LicenceStatus to, String reason) {
        if (isFinal()) throw new IllegalStateException("This licence is already " + status.name().toLowerCase());
        requireText(reason, "Say why");
        this.statusReason = reason.trim();
        this.status = to;
        this.renewalStartedOn = null;
        clearSubmission();
    }

    private void clearSubmission() { this.renewalSubmittedOn = null; this.renewalReference = null; this.renewalProof = null; }

    private static void requireText(String s, String msg) {
        if (s == null || s.trim().length() < MIN_TEXT) throw new IllegalArgumentException(msg + " (at least " + MIN_TEXT + " characters)");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String append(String existing, String line) { return existing == null || existing.isBlank() ? line : existing + "\n" + line; }
}
