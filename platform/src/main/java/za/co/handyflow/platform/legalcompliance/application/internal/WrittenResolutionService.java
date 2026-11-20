package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.WrittenResolutionRules.Counts;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.WrittenResolutionDtos.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * Written (circular) resolutions: a committee decision taken without a meeting. It is circulated to the committee's voting members as they are on the day. Members with a
 * user account answer for themselves; for a member without one, the secretary records the answer and how it was received. It closes when everyone has answered or the
 * deadline has passed (automatically, overnight), and the outcome comes from WrittenResolutionRules. While it is open, managers see who has answered but never how; the
 * audit trail records counts only and names nobody.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WrittenResolutionService {

    private static final String ENTITY = "COMMITTEE";

    private final CommitteeRepository committees;
    private final CommitteeMemberRepository members;
    private final WrittenResolutionRepository resolutions;
    private final WrittenResponseRepository responses;
    private final WrittenResolutionNotifier notifier;
    private final RegulatoryAuditService trail;
    private final CommitteeCharterRulesRepository charters;

    // ── Managers ──

    @Transactional
    public WrittenDetail circulate(TenantId t, CirculateRequest r, UUID uid, String uname) {
        Committee committee = committees.findForTenant(t, r.committeeId()).orElseThrow(() -> new ResourceNotFoundException("Committee", r.committeeId().toString()));
        if (committee.getStatus() != CommitteeStatus.ACTIVE) throw new HandyFlowException("A dissolved committee cannot pass written resolutions", HttpStatus.CONFLICT, "INVALID_STATE");
        String charterProblem = CharterRules.writtenProblem(CharterRules.current(charters.history(t, committee.getId()), today()), r.threshold());
        if (charterProblem != null) throw new HandyFlowException(charterProblem, HttpStatus.CONFLICT, "INVALID_STATE");
        List<CommitteeMember> voters = members.findForCommittee(t, committee.getId()).stream().filter(CommitteeMember::countsForQuorum).toList();
        WrittenResolution w = guarded(() -> WrittenResolution.circulate(t, committee.getId(), String.format("WRS-%05d", resolutions.nextRef()), r.title(), r.text(), r.threshold(), r.deadline(),
                voters.size(), committee.getQuorumMin(), today(), uname));
        resolutions.save(w);
        responses.saveAll(voters.stream().map(m -> WrittenResponse.assign(t, w.getId(), m.getId(), m.getMemberName(), m.getUserId())).toList());
        trail.record(t.getValue(), ENTITY, committee.getId(), "WRITTEN_CIRCULATED", w.getReference() + " " + w.getTitle() + " sent to " + voters.size() + " member(s), " + w.getThreshold() + ", answers by " + w.getDeadline(), uid, uname);
        notify(t, NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_REQUEST, "Written resolution for your vote: " + w.getTitle(),
                "Please answer by " + w.getDeadline() + " in Legal & Compliance → Written resolutions.", voters.stream().map(CommitteeMember::getUserId).filter(Objects::nonNull).toList());
        return detail(t, w.getId());
    }

    /** For a member without a user account: the secretary records what the member sent, and how it was received. */
    @Transactional
    public WrittenDetail recordOnBehalf(TenantId t, UUID id, UUID memberId, RecordRequest r, UUID uid, String uname) {
        WrittenResolution w = openResolution(t, id);
        if (w.isPastDeadline(today())) throw new HandyFlowException("The deadline has passed", HttpStatus.CONFLICT, "INVALID_STATE");
        WrittenResponse resp = responses.findForResolution(t, id).stream().filter(x -> x.getMemberId().equals(memberId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId.toString()));
        guarded(() -> { resp.recordOnBehalf(r.choice(), r.reason(), r.howReceived(), uname); return null; });
        responses.save(resp);
        trail.record(t.getValue(), ENTITY, w.getCommitteeId(), "WRITTEN_RECORDED", w.getReference() + ": an answer was recorded on a member's behalf (" + answered(t, id) + " of " + w.getMemberCount() + " answered)", uid, uname);
        return detail(t, id);
    }

    @Transactional
    public WrittenDetail close(TenantId t, UUID id, UUID uid, String uname) {
        WrittenResolution w = openResolution(t, id);
        closeNow(t, w, today(), false, uid, uname);
        return detail(t, id);
    }

    @Transactional
    public WrittenDetail cancel(TenantId t, UUID id, CancelRequest r, UUID uid, String uname) {
        WrittenResolution w = openResolution(t, id);
        guarded(() -> { w.cancel(r.reason(), today()); return null; });
        resolutions.save(w);
        trail.record(t.getValue(), ENTITY, w.getCommitteeId(), "WRITTEN_CANCELLED", w.getReference() + ": " + r.reason().trim(), uid, uname);
        notify(t, NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_CLOSED, "Written resolution cancelled: " + w.getTitle(), "It was cancelled and needs no answer.", accountIds(t, id));
        return detail(t, id);
    }

    @Transactional(readOnly = true)
    public List<WrittenView> list(TenantId t, UUID committeeId, WrittenStatus status) {
        Map<UUID, String> names = new HashMap<>();
        List<WrittenView> out = new ArrayList<>();
        for (WrittenResolution w : resolutions.search(t, committeeId, status)) out.add(view(t, w, names));
        return out;
    }

    @Transactional(readOnly = true)
    public WrittenDetail detail(TenantId t, UUID id) {
        WrittenResolution w = load(t, id);
        List<WrittenResponse> rows = responses.findForResolution(t, id);
        boolean showChoices = !w.isOpen();
        List<MemberRow> out = rows.stream().map(x -> new MemberRow(x.getMemberId(), x.getMemberName(), x.hasAccount(), !x.isPending(), x.getRespondedAt(),
                showChoices ? x.getChoice() : null, showChoices ? x.getReason() : null, x.getRecordedByName(), x.getHowReceived())).toList();
        return new WrittenDetail(view(t, w, new HashMap<>()), out);
    }

    // ── Any signed-in member ──

    @Transactional(readOnly = true)
    public List<MyWritten> mine(TenantId t, UUID uid) {
        List<WrittenResponse> mine = responses.findForUser(t, uid);
        if (mine.isEmpty()) return List.of();
        Map<UUID, WrittenResolution> byId = new HashMap<>();
        for (WrittenResolution w : resolutions.findForIds(t, mine.stream().map(WrittenResponse::getResolutionId).toList())) byId.put(w.getId(), w);
        List<MyWritten> out = new ArrayList<>();
        for (WrittenResponse x : mine) {
            WrittenResolution w = byId.get(x.getResolutionId());
            if (w == null) continue;
            out.add(new MyWritten(w.getId(), w.getReference(), w.getTitle(), w.getResolutionText(), WrittenResolutionRules.describe(w.getThreshold()), w.getDeadline(), w.getStatus(), w.isOpen(), x.getChoice(), x.getReason(), x.getRespondedAt()));
        }
        out.sort(Comparator.comparing(MyWritten::open).reversed().thenComparing(MyWritten::deadline));
        return out.size() > 50 ? out.subList(0, 50) : out;
    }

    @Transactional
    public MyWritten respond(TenantId t, UUID id, RespondRequest r, UUID uid, String uname) {
        WrittenResolution w = load(t, id);
        WrittenResponse mine = responses.findForResolution(t, id).stream().filter(x -> uid != null && uid.equals(x.getUserId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Written resolution", id.toString()));   // someone else's looks the same as none
        if (!w.isOpen()) throw new HandyFlowException("This written resolution is closed", HttpStatus.CONFLICT, "INVALID_STATE");
        if (w.isPastDeadline(today())) throw new HandyFlowException("The deadline has passed", HttpStatus.CONFLICT, "INVALID_STATE");
        guarded(() -> { mine.respond(r.choice(), r.reason()); return null; });
        responses.save(mine);
        trail.record(t.getValue(), ENTITY, w.getCommitteeId(), "WRITTEN_RESPONDED", w.getReference() + ": an answer was recorded (" + answered(t, id) + " of " + w.getMemberCount() + " answered)", uid, uname);
        return new MyWritten(w.getId(), w.getReference(), w.getTitle(), w.getResolutionText(), WrittenResolutionRules.describe(w.getThreshold()), w.getDeadline(), w.getStatus(), true, mine.getChoice(), mine.getReason(), mine.getRespondedAt());
    }

    // ── Overnight sweep ──

    /** Closes a resolution whose deadline has passed; otherwise sends reminders on reminder days. Returns true if it closed. */
    public boolean sweep(TenantId t, WrittenResolution w, LocalDate today) {
        if (!w.isOpen()) return false;
        if (w.isPastDeadline(today)) { closeNow(t, w, today, true, null, "System"); return true; }
        if (WrittenResolutionRules.remindToday(w.getDeadline(), today)) {
            List<UUID> pending = responses.findForResolution(t, w.getId()).stream().filter(WrittenResponse::isPending).map(WrittenResponse::getUserId).filter(Objects::nonNull).toList();
            notify(t, NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_REMINDER, "Written resolution " + WrittenResolutionRules.reminderText(w.getDeadline(), today) + ": " + w.getTitle(),
                    "You have not answered yet. Please answer in Legal & Compliance → Written resolutions.", pending);
        }
        return false;
    }

    // ── Helpers ──

    private void closeNow(TenantId t, WrittenResolution w, LocalDate today, boolean automatically, UUID uid, String uname) {
        List<WrittenResponse> rows = responses.findForResolution(t, w.getId());
        Counts counts = WrittenResolutionRules.count(rows.stream().map(WrittenResponse::getChoice).toList());
        String problem = automatically ? null : WrittenResolutionRules.closeProblem(counts, w.getDeadline(), today);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "INVALID_STATE");
        WrittenStatus outcome = WrittenResolutionRules.outcome(w.getThreshold(), counts);
        guarded(() -> { w.close(outcome, counts, today, automatically); return null; });
        resolutions.save(w);
        trail.record(t.getValue(), ENTITY, w.getCommitteeId(), "WRITTEN_CLOSED", w.getReference() + " " + w.getTitle() + ": " + outcome + " (" + counts.votesFor() + " for, " + counts.against() + " against, "
                + counts.abstained() + " abstained, " + counts.noResponse() + " no response of " + w.getMemberCount() + "), " + w.getThreshold() + (automatically ? ", closed automatically at the deadline" : ""), uid, uname);
        notify(t, NotificationType.LEGALCOMPLIANCE_WRITTEN_RESOLUTION_CLOSED, "Written resolution " + (outcome == WrittenStatus.CARRIED ? "carried" : "not carried") + ": " + w.getTitle(),
                counts.votesFor() + " for, " + counts.against() + " against, " + counts.abstained() + " abstained, " + counts.noResponse() + " did not answer.", rows.stream().map(WrittenResponse::getUserId).filter(Objects::nonNull).toList());
    }

    private WrittenResolution load(TenantId t, UUID id) { return resolutions.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Written resolution", id.toString())); }

    private WrittenResolution openResolution(TenantId t, UUID id) {
        WrittenResolution w = load(t, id);
        if (!w.isOpen()) throw new HandyFlowException("This written resolution is already closed", HttpStatus.CONFLICT, "INVALID_STATE");
        return w;
    }

    private int answered(TenantId t, UUID id) { return (int) responses.findForResolution(t, id).stream().filter(x -> !x.isPending()).count(); }

    private List<UUID> accountIds(TenantId t, UUID id) { return responses.findForResolution(t, id).stream().map(WrittenResponse::getUserId).filter(Objects::nonNull).toList(); }

    private WrittenView view(TenantId t, WrittenResolution w, Map<UUID, String> committeeNames) {
        String cname = committeeNames.computeIfAbsent(w.getCommitteeId(), cid -> committees.findForTenant(t, cid).map(Committee::getName).orElse("—"));
        List<WrittenResponse> rows = responses.findForResolution(t, w.getId());
        Counts live = WrittenResolutionRules.count(rows.stream().map(WrittenResponse::getChoice).toList());
        String problem = w.isOpen() ? WrittenResolutionRules.closeProblem(live, w.getDeadline(), today()) : null;
        return new WrittenView(w.getId(), w.getCommitteeId(), cname, w.getReference(), w.getTitle(), w.getResolutionText(), w.getThreshold(), WrittenResolutionRules.describe(w.getThreshold()), w.getDeadline(),
                w.getStatus(), w.getCirculatedOn(), w.getClosedOn(), w.isClosedAutomatically(), w.getCancelReason(), w.getMemberCount(), live.answered(),
                w.getVotesFor(), w.getVotesAgainst(), w.getAbstained(), w.getNoResponse(), w.isOpen() && problem == null, problem, w.isOpen() && w.isPastDeadline(today()));
    }

    private void notify(TenantId t, NotificationType type, String title, String message, Collection<UUID> userIds) {
        try { notifier.send(t, type, title, message, userIds); }
        catch (Exception e) { log.warn("[Legal/Compliance] Could not send written-resolution notification: {}", e.getMessage()); }
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
