package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.ApprovalThreshold;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeCharterRules;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Pure rules for choosing and applying a committee's charter. The current version is the highest version number whose effective date has arrived. When a committee has no
 * recorded charter, NONE applies: the casting vote needs the closer's confirmation, any written-resolution threshold may be used, a special resolution needs 75%, and the
 * quorum-after-recusal check is on. That is exactly how the system behaved before charters could be recorded.
 */
public final class CharterRules {

    private CharterRules() {}

    public static final int DEFAULT_SPECIAL_PERCENT = 75;

    /** recorded=false means no charter is on file. */
    public record Applied(boolean recorded, boolean castingVote, WrittenMode writtenMode, boolean quorumAfterRecusal, int specialPercent, Integer versionNo) {}

    public static final Applied NONE = new Applied(false, false, WrittenMode.EITHER, true, DEFAULT_SPECIAL_PERCENT, null);

    public static Applied current(List<CommitteeCharterRules> versions, LocalDate today) {
        Optional<CommitteeCharterRules> c = versions.stream().filter(v -> !v.getEffectiveFrom().isAfter(today)).max((a, b) -> Integer.compare(a.getVersionNo(), b.getVersionNo()));
        return c.map(v -> new Applied(true, v.isCastingVote(), v.getWrittenMode(), v.isQuorumAfterRecusal(), v.getSpecialPercent(), v.getVersionNo())).orElse(NONE);
    }

    /** Why a casting vote cannot be used, or null. With a charter on file it decides; without one the closer's confirmation decides. */
    public static String castingVoteProblem(Applied a, boolean closerConfirms) {
        if (a.recorded()) return a.castingVote() ? null : "The committee's recorded charter (version " + a.versionNo() + ") gives the chair no casting vote";
        return closerConfirms ? null : "Confirm that the committee's charter or standing orders give the chair a casting vote";
    }

    /** Why a written resolution with this threshold cannot be circulated, or null. */
    public static String writtenProblem(Applied a, ApprovalThreshold threshold) {
        return switch (a.writtenMode()) {
            case NOT_ALLOWED -> "The committee's recorded charter (version " + a.versionNo() + ") does not allow written resolutions";
            case UNANIMOUS -> threshold == ApprovalThreshold.UNANIMOUS ? null : "The committee's recorded charter allows written resolutions only if they are unanimous";
            case MAJORITY_OF_ALL -> threshold == ApprovalThreshold.MAJORITY_OF_ALL ? null : "The committee's recorded charter allows written resolutions only by a majority of all members";
            case EITHER -> null;
        };
    }

    /** What the screen should offer: the thresholds that may be used. */
    public static List<ApprovalThreshold> allowedThresholds(Applied a) {
        return switch (a.writtenMode()) {
            case NOT_ALLOWED -> List.of();
            case UNANIMOUS -> List.of(ApprovalThreshold.UNANIMOUS);
            case MAJORITY_OF_ALL -> List.of(ApprovalThreshold.MAJORITY_OF_ALL);
            case EITHER -> List.of(ApprovalThreshold.UNANIMOUS, ApprovalThreshold.MAJORITY_OF_ALL);
        };
    }

    /** Whether a new version differs from the current one in any rule. Used to refuse a "change" that changes nothing. */
    public static boolean sameRules(Applied a, boolean castingVote, WrittenMode mode, boolean quorumAfterRecusal, int specialPercent) {
        return a.recorded() && a.castingVote() == castingVote && a.writtenMode() == mode && a.quorumAfterRecusal() == quorumAfterRecusal && a.specialPercent() == specialPercent;
    }
}
