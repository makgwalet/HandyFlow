package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.Committee;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeCharterRules;
import za.co.handyflow.platform.legalcompliance.domain.model.CommitteeStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.CommitteeCharterRulesRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.CommitteeRepository;
import za.co.handyflow.platform.legalcompliance.dto.CharterDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A committee's charter rules, kept as versions that are never edited. Recording a version is a decision about how the committee's votes are counted, so it needs a reason and
 * is written to the audit trail with what changed. A version that changes nothing is refused.
 */
@Service
@RequiredArgsConstructor
public class CommitteeCharterService {

    private static final String ENTITY = "COMMITTEE";

    private final CommitteeRepository committees;
    private final CommitteeCharterRulesRepository charters;
    private final RegulatoryAuditService trail;

    @Transactional(readOnly = true)
    public CharterView view(TenantId t, UUID committeeId) {
        Committee c = committee(t, committeeId);
        return render(c.getId(), charters.history(t, committeeId));
    }

    @Transactional
    public CharterView record(TenantId t, UUID committeeId, RecordCharterRequest r, UUID uid, String uname) {
        Committee c = committee(t, committeeId);
        if (c.getStatus() != CommitteeStatus.ACTIVE) throw new HandyFlowException("A dissolved committee's charter cannot be changed", HttpStatus.CONFLICT, "INVALID_STATE");
        List<CommitteeCharterRules> before = charters.history(t, committeeId);
        CharterRules.Applied now = CharterRules.current(before, today());
        if (CharterRules.sameRules(now, r.castingVote(), r.writtenMode(), r.quorumAfterRecusal(), r.specialPercent()) && before.stream().noneMatch(v -> v.getEffectiveFrom().isAfter(today())))
            throw new HandyFlowException("These are the rules already in force (version " + now.versionNo() + "): nothing to record", HttpStatus.CONFLICT, "INVALID_STATE");
        if (before.stream().anyMatch(v -> v.getEffectiveFrom().isAfter(r.effectiveFrom())))
            throw new HandyFlowException("A later version is already recorded with a later effective date; the new version cannot take effect before it", HttpStatus.CONFLICT, "INVALID_STATE");
        int next = charters.latestVersion(t, committeeId) + 1;
        CommitteeCharterRules v = guarded(() -> CommitteeCharterRules.record(t, committeeId, next, r.charterReference(), r.adoptedOn(), r.effectiveFrom(), r.castingVote(), r.writtenMode(),
                r.quorumAfterRecusal(), r.specialPercent(), r.reason(), uname));
        charters.save(v);
        trail.record(t.getValue(), ENTITY, committeeId, "CHARTER_RULES_RECORDED", "Charter rules version " + next + " from " + r.effectiveFrom() + ": " + describe(v) + (now.recorded() ? "; changed from " + describe(now) : "; first version")
                + ". Reason: " + v.getChangeReason(), uid, uname);
        List<CommitteeCharterRules> after = new ArrayList<>(before);
        after.add(v);
        return render(committeeId, after);
    }

    /** The rules in force today, for the other services. */
    @Transactional(readOnly = true)
    public CharterRules.Applied inForce(TenantId t, UUID committeeId) { return CharterRules.current(charters.history(t, committeeId), today()); }

    // ── Helpers ──

    private CharterView render(UUID committeeId, List<CommitteeCharterRules> versions) {
        LocalDate today = today();
        CharterRules.Applied a = CharterRules.current(versions, today);
        List<CommitteeCharterRules> newestFirst = versions.stream().sorted((x, y) -> Integer.compare(y.getVersionNo(), x.getVersionNo())).toList();
        List<CharterVersion> history = newestFirst.stream().map(v -> new CharterVersion(v.getId(), v.getVersionNo(), v.getCharterReference(), v.getAdoptedOn(), v.getEffectiveFrom(), v.isCastingVote(),
                v.getWrittenMode(), v.isQuorumAfterRecusal(), v.getSpecialPercent(), v.getChangeReason(), v.getRecordedByName(), a.recorded() && v.getVersionNo() == a.versionNo())).toList();
        return new CharterView(committeeId, a.recorded(), a.versionNo(), a.castingVote(), a.writtenMode(), a.quorumAfterRecusal(), a.specialPercent(), CharterRules.allowedThresholds(a), summary(a), history);
    }

    static List<String> summary(CharterRules.Applied a) {
        List<String> out = new ArrayList<>();
        if (!a.recorded()) {
            out.add("No charter rules are recorded for this committee, so the house rules apply.");
            out.add("Casting vote: the person closing the vote must confirm the charter gives the chair one.");
            out.add("Written resolutions: either threshold may be used.");
        } else {
            out.add("Casting vote: " + (a.castingVote() ? "the chair has one on a tied ordinary resolution." : "the chair has none; a tie is not carried."));
            out.add("Written resolutions: " + switch (a.writtenMode()) {
                case NOT_ALLOWED -> "not allowed.";
                case UNANIMOUS -> "allowed only if unanimous.";
                case MAJORITY_OF_ALL -> "allowed by a majority of all members.";
                case EITHER -> "allowed, unanimous or by a majority of all members.";
            });
        }
        out.add("Quorum after recusal: " + (a.quorumAfterRecusal() ? "enough members must remain after recusals to make up the quorum." : "not required."));
        out.add("Special resolution: at least " + a.specialPercent() + "% of the votes for and against.");
        return out;
    }

    private static String describe(CommitteeCharterRules v) { return "casting vote " + yn(v.isCastingVote()) + ", written " + v.getWrittenMode() + ", quorum after recusal " + yn(v.isQuorumAfterRecusal()) + ", special " + v.getSpecialPercent() + "%"; }
    private static String describe(CharterRules.Applied a) { return "casting vote " + yn(a.castingVote()) + ", written " + a.writtenMode() + ", quorum after recusal " + yn(a.quorumAfterRecusal()) + ", special " + a.specialPercent() + "%"; }
    private static String yn(boolean b) { return b ? "yes" : "no"; }

    private Committee committee(TenantId t, UUID id) { return committees.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Committee", id.toString())); }
    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
