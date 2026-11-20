package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalcompliance.application.internal.WrittenResolutionRules.Counts;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** A decision taken without a meeting. OPEN until it is closed (everyone answered, or the deadline passed) or cancelled; after that nothing about it changes. */
@Entity
@Table(name = "legalcompliance_written_resolutions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrittenResolution extends AggregateRoot<WrittenResolution> {

    public static final int MIN_TEXT = 15, MAX_DAYS = 60;

    @Column(name = "committee_id", nullable = false) private UUID committeeId;
    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(name = "resolution_text", nullable = false, columnDefinition = "TEXT") private String resolutionText;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ApprovalThreshold threshold;
    @Column(nullable = false) private LocalDate deadline;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 11) private WrittenStatus status = WrittenStatus.OPEN;
    @Column(name = "circulated_on", nullable = false) private LocalDate circulatedOn;
    @Column(name = "member_count", nullable = false) private int memberCount;
    @Column(name = "closed_on") private LocalDate closedOn;
    @Column(name = "closed_automatically", nullable = false) private boolean closedAutomatically;
    @Column(name = "votes_for") private Integer votesFor;
    @Column(name = "votes_against") private Integer votesAgainst;
    private Integer abstained;
    @Column(name = "no_response") private Integer noResponse;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static WrittenResolution circulate(TenantId t, UUID committeeId, String reference, String title, String text, ApprovalThreshold threshold, LocalDate deadline,
                                              int memberCount, int quorumMin, LocalDate today, String byName) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (text == null || text.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Write out the resolution (at least " + MIN_TEXT + " characters)");
        if (threshold == null) throw new IllegalArgumentException("Choose how it is approved");
        if (deadline == null || !deadline.isAfter(today)) throw new IllegalArgumentException("The deadline must be after today");
        if (deadline.isAfter(today.plusDays(MAX_DAYS))) throw new IllegalArgumentException("The deadline cannot be more than " + MAX_DAYS + " days away");
        if (memberCount < Math.max(1, quorumMin)) throw new IllegalArgumentException("Only " + memberCount + " voting member(s) are on the committee, but its quorum is " + quorumMin);
        WrittenResolution r = new WrittenResolution();
        r.initTenantId(t);
        r.committeeId = committeeId;
        r.reference = reference;
        r.title = title.trim();
        r.resolutionText = text.trim();
        r.threshold = threshold;
        r.deadline = deadline;
        r.circulatedOn = today;
        r.memberCount = memberCount;
        r.createdByName = byName;
        return r;
    }

    public boolean isOpen() { return status == WrittenStatus.OPEN; }

    /** Members can answer through the deadline day itself. */
    public boolean isPastDeadline(LocalDate today) { return today.isAfter(deadline); }

    public void close(WrittenStatus outcome, Counts counts, LocalDate today, boolean automatically) {
        if (!isOpen()) throw new IllegalStateException("This written resolution is already closed");
        if (outcome != WrittenStatus.CARRIED && outcome != WrittenStatus.NOT_CARRIED) throw new IllegalArgumentException("A closed resolution is carried or not carried");
        if (counts.total() != memberCount) throw new IllegalStateException("The counts do not add up to the " + memberCount + " members it was sent to");
        this.status = outcome;
        this.votesFor = counts.votesFor();
        this.votesAgainst = counts.against();
        this.abstained = counts.abstained();
        this.noResponse = counts.noResponse();
        this.closedOn = today;
        this.closedAutomatically = automatically;
    }

    public void cancel(String reason, LocalDate today) {
        if (!isOpen()) throw new IllegalStateException("Only an open written resolution can be cancelled");
        if (reason == null || reason.trim().length() < 15) throw new IllegalArgumentException("Give a reason for cancelling (at least 15 characters)");
        this.status = WrittenStatus.CANCELLED;
        this.cancelReason = reason.trim();
        this.closedOn = today;
    }
}
