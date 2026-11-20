package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.KycRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One cycle of due diligence on a client: DRAFT (being prepared) → SUBMITTED (complete, waiting for a second person) → VERIFIED
 * (approved; valid until the review date). A submitted one can be returned. A verified one never changes: a review opens a new profile.
 * Never deleted; the database enforces that, and the rules about who may approve.
 */
@Entity
@Table(name = "lp_kyc_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpKycProfile {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "subject_kind", nullable = false, length = 12)
    private String subjectKind;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "risk_geography", length = 6) private String riskGeography;
    @Column(name = "risk_service", length = 6) private String riskService;
    @Column(name = "risk_client", length = 6) private String riskClient;
    @Column(name = "risk_channel", length = 6) private String riskChannel;
    @Column(name = "risk_rating", length = 6) private String riskRating;

    @Column(name = "pep_status", length = 5) private String pepStatus;
    @Column(name = "pep_checked_on") private LocalDate pepCheckedOn;
    @Column(name = "pep_note") private String pepNote;

    @Column(name = "sanctions_checked_on") private LocalDate sanctionsCheckedOn;
    @Column(name = "sanctions_result", length = 5) private String sanctionsResult;

    @Column(name = "business_nature") private String businessNature;
    @Column(name = "source_of_funds") private String sourceOfFunds;
    @Column(name = "source_of_wealth") private String sourceOfWealth;

    @Column(name = "started_by", nullable = false) private UUID startedBy;
    @Column(name = "started_by_name") private String startedByName;
    @Column(name = "started_at", nullable = false) private Instant startedAt;

    @Column(name = "submitted_by") private UUID submittedBy;
    @Column(name = "submitted_by_name") private String submittedByName;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "return_reason") private String returnReason;

    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_by_name") private String approvedByName;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approval_note") private String approvalNote;
    @Column(name = "self_approved", nullable = false) private boolean selfApproved;
    @Column(name = "senior_approved", nullable = false) private boolean seniorApproved;
    @Column(name = "valid_until") private LocalDate validUntil;

    @Version
    private Long version;

    public static LpKycProfile start(TenantId tenantId, UUID clientId, String subjectKind, UUID by, String byName, Instant now) {
        if (!KycRules.SUBJECT_KINDS.contains(subjectKind)) throw new IllegalArgumentException("Choose what kind of client this is");
        LpKycProfile p = new LpKycProfile();
        p.tenantId = tenantId;
        p.clientId = clientId;
        p.subjectKind = subjectKind;
        p.status = "DRAFT";
        p.startedBy = by;
        p.startedByName = byName;
        p.startedAt = now;
        return p;
    }

    /** A new cycle for a review, starting from what the last one said (the checks are done again, so nothing is marked verified). */
    public LpKycProfile reviewCopy(UUID by, String byName, Instant now) {
        LpKycProfile p = start(tenantId, clientId, subjectKind, by, byName, now);
        p.riskGeography = riskGeography; p.riskService = riskService; p.riskClient = riskClient; p.riskChannel = riskChannel; p.riskRating = riskRating;
        p.pepStatus = pepStatus; p.pepNote = pepNote;
        p.businessNature = businessNature; p.sourceOfFunds = sourceOfFunds; p.sourceOfWealth = sourceOfWealth;
        return p;
    }

    public boolean draft() { return "DRAFT".equals(status); }

    public KycRules.Assessment assessment() {
        return new KycRules.Assessment(subjectKind, riskGeography, riskService, riskClient, riskChannel, pepStatus, pepCheckedOn,
                sanctionsCheckedOn, sanctionsResult, businessNature, sourceOfFunds, sourceOfWealth);
    }

    public void assess(String geography, String service, String client, String channel, String pep, LocalDate pepCheckedOn, String pepNote,
                       LocalDate sanctionsCheckedOn, String sanctionsResult, String businessNature, String sourceOfFunds, String sourceOfWealth) {
        requireDraft();
        level(geography, "country risk"); level(service, "service risk"); level(client, "client risk"); level(channel, "channel risk");
        if (pep != null && !KycRules.PEP.contains(pep)) throw new IllegalArgumentException("Politically exposed status must be NONE, DPEP, PIP or FPEP");
        if (sanctionsResult != null && !Set2.SANCTIONS.contains(sanctionsResult)) throw new IllegalArgumentException("Sanctions result must be CLEAR or MATCH");
        this.riskGeography = geography; this.riskService = service; this.riskClient = client; this.riskChannel = channel;
        this.pepStatus = pep; this.pepCheckedOn = pepCheckedOn; this.pepNote = blank(pepNote);
        this.sanctionsCheckedOn = sanctionsCheckedOn; this.sanctionsResult = sanctionsResult;
        this.businessNature = blank(businessNature); this.sourceOfFunds = blank(sourceOfFunds); this.sourceOfWealth = blank(sourceOfWealth);
        this.riskRating = KycRules.riskOf(geography, service, client, channel, pep);
    }

    public void submit(UUID by, String byName, List<String> missing, Instant now) {
        if (!draft()) throw new IllegalStateException("Only a draft can be submitted");
        if (!missing.isEmpty()) throw new IllegalStateException("Not complete yet: " + missing.get(0));
        this.status = "SUBMITTED";
        this.submittedBy = by;
        this.submittedByName = byName;
        this.submittedAt = now;
        this.returnReason = null;
    }

    public void returnToDraft(String reason) {
        if (!"SUBMITTED".equals(status)) throw new IllegalStateException("Only a submitted profile can be returned");
        if (reason == null || reason.trim().length() < KycRules.MIN_TEXT) throw new IllegalArgumentException("Say what needs fixing (at least " + KycRules.MIN_TEXT + " characters)");
        this.status = "DRAFT";
        this.submittedBy = null; this.submittedByName = null; this.submittedAt = null;
        this.returnReason = reason.trim();
    }

    /**
     * Approves a submitted profile. Another person must approve it; the firm may allow the same person for an ordinary client, with a written reason,
     * but never for a high-risk or politically exposed one, which also needs a senior approver.
     */
    public void approve(UUID by, String byName, String note, boolean senior, boolean selfApprovalAllowed, List<String> missing, LocalDate today, Instant now) {
        if (!"SUBMITTED".equals(status)) throw new IllegalStateException("Only a submitted profile can be approved");
        if (!missing.isEmpty()) throw new IllegalStateException("No longer complete: " + missing.get(0));
        boolean needsSenior = KycRules.needsSenior(riskRating, pepStatus);
        if (needsSenior && !senior) throw new IllegalStateException("A high-risk or politically exposed client must be approved by senior management");
        boolean same = by.equals(submittedBy);
        if (same) {
            if (needsSenior) throw new IllegalStateException("A high-risk or politically exposed client cannot be approved by the person who submitted it");
            if (!selfApprovalAllowed) throw new IllegalStateException("The person who submitted this cannot approve it. Another person must");
            if (note == null || note.trim().length() < KycRules.MIN_TEXT) throw new IllegalArgumentException("Approving your own work needs a written reason (at least " + KycRules.MIN_TEXT + " characters)");
        }
        this.status = "VERIFIED";
        this.approvedBy = by;
        this.approvedByName = byName;
        this.approvedAt = now;
        this.approvalNote = blank(note);
        this.selfApproved = same;
        this.seniorApproved = needsSenior;
        this.validUntil = today.plusMonths(KycRules.reviewMonths(riskRating));
    }

    /** Usable for opening a matter or taking trust money: verified and not past its review date. */
    public boolean currentOn(LocalDate day) { return "VERIFIED".equals(status) && validUntil != null && !validUntil.isBefore(day); }

    private void requireDraft() { if (!draft()) throw new IllegalStateException("Only a draft can be changed"); }

    private static void level(String v, String what) {
        if (v != null && !KycRules.LEVELS.contains(v)) throw new IllegalArgumentException("The " + what + " must be LOW, MEDIUM or HIGH");
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static final class Set2 { static final java.util.Set<String> SANCTIONS = java.util.Set.of("CLEAR", "MATCH"); }
}
