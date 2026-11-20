package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** One version of a committee's charter rules. Never edited after it is saved: a change is a new version. */
@Entity
@Table(name = "legalcompliance_committee_charter_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitteeCharterRules extends AggregateRoot<CommitteeCharterRules> {

    @Column(name = "committee_id", nullable = false, updatable = false) private UUID committeeId;
    @Column(name = "version_no", nullable = false, updatable = false) private int versionNo;
    @Column(name = "charter_reference", length = 120, updatable = false) private String charterReference;
    @Column(name = "adopted_on", nullable = false, updatable = false) private LocalDate adoptedOn;
    @Column(name = "effective_from", nullable = false, updatable = false) private LocalDate effectiveFrom;
    @Column(name = "casting_vote", nullable = false, updatable = false) private boolean castingVote;
    @Enumerated(EnumType.STRING) @Column(name = "written_mode", nullable = false, length = 16, updatable = false) private WrittenMode writtenMode;
    @Column(name = "quorum_after_recusal", nullable = false, updatable = false) private boolean quorumAfterRecusal;
    @Column(name = "special_percent", nullable = false, updatable = false) private int specialPercent;
    @Column(name = "change_reason", nullable = false, columnDefinition = "TEXT", updatable = false) private String changeReason;
    @Column(name = "recorded_by_name", length = 255, updatable = false) private String recordedByName;

    public static CommitteeCharterRules record(TenantId t, UUID committeeId, int versionNo, String charterReference, LocalDate adoptedOn, LocalDate effectiveFrom, boolean castingVote,
                                               WrittenMode writtenMode, boolean quorumAfterRecusal, int specialPercent, String reason, String byName) {
        if (adoptedOn == null) throw new IllegalArgumentException("Say when the charter was adopted");
        if (effectiveFrom == null) throw new IllegalArgumentException("Say when the rules take effect");
        if (effectiveFrom.isBefore(adoptedOn)) throw new IllegalArgumentException("The rules cannot take effect before the charter was adopted");
        if (writtenMode == null) throw new IllegalArgumentException("Say whether written resolutions are allowed");
        if (specialPercent < 51 || specialPercent > 100) throw new IllegalArgumentException("A special resolution percentage must be between 51 and 100");
        if (reason == null || reason.trim().length() < 10) throw new IllegalArgumentException("Give a reason of at least 10 characters");
        if (charterReference != null && charterReference.length() > 120) throw new IllegalArgumentException("The charter reference is too long (120 characters at most)");
        CommitteeCharterRules r = new CommitteeCharterRules();
        r.initTenantId(t);
        r.committeeId = committeeId;
        r.versionNo = versionNo;
        r.charterReference = charterReference == null || charterReference.isBlank() ? null : charterReference.trim();
        r.adoptedOn = adoptedOn;
        r.effectiveFrom = effectiveFrom;
        r.castingVote = castingVote;
        r.writtenMode = writtenMode;
        r.quorumAfterRecusal = quorumAfterRecusal;
        r.specialPercent = specialPercent;
        r.changeReason = reason.trim();
        r.recordedByName = byName;
        return r;
    }
}
