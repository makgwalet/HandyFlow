package za.co.handyflow.platform.internalaudit.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.internalaudit.domain.model.*;
import za.co.handyflow.platform.internalaudit.domain.repository.*;
import za.co.handyflow.platform.internalaudit.dto.AuditInvestigationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

/**
 * Investigation case files. Two locks: the caller needs AUDIT_INVESTIGATE (checked at the controller) and must be named on the case (checked here).
 * Someone who is not on a case gets "not found", exactly as if it did not exist; administrators have no way in. Everything that happens is written to the
 * case's own hash-chained record and deliberately NOT to the company-wide audit trail, which administrators can read.
 */
@Service
@RequiredArgsConstructor
public class AuditInvestigationService {
    public record Actor(UUID userId, String name) {}
    static final Duration VIEW_LOG_GAP = Duration.ofMinutes(15);

    private final AuditInvestigationRepository cases;
    private final AuditInvestigationMemberRepository members;
    private final AuditInvestigationLogRepository log;
    private final AuditInvestigationEvidenceRepository evidence;
    private final UserRepository users;

    /** Only the cases the caller is named on; the others are not hinted at. */
    @Transactional(readOnly = true)
    public List<Summary> list(TenantId tenant, Actor actor) {
        UUID t = tenant.getValue();
        var mine = members.findByTenantIdAndUserIdAndRemovedAtIsNull(t, actor.userId());
        if (mine.isEmpty()) return List.of();
        Map<UUID, String> roles = new HashMap<>();
        mine.forEach(m -> roles.put(m.getInvestigationId(), m.getRole()));
        return cases.findAllByIds(t, new ArrayList<>(roles.keySet())).stream()
                .map(c -> summary(c, roles.get(c.getId()), (int) members.findByTenantIdAndInvestigationId(t, c.getId()).stream().filter(AuditInvestigationMember::isActive).count())).toList();
    }

    @Transactional
    public View open(TenantId tenant, Actor actor, String title, String summaryText) {
        UUID t = tenant.getValue();
        int n = (int) cases.countByTenantId(t) + 1;
        while (cases.existsByTenantIdAndNumber(t, AuditInvestigation.numberFor(n))) n++;
        final String number = AuditInvestigation.numberFor(n);   // n changes in the loop above, so the lambda takes a final copy
        AuditInvestigation c = guard(() -> AuditInvestigation.open(t, number, title, summaryText, actor.userId()));
        cases.save(c);
        members.save(AuditInvestigationMember.of(t, c.getId(), actor.userId(), actor.name(), "LEAD", actor.userId()));
        append(t, c.getId(), actor, "CASE_OPENED", c.getNumber() + ": " + c.getTitle());
        return view(t, c, actor.userId());
    }

    @Transactional
    public View get(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        requireMember(t, id, actor);
        var c = lock(t, id);
        // Reading a case is a custody event too, but one line per person per quarter of an hour keeps the record readable.
        var last = log.findFirstByTenantIdAndInvestigationIdAndActorIdAndActionOrderBySeqDesc(t, id, actor.userId(), "VIEWED");
        if (last.isEmpty() || last.get().getAt().isBefore(Instant.now().minus(VIEW_LOG_GAP))) append(t, id, actor, "VIEWED", null);
        return view(t, c, actor.userId());
    }

    @Transactional
    public View addMember(TenantId tenant, Actor actor, UUID id, UUID userId, String role) {
        UUID t = tenant.getValue();
        requireLead(t, id, actor);
        var c = writable(t, id);
        if (userId == null) throw bad("Choose a person", "INV_INVALID");
        var user = users.findByIdAndTenantId(userId, tenant).orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        if (members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(t, id, userId).isPresent())
            throw conflict("That person is already on the case; remove them first to change their role", "INV_MEMBER_EXISTS");
        String name = (user.getFirstName() + " " + user.getLastName()).trim();
        var m = guard(() -> AuditInvestigationMember.of(t, id, userId, name, role, actor.userId()));
        members.save(m);
        append(t, id, actor, "MEMBER_ADDED", name + " as " + m.getRole());
        return view(t, c, actor.userId());
    }

    /** A lead can remove anyone; anyone can take themselves off. The last lead cannot be removed. */
    @Transactional
    public View removeMember(TenantId tenant, Actor actor, UUID id, UUID userId) {
        UUID t = tenant.getValue();
        var me = requireMember(t, id, actor);
        if (!me.isLead() && !actor.userId().equals(userId)) throw new HandyFlowException("Only a lead can do this", HttpStatus.FORBIDDEN, "INV_LEAD_ONLY");
        var c = writable(t, id);
        var target = members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(t, id, userId).orElseThrow(() -> new ResourceNotFoundException("Member", String.valueOf(userId)));
        guard(() -> target.remove(members.findByTenantIdAndInvestigationId(t, id).stream().filter(AuditInvestigationMember::isActive).toList()));
        members.save(target);
        append(t, id, actor, "MEMBER_REMOVED", target.getUserName());
        // The person just removed can no longer see the case, so the view is built for the caller only if they are still on it.
        if (members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(t, id, actor.userId()).isEmpty()) return null;
        return view(t, c, actor.userId());
    }

    @Transactional
    public View addNote(TenantId tenant, Actor actor, UUID id, String text) {
        UUID t = tenant.getValue();
        requireWriter(t, id, actor);
        var c = writable(t, id);
        if (text == null || text.trim().length() < NOTE_MIN) throw bad("A note needs at least " + NOTE_MIN + " characters", "INV_INVALID");
        append(t, id, actor, "NOTE", text.trim());
        return view(t, c, actor.userId());
    }

    @Transactional
    public View collectEvidence(TenantId tenant, Actor actor, UUID id, String description, String source) {
        UUID t = tenant.getValue();
        requireWriter(t, id, actor);
        var c = writable(t, id);
        String ref = AuditInvestigationEvidence.refFor((int) evidence.countByTenantIdAndInvestigationId(t, id) + 1);
        var e = guard(() -> AuditInvestigationEvidence.collect(t, id, ref, description, source, actor.userId(), actor.name()));
        evidence.save(e);
        append(t, id, actor, "EVIDENCE_COLLECTED", ref + ": " + e.getDescription() + (e.getSource() == null ? "" : " (from " + e.getSource() + ")"));
        return view(t, c, actor.userId());
    }

    @Transactional
    public View transferEvidence(TenantId tenant, Actor actor, UUID id, UUID evidenceId, UUID toUserId, String reason) {
        UUID t = tenant.getValue();
        var me = requireWriter(t, id, actor);
        var c = writable(t, id);
        var e = evidence.findByTenantIdAndInvestigationIdAndId(t, id, evidenceId).orElseThrow(() -> new ResourceNotFoundException("Evidence", String.valueOf(evidenceId)));
        if (reason == null || reason.trim().length() < NOTE_MIN) throw bad("Say why it is being handed over (at least " + NOTE_MIN + " characters)", "INV_INVALID");
        var to = members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(t, id, toUserId).filter(AuditInvestigationMember::canWrite)
                .orElseThrow(() -> bad("Evidence can only be handed to a lead or investigator on this case", "INV_INVALID"));
        String from = e.getHolderName();
        guard(() -> e.transfer(actor.userId(), me.isLead(), to.getUserId(), to.getUserName()));
        evidence.save(e);
        append(t, id, actor, "EVIDENCE_TRANSFERRED", e.getRef() + " from " + from + " to " + to.getUserName() + ": " + reason.trim());
        return view(t, c, actor.userId());
    }

    @Transactional
    public View setReview(TenantId tenant, Actor actor, UUID id, boolean underReview) {
        UUID t = tenant.getValue();
        requireLead(t, id, actor);
        var c = writable(t, id);
        guard(() -> c.setReview(underReview));
        cases.save(c);
        append(t, id, actor, "STATUS_CHANGED", underReview ? "Under review" : "Back to open");
        return view(t, c, actor.userId());
    }

    @Transactional
    public View close(TenantId tenant, Actor actor, UUID id, String reason) {
        UUID t = tenant.getValue();
        requireLead(t, id, actor);
        var c = writable(t, id);
        guard(() -> c.close(reason, actor.userId()));
        cases.save(c);
        append(t, id, actor, "CASE_CLOSED", c.getCloseReason());
        return view(t, c, actor.userId());
    }

    @Transactional(readOnly = true)
    public Verification verify(TenantId tenant, Actor actor, UUID id) {
        UUID t = tenant.getValue();
        requireMember(t, id, actor);
        var r = InvestigationChain.verify(log.findByTenantIdAndInvestigationIdOrderBySeqAsc(t, id));
        return new Verification(r.ok(), r.entries(), r.brokenAtSeq());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────

    static final int NOTE_MIN = 10;

    /** Not on the case means the case does not exist, as far as the caller can tell. */
    private AuditInvestigationMember requireMember(UUID t, UUID id, Actor actor) {
        return members.findByTenantIdAndInvestigationIdAndUserIdAndRemovedAtIsNull(t, id, actor.userId())
                .orElseThrow(() -> new ResourceNotFoundException("AuditInvestigation", id.toString()));
    }

    private AuditInvestigationMember requireWriter(UUID t, UUID id, Actor actor) {
        var m = requireMember(t, id, actor);
        if (!m.canWrite()) throw new HandyFlowException("Observers can read this case but not change it", HttpStatus.FORBIDDEN, "INV_READ_ONLY");
        return m;
    }

    private AuditInvestigationMember requireLead(UUID t, UUID id, Actor actor) {
        var m = requireMember(t, id, actor);
        if (!m.isLead()) throw new HandyFlowException("Only a lead can do this", HttpStatus.FORBIDDEN, "INV_LEAD_ONLY");
        return m;
    }

    private AuditInvestigation lock(UUID t, UUID id) {
        return cases.lockByTenantIdAndId(t, id).orElseThrow(() -> new ResourceNotFoundException("AuditInvestigation", id.toString()));
    }

    private AuditInvestigation writable(UUID t, UUID id) {
        var c = lock(t, id);
        guard(c::requireWritable);
        return c;
    }

    /** The row lock taken by {@link #lock} is what keeps two writers from taking the same place in the chain. */
    private AuditInvestigationLogEntry append(UUID t, UUID id, Actor actor, String action, String detail) {
        var previous = log.findFirstByTenantIdAndInvestigationIdOrderBySeqDesc(t, id).orElse(null);
        return log.save(AuditInvestigationLogEntry.next(t, id, previous, Instant.now().truncatedTo(ChronoUnit.MICROS), actor.userId(), actor.name(), action, detail));
    }

    private Summary summary(AuditInvestigation c, String myRole, int memberCount) {
        return new Summary(c.getId(), c.getNumber(), c.getTitle(), c.getStatus(), myRole, c.getOpenedAt(), memberCount);
    }

    private View view(UUID t, AuditInvestigation c, UUID me) {
        var all = members.findByTenantIdAndInvestigationId(t, c.getId()).stream().filter(AuditInvestigationMember::isActive).toList();
        String myRole = all.stream().filter(m -> m.getUserId().equals(me)).map(AuditInvestigationMember::getRole).findFirst().orElse(null);
        return new View(summary(c, myRole, all.size()), c.getSummary(), c.getCloseReason(), c.getClosedAt(),
                all.stream().map(m -> new MemberView(m.getUserId(), m.getUserName(), m.getRole(), m.getAddedAt())).toList(),
                evidence.findByTenantIdAndInvestigationIdOrderByRefAsc(t, c.getId()).stream()
                        .map(e -> new EvidenceView(e.getId(), e.getRef(), e.getDescription(), e.getSource(), e.getCollectedAt(), e.getHolderId(), e.getHolderName())).toList(),
                log.findByTenantIdAndInvestigationIdOrderBySeqAsc(t, c.getId()).stream()
                        .map(l -> new LogView(l.getSeq(), l.getAt(), l.getActorName(), l.getAction(), l.getDetail(), l.getHash())).toList());
    }

    private static HandyFlowException bad(String m, String code) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, code); }
    private static HandyFlowException conflict(String m, String code) { return new HandyFlowException(m, HttpStatus.CONFLICT, code); }
    private static void guard(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "INV_INVALID"); } catch (IllegalStateException e) { throw conflict(e.getMessage(), "INV_STATE"); }
    }
    private static <T> T guard(Supplier<T> s) {
        try { return s.get(); } catch (IllegalArgumentException e) { throw bad(e.getMessage(), "INV_INVALID"); } catch (IllegalStateException e) { throw conflict(e.getMessage(), "INV_STATE"); }
    }
}
