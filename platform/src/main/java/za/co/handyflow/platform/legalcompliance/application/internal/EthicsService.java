package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.CoiDeclaration;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.GiftEntry;
import za.co.handyflow.platform.legalcompliance.domain.model.GiftStatus;
import za.co.handyflow.platform.legalcompliance.domain.repository.CoiDeclarationRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.GiftEntryRepository;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Conflict of interest and ethics: declarations and the gifts and hospitality register.
 * These are personal and sensitive, so people who cannot review see only their own entries (the {@code seeAll} flag, decided by the controller from the caller's
 * authorities). Nobody reviews or approves their own entry. Declarations are made as the signed-in user; nobody can declare on behalf of someone else here.
 */
@Service
@RequiredArgsConstructor
public class EthicsService {

    static final String DECL = "COI_DECLARATION", GIFT = "GIFT_ENTRY";

    private final CoiDeclarationRepository declarations;
    private final GiftEntryRepository gifts;
    private final RegulatoryAuditService trail;

    // ── Declarations ──

    @Transactional
    public DeclarationResponse declare(TenantId t, DeclarationRequest r, UUID uid, String uname) {
        CoiDeclaration d = guarded(() -> CoiDeclaration.declare(t, String.format("COI-%05d", declarations.nextRef()), uid, uname, r.declarationType(), r.counterparty(), r.description(),
                r.estimatedValue(), today(), r.reviewMonths() == 0 ? 12 : r.reviewMonths()));
        declarations.save(d);
        trail.record(t.getValue(), DECL, d.getId(), "DECLARED", d.getReference() + " " + d.getDeclarationType() + " — " + d.getCounterparty(), uid, uname);
        return view(d, uid);
    }

    @Transactional
    public DeclarationResponse amend(TenantId t, UUID id, DeclarationRequest r, UUID uid, String uname) {
        CoiDeclaration d = load(t, id);
        if (!d.getDeclarantId().equals(uid)) throw new HandyFlowException("Only the declarant can amend a declaration", HttpStatus.FORBIDDEN, "FORBIDDEN");
        guarded(() -> { d.amend(r.declarationType(), r.counterparty(), r.description(), r.estimatedValue(), r.reviewMonths() == 0 ? d.getReviewMonths() : r.reviewMonths()); return null; });
        declarations.save(d);
        trail.record(t.getValue(), DECL, id, "AMENDED", "Details changed before review", uid, uname);
        return view(d, uid);
    }

    @Transactional
    public DeclarationResponse startReview(TenantId t, UUID id, UUID uid, String uname) {
        CoiDeclaration d = load(t, id);
        guarded(() -> { d.startReview(uid, uname); return null; });
        declarations.save(d);
        trail.record(t.getValue(), DECL, id, "REVIEW_STARTED", "Reviewer " + uname, uid, uname);
        return view(d, uid);
    }

    @Transactional
    public DeclarationResponse decide(TenantId t, UUID id, DecisionRequest r, UUID uid, String uname) {
        CoiDeclaration d = load(t, id);
        guarded(() -> { d.decide(r.outcome(), r.note(), r.mitigation(), uid, uname, today()); return null; });
        declarations.save(d);
        trail.record(t.getValue(), DECL, id, "DECIDED", r.outcome() + ": " + r.note().trim(), uid, uname);
        return view(d, uid);
    }

    @Transactional
    public DeclarationResponse confirmUnchanged(TenantId t, UUID id, UUID uid, String uname) {
        CoiDeclaration d = load(t, id);
        guarded(() -> { d.confirmUnchanged(uid, today()); return null; });
        declarations.save(d);
        trail.record(t.getValue(), DECL, id, "CONFIRMED_UNCHANGED", "Next review " + d.getNextReviewDue(), uid, uname);
        return view(d, uid);
    }

    @Transactional
    public DeclarationResponse withdraw(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        CoiDeclaration d = load(t, id);
        guarded(() -> { d.withdraw(r.reason(), uid); return null; });
        declarations.save(d);
        trail.record(t.getValue(), DECL, id, "WITHDRAWN", r.reason().trim(), uid, uname);
        return view(d, uid);
    }

    @Transactional(readOnly = true)
    public List<DeclarationResponse> declarations(TenantId t, UUID viewer, boolean seeAll, DeclarationStatus status) {
        return declarations.search(t, seeAll ? null : viewer, status).stream().map(d -> view(d, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public DeclarationResponse declaration(TenantId t, UUID id, UUID viewer, boolean seeAll) {
        CoiDeclaration d = load(t, id);
        if (!seeAll && !d.getDeclarantId().equals(viewer)) throw new ResourceNotFoundException("Declaration", id.toString());
        return view(d, viewer);
    }

    // ── Gifts and hospitality ──

    @Transactional
    public GiftResponse recordGift(TenantId t, GiftRequest r, UUID uid, String uname) {
        GiftEntry g = guarded(() -> GiftEntry.record(t, String.format("GFT-%05d", gifts.nextRef()), r.direction(), uid, uname, r.counterparty(), r.description(), r.occasion(), r.entryDate(),
                r.valueZar(), EthicsGovernanceRules.DEFAULT_GIFT_THRESHOLD, today()));
        gifts.save(g);
        trail.record(t.getValue(), GIFT, g.getId(), "RECORDED", g.getReference() + " " + g.getDirection() + " R" + g.getValueZar() + (g.getStatus() == GiftStatus.PENDING ? " — needs approval" : ""), uid, uname);
        return view(g, uid);
    }

    @Transactional
    public GiftResponse approveGift(TenantId t, UUID id, GiftDecisionRequest r, UUID uid, String uname) {
        GiftEntry g = loadGift(t, id);
        guarded(() -> { g.approve(uid, uname, r == null ? null : r.note(), today()); return null; });
        gifts.save(g);
        trail.record(t.getValue(), GIFT, id, "APPROVED", g.getReference() + (g.getDecisionNote() == null ? "" : ": " + g.getDecisionNote()), uid, uname);
        return view(g, uid);
    }

    @Transactional
    public GiftResponse declineGift(TenantId t, UUID id, GiftDecisionRequest r, UUID uid, String uname) {
        GiftEntry g = loadGift(t, id);
        guarded(() -> { g.decline(uid, uname, r == null ? null : r.note(), today()); return null; });
        gifts.save(g);
        trail.record(t.getValue(), GIFT, id, "DECLINED", g.getReference() + ": " + g.getDecisionNote(), uid, uname);
        return view(g, uid);
    }

    @Transactional(readOnly = true)
    public List<GiftResponse> gifts(TenantId t, UUID viewer, boolean seeAll, GiftStatus status) {
        return gifts.search(t, seeAll ? null : viewer, status).stream().map(g -> view(g, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public EthicsSummary summary(TenantId t) {
        LocalDate today = today();
        List<CoiDeclaration> ds = declarations.search(t, null, null);
        return new EthicsSummary(ds.stream().filter(CoiDeclaration::awaitingReview).count(),
                ds.stream().filter(d -> d.getStatus() == DeclarationStatus.CLEARED || d.getStatus() == DeclarationStatus.MITIGATED).count(),
                ds.stream().filter(d -> (d.getStatus() == DeclarationStatus.CLEARED || d.getStatus() == DeclarationStatus.MITIGATED) && EthicsGovernanceRules.reviewOverdue(d.getNextReviewDue(), today)).count(),
                gifts.search(t, null, GiftStatus.PENDING).size(), ds.stream().filter(d -> d.getStatus() == DeclarationStatus.NOT_PERMITTED).count());
    }

    // ── internals ──

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private CoiDeclaration load(TenantId t, UUID id) { return declarations.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Declaration", id.toString())); }

    private GiftEntry loadGift(TenantId t, UUID id) { return gifts.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Gift entry", id.toString())); }

    private static DeclarationResponse view(CoiDeclaration d, UUID viewer) {
        boolean inForce = d.getStatus() == DeclarationStatus.CLEARED || d.getStatus() == DeclarationStatus.MITIGATED;
        return new DeclarationResponse(d.getId(), d.getReference(), d.getDeclarantId(), d.getDeclarantName(), d.getDeclarationType(), d.getCounterparty(), d.getDescription(), d.getEstimatedValue(),
                d.getDeclaredOn(), d.getStatus(), d.getReviewerName(), d.getReviewedOn(), d.getDecisionNote(), d.getMitigation(), d.getReviewMonths(), d.getNextReviewDue(),
                inForce && EthicsGovernanceRules.reviewOverdue(d.getNextReviewDue(), today()), d.getDeclarantId().equals(viewer));
    }

    private static GiftResponse view(GiftEntry g, UUID viewer) {
        return new GiftResponse(g.getId(), g.getReference(), g.getDirection(), g.getDeclarantId(), g.getDeclarantName(), g.getCounterparty(), g.getDescription(), g.getOccasion(), g.getEntryDate(),
                g.getValueZar(), g.getThresholdZar(), g.getStatus(), g.getDecidedByName(), g.getDecidedOn(), g.getDecisionNote(), g.getDeclarantId().equals(viewer));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
