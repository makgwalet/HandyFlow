package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.CoiDeclarationRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.DeclarationCampaignRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.DeclarationReturnRepository;
import za.co.handyflow.platform.legalcompliance.dto.DeclarationCampaignDtos.*;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * Staff-wide declaration campaigns. A campaign goes to every active user (optionally one department) as they were when it launched, and each person answers once:
 * nothing to declare, earlier declarations unchanged, or declare an interest. Declaring creates an ordinary conflict-of-interest declaration for the person, reviewed
 * through the normal route; people do not need any Legal/Compliance permission to answer. Managers see who has answered and how, and never what anyone declared.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeclarationCampaignService {

    static final String CAMPAIGN = "DECLARATION_CAMPAIGN", RETURN = "DECLARATION_RETURN";

    private final DeclarationCampaignRepository campaigns;
    private final DeclarationReturnRepository returns;
    private final CoiDeclarationRepository declarations;
    private final DeclarationMaker declarationMaker;
    private final UserDirectory directory;
    private final DeclarationNotifier notifier;
    private final CampaignOwners owners;
    private final RegulatoryAuditService trail;

    // ── Managers ──

    @Transactional
    public CampaignResponse launch(TenantId t, CampaignRequest r, UUID uid, String uname) {
        DeclarationCampaign c = guarded(() -> DeclarationCampaign.launch(t, String.format("DEC-%05d", campaigns.nextRef()), r.title(), r.instructions(), r.dueDate(), r.department(), today(), uid, uname));
        List<UserDirectory.Person> audience = directory.activeUsers(t, c.getDepartmentFilter());
        if (audience.isEmpty()) throw new HandyFlowException("No active users match this audience", HttpStatus.BAD_REQUEST, "EMPTY_AUDIENCE");
        campaigns.save(c);
        returns.saveAll(audience.stream().map(a -> DeclarationReturn.assign(t, c.getId(), a.id(), a.name(), a.email())).toList());
        trail.record(t.getValue(), CAMPAIGN, c.getId(), "LAUNCHED", c.getReference() + " to " + audience.size() + " user(s), due " + c.getDueDate(), uid, uname);
        notify(t, NotificationType.LEGALCOMPLIANCE_DECLARATION_REQUEST, "Please make your annual declaration: " + c.getTitle(), "Due " + c.getDueDate() + ". It takes a minute if you have nothing to declare.",
                audience.stream().map(a -> Recipient.user(a.id(), a.name(), a.email(), null)).toList());
        return view(c, returns.findByCampaign(t, c.getId()));
    }

    @Transactional
    public CampaignResponse close(TenantId t, UUID id, CloseRequest r, UUID uid, String uname) {
        DeclarationCampaign c = load(t, id);
        guarded(() -> { c.close(uname, r.reason()); return null; });
        campaigns.save(c);
        trail.record(t.getValue(), CAMPAIGN, id, "CLOSED", c.getReference() + ": " + r.reason().trim(), uid, uname);
        return view(c, returns.findByCampaign(t, id));
    }

    /**
     * Brings a running campaign in line with who is in the audience now: people who joined (or moved into the department) are added and asked; people who have left (or moved out)
     * and have not answered are excused; an excused person who is back is put back. Anyone who has answered is left exactly as they are. If the user directory returns nobody at all
     * it is treated as a failed lookup and nothing is changed, so an outage can never excuse a whole campaign.
     */
    @Transactional
    public SyncResult updateAudience(TenantId t, UUID id, UUID uid, String uname) {
        DeclarationCampaign c = load(t, id);
        if (!c.isOpen()) throw new HandyFlowException("This campaign is closed", HttpStatus.CONFLICT, "CAMPAIGN_CLOSED");
        List<UserDirectory.Person> audience = directory.activeUsers(t, c.getDepartmentFilter());
        if (audience.isEmpty()) throw new HandyFlowException("The user directory returned nobody, so the audience was left as it is", HttpStatus.CONFLICT, "EMPTY_AUDIENCE");
        Set<UUID> now = new HashSet<>();
        for (UserDirectory.Person p : audience) now.add(p.id());
        List<DeclarationReturn> rows = returns.findByCampaign(t, id);
        Set<UUID> already = new HashSet<>();
        int excused = 0, back = 0;
        for (DeclarationReturn r : rows) {
            already.add(r.getUserId());
            if (r.isPending() && !now.contains(r.getUserId())) { r.markLeft(); returns.save(r); excused++; }
            else if (r.isLeft() && now.contains(r.getUserId())) { r.reinstate(); returns.save(r); back++; }
        }
        List<UserDirectory.Person> fresh = new ArrayList<>();
        for (UserDirectory.Person p : audience) if (already.add(p.id())) fresh.add(p);
        if (!fresh.isEmpty()) returns.saveAll(fresh.stream().map(a -> DeclarationReturn.assign(t, c.getId(), a.id(), a.name(), a.email())).toList());
        if (!fresh.isEmpty() || excused > 0 || back > 0) {
            trail.record(t.getValue(), CAMPAIGN, id, "AUDIENCE_UPDATED", c.getReference() + ": " + fresh.size() + " added, " + excused + " excused (no longer in the audience), " + back + " put back", uid, uname);
        }
        if (!fresh.isEmpty()) notify(t, NotificationType.LEGALCOMPLIANCE_DECLARATION_REQUEST, "Please make your annual declaration: " + c.getTitle(), "Due " + c.getDueDate() + ". It takes a minute if you have nothing to declare.",
                fresh.stream().map(a -> Recipient.user(a.id(), a.name(), a.email(), null)).toList());
        return new SyncResult(fresh.size(), excused, back, view(c, returns.findByCampaign(t, id)));
    }

    /** The overnight version: excuses people who are no longer in the audience (never adds or reinstates), so they are not chased and do not hold the campaign overdue. */
    private int excuseLeavers(TenantId t, DeclarationCampaign c) {
        List<UserDirectory.Person> audience;
        try { audience = directory.activeUsers(t, c.getDepartmentFilter()); }
        catch (Exception e) { log.warn("[Legal/Compliance] Could not read the audience for {}: {}", c.getId(), e.getMessage()); return 0; }
        if (audience == null || audience.isEmpty()) return 0;   // a failed or empty lookup never excuses anyone
        Set<UUID> now = new HashSet<>();
        for (UserDirectory.Person p : audience) now.add(p.id());
        int excused = 0;
        for (DeclarationReturn r : returns.findPending(t, c.getId())) {
            if (now.contains(r.getUserId())) continue;
            r.markLeft();
            returns.save(r);
            excused++;
        }
        if (excused > 0) trail.record(t.getValue(), CAMPAIGN, c.getId(), "AUDIENCE_UPDATED", c.getReference() + ": 0 added, " + excused + " excused (no longer in the audience), 0 put back", null, "System");
        return excused;
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> list(TenantId t, CampaignStatus status) {
        return campaigns.search(t, status).stream().map(c -> view(c, returns.findByCampaign(t, c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDetail detail(TenantId t, UUID id) {
        DeclarationCampaign c = load(t, id);
        List<DeclarationReturn> rows = returns.findByCampaign(t, id);
        return new CampaignDetail(view(c, rows), rows.stream().map(x -> new ReturnRow(x.getId(), x.getUserName(), x.getUserEmail(), x.getStatus(), x.getRespondedAt())).toList());
    }

    // ── Any signed-in person ──

    @Transactional(readOnly = true)
    public List<MyReturn> mine(TenantId t, UUID uid) {
        List<MyReturn> out = new ArrayList<>();
        for (DeclarationReturn r : returns.findForUser(t, uid)) out.add(myView(t, r, load(t, r.getCampaignId()), uid));
        return out;
    }

    @Transactional
    public MyReturn answer(TenantId t, UUID returnId, AnswerRequest req, UUID uid, String uname) {
        DeclarationReturn r = own(t, returnId, uid);
        DeclarationCampaign c = openCampaign(t, r);
        if (req.answer() == Answer.NIL) guarded(() -> { r.answerNil(); return null; });
        int extended = 0;
        if (req.answer() == Answer.UNCHANGED) {
            List<CoiDeclaration> existing = existing(t, uid, r);
            guarded(() -> { r.answerUnchanged(existing.size()); return null; });
            extended = extendReviewDates(t, existing, uid, uname);
        }
        returns.save(r);
        trail.record(t.getValue(), RETURN, r.getId(), req.answer().name(), c.getReference() + (extended > 0 ? " — " + extended + " review date(s) extended" : ""), uid, uname);
        return myView(t, r, c, uid).withReviewDatesExtended(extended);
    }

    /** Declares an interest for the signed-in person, as an ordinary conflict-of-interest declaration, and records it against their return. */
    @Transactional
    public MyReturn declare(TenantId t, UUID returnId, EthicsDtos.DeclarationRequest req, UUID uid, String uname) {
        DeclarationReturn r = own(t, returnId, uid);
        DeclarationCampaign c = openCampaign(t, r);
        if (r.isLeft()) throw new HandyFlowException("You are no longer part of this campaign", HttpStatus.CONFLICT, "INVALID_STATE");   // before anything is declared
        EthicsDtos.DeclarationResponse d = declarationMaker.declare(t, req, uid, uname);
        guarded(() -> { r.addDeclaration(d.id()); return null; });
        returns.save(r);
        trail.record(t.getValue(), RETURN, r.getId(), "DECLARED", c.getReference() + " — " + d.reference(), uid, uname);
        return myView(t, r, c, uid);
    }

    // ── Daily reminders ──

    /**
     * Reminds each person who has not answered, on the days DeclarationRules.remindToday allows. Once the campaign is overdue, whoever launched it also gets a count of how many
     * are still outstanding (a count only, never who answered what). Returns how many people were reminded.
     */
    public int remind(TenantId t, DeclarationCampaign c, LocalDate today) {
        if (!c.isOpen()) return 0;
        excuseLeavers(t, c);
        if (!DeclarationRules.remindToday(c.getDueDate(), today)) return 0;
        List<DeclarationReturn> pending = returns.findPending(t, c.getId());
        if (pending.isEmpty()) return 0;
        String when = DeclarationRules.reminderText(c.getDueDate(), today);
        notify(t, NotificationType.LEGALCOMPLIANCE_DECLARATION_REMINDER, "Declaration " + when, c.getTitle() + ". Please answer in Legal & Compliance → Declarations.",
                pending.stream().map(p -> Recipient.user(p.getUserId(), p.getUserName(), p.getUserEmail(), null)).toList());
        if (today.isAfter(c.getDueDate())) {
            int total = (int) returns.findByCampaign(t, c.getId()).stream().filter(r -> !r.isLeft()).count();
            List<Recipient> to;
            try { to = owners.of(t, c.getLaunchedBy()); } catch (Exception e) { log.warn("[Legal/Compliance] Could not find who launched {}: {}", c.getId(), e.getMessage()); to = List.of(); }
            if (!to.isEmpty()) notify(t, NotificationType.LEGALCOMPLIANCE_DECLARATION_OVERDUE, c.getReference() + " is overdue: " + pending.size() + " of " + total + " not answered",
                    c.getTitle() + " was due " + c.getDueDate() + ". Open Legal & Compliance → Declarations to see who is outstanding, or close the campaign.", to);
        }
        return pending.size();
    }

    // ── Helpers ──

    private DeclarationCampaign load(TenantId t, UUID id) { return campaigns.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Declaration campaign", id.toString())); }

    private DeclarationReturn own(TenantId t, UUID returnId, UUID uid) {
        DeclarationReturn r = returns.findForTenant(t, returnId).orElseThrow(() -> new ResourceNotFoundException("Declaration return", returnId.toString()));
        if (uid == null || !uid.equals(r.getUserId())) throw new ResourceNotFoundException("Declaration return", returnId.toString());   // someone else's looks the same as none
        return r;
    }

    private DeclarationCampaign openCampaign(TenantId t, DeclarationReturn r) {
        DeclarationCampaign c = load(t, r.getCampaignId());
        if (!c.isOpen()) throw new HandyFlowException("This campaign is closed", HttpStatus.CONFLICT, "CAMPAIGN_CLOSED");
        return c;
    }

    /**
     * "Unchanged" is the declarant's own confirmation, so it does what the confirm button on the declaration does: for each earlier declaration a reviewer has cleared or
     * mitigated, the next review moves a full review period from today. Declarations still awaiting review have no review date and are left alone, as are ones already
     * confirmed today. Returns how many review dates moved.
     */
    private int extendReviewDates(TenantId t, List<CoiDeclaration> existing, UUID uid, String uname) {
        LocalDate today = today();
        int n = 0;
        for (CoiDeclaration d : existing) {
            if (!DeclarationRules.extendsReview(d.getStatus()) || DeclarationRules.alreadyConfirmed(d.getNextReviewDue(), d.getReviewMonths(), today)) continue;
            guarded(() -> { d.confirmUnchanged(uid, today); return null; });
            declarations.save(d);
            trail.record(t.getValue(), "COI_DECLARATION", d.getId(), "CONFIRMED_UNCHANGED", "Through an annual declaration campaign. Next review " + d.getNextReviewDue(), uid, uname);
            n++;
        }
        return n;
    }

    /** The person's earlier declarations that "unchanged" can vouch for, not counting any made through this campaign. */
    private List<CoiDeclaration> existing(TenantId t, UUID uid, DeclarationReturn r) {
        Set<UUID> through = new HashSet<>(r.declaredIds());
        return declarations.search(t, uid, null).stream().filter(d -> DeclarationRules.confirmable(d.getStatus()) && !through.contains(d.getId())).toList();
    }

    private MyReturn myView(TenantId t, DeclarationReturn r, DeclarationCampaign c, UUID uid) {
        List<MyDeclaration> mine = new ArrayList<>();
        for (UUID id : r.declaredIds()) declarations.findForTenant(t, id).ifPresent(d -> mine.add(new MyDeclaration(d.getId(), d.getReference(), d.getDeclarationType(), d.getCounterparty(), d.getStatus())));
        return new MyReturn(r.getId(), c.getId(), c.getTitle(), c.getInstructions(), c.getDueDate(), r.isPending() && c.isOverdue(today()), c.isOpen(), r.getStatus(), r.getRespondedAt(), r.getStatement(),
                existing(t, uid, r).size(), mine, DeclarationReturn.NIL_STATEMENT, DeclarationReturn.UNCHANGED_STATEMENT, 0);
    }

    private CampaignResponse view(DeclarationCampaign c, List<DeclarationReturn> rows) {
        int left = count(rows, ReturnStatus.LEFT), active = rows.size() - left;
        int nil = count(rows, ReturnStatus.NIL), unchanged = count(rows, ReturnStatus.UNCHANGED), declared = count(rows, ReturnStatus.DECLARED), pending = active - nil - unchanged - declared;
        return new CampaignResponse(c.getId(), c.getReference(), c.getTitle(), c.getInstructions(), c.getDueDate(), c.isOverdue(today()), c.getDepartmentFilter(), c.getStatus(), c.getLaunchedByName(),
                c.getClosedAt(), c.getCloseReason(), active, nil, unchanged, declared, pending, left, DeclarationRules.percent(active - pending, active));
    }

    private static int count(List<DeclarationReturn> rows, ReturnStatus s) { return (int) rows.stream().filter(r -> r.getStatus() == s).count(); }

    private void notify(TenantId t, NotificationType type, String title, String message, List<Recipient> to) {
        try { notifier.send(t, type, title, message, to); }
        catch (Exception e) { log.warn("[Legal/Compliance] Could not send declaration notification: {}", e.getMessage()); }
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
