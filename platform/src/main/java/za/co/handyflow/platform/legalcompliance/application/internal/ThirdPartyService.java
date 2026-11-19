package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.ThirdPartyItemRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ThirdPartyRepository;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.InfoGovDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Third-party (supplier, partner, contractor, service provider) compliance: a profile, a checklist of due-diligence items and certificates with expiry
 * dates, an approval that cannot be given until every required item is verified and unexpired (and, where personal information is handled, an operator
 * agreement is on file), and periodic reviews. The approver cannot be the person who created the record. Item evidence is held by the shared evidence module
 * under entity type THIRD_PARTY_ITEM; the service only counts it, so the person verifying can see whether anything was attached.
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyService {

    static final String SOURCE_MODULE = "legalcompliance";
    static final String ENTITY = "THIRD_PARTY", ITEM_ENTITY = "THIRD_PARTY_ITEM";

    private final ThirdPartyRepository parties;
    private final ThirdPartyItemRepository items;
    private final EvidenceFacade evidence;
    private final RegulatoryAuditService trail;

    @Transactional
    public ThirdPartyDetail create(TenantId t, ThirdPartyRequest r, UUID uid, String uname) {
        int months = r.reviewMonths() == 0 ? PrivacyPartyRules.defaultReviewMonths(r.criticality() == null ? Criticality.MEDIUM : r.criticality()) : r.reviewMonths();
        ThirdParty p = guarded(() -> ThirdParty.create(t, String.format("TP-%05d", parties.nextRef()), r.name(), r.partyType(), r.criticality(), r.riskRating(), r.ownerName(), r.supplierRef(),
                r.handlesPersonalInfo(), months, r.notes(), uid, uname));
        parties.save(p);
        trail.record(t.getValue(), ENTITY, p.getId(), "CREATED", p.getReference() + " " + p.getName() + " (" + p.getCriticality() + ")", uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail update(TenantId t, UUID id, ThirdPartyRequest r, UUID uid, String uname) {
        ThirdParty p = load(t, id);
        ThirdPartyStatus before = p.getStatus();
        int months = r.reviewMonths() == 0 ? p.getReviewMonths() : r.reviewMonths();
        guarded(() -> { p.update(r.name(), r.partyType(), r.criticality(), r.riskRating(), r.ownerName(), r.supplierRef(), r.handlesPersonalInfo(), months, r.notes()); return null; });
        parties.save(p);
        trail.record(t.getValue(), ENTITY, id, "UPDATED", before != p.getStatus() ? "Now handles personal information: suspended until an operator agreement is verified" : "Details changed", uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail approve(TenantId t, UUID id, UUID uid, String uname) {
        ThirdParty p = load(t, id);
        if (uid != null && uid.equals(p.getCreatedBy()))
            throw new HandyFlowException("Segregation of duties: you created this record, so you cannot approve it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        guarded(() -> { p.approve(uid, uname, LocalDate.now(), items.findForParty(t, id)); return null; });
        parties.save(p);
        trail.record(t.getValue(), ENTITY, id, "APPROVED", "Next review " + p.getNextReviewDue(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail suspend(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        ThirdParty p = load(t, id);
        guarded(() -> { p.suspend(r.reason()); return null; });
        parties.save(p);
        trail.record(t.getValue(), ENTITY, id, "SUSPENDED", r.reason().trim(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail offboard(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        ThirdParty p = load(t, id);
        guarded(() -> { p.offboard(r.reason()); return null; });
        parties.save(p);
        trail.record(t.getValue(), ENTITY, id, "OFFBOARDED", r.reason().trim(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail review(TenantId t, UUID id, ThirdPartyReviewRequest r, UUID uid, String uname) {
        ThirdParty p = load(t, id);
        guarded(() -> { p.recordReview(LocalDate.now(), r.riskRating(), r.note()); return null; });
        parties.save(p);
        trail.record(t.getValue(), ENTITY, id, "REVIEWED", "Next review " + p.getNextReviewDue() + ": " + r.note().trim(), uid, uname);
        return detail(t, p);
    }

    // ── Items ──

    @Transactional
    public ThirdPartyDetail addItem(TenantId t, UUID id, ItemRequest r, UUID uid, String uname) {
        ThirdParty p = openParty(t, id);
        ThirdPartyItem i = guarded(() -> ThirdPartyItem.add(t, id, r.kind(), r.title(), r.issuer(), r.referenceNo(), r.required(), r.validTo()));
        items.save(i);
        trail.record(t.getValue(), ENTITY, id, "ITEM_ADDED", i.getKind() + " " + i.getTitle(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail updateItem(TenantId t, UUID id, UUID itemId, ItemRequest r, UUID uid, String uname) {
        ThirdParty p = openParty(t, id);
        ThirdPartyItem i = loadItem(t, id, itemId);
        guarded(() -> { i.update(r.kind(), r.title(), r.issuer(), r.referenceNo(), r.required(), r.validTo()); return null; });
        items.save(i);
        trail.record(t.getValue(), ENTITY, id, "ITEM_UPDATED", i.getTitle() + " sent back to pending", uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail verifyItem(TenantId t, UUID id, UUID itemId, UUID uid, String uname) {
        ThirdParty p = openParty(t, id);
        ThirdPartyItem i = loadItem(t, id, itemId);
        guarded(() -> { i.verify(uname, LocalDate.now()); return null; });
        items.save(i);
        trail.record(t.getValue(), ENTITY, id, "ITEM_VERIFIED", i.getTitle() + (i.getValidTo() == null ? "" : ", valid to " + i.getValidTo()) + ", " + evidenceCount(t, itemId) + " file(s) attached", uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail rejectItem(TenantId t, UUID id, UUID itemId, ReasonRequest r, UUID uid, String uname) {
        ThirdParty p = openParty(t, id);
        ThirdPartyItem i = loadItem(t, id, itemId);
        guarded(() -> { i.reject(r.reason()); return null; });
        items.save(i);
        trail.record(t.getValue(), ENTITY, id, "ITEM_REJECTED", i.getTitle() + ": " + r.reason().trim(), uid, uname);
        return detail(t, p);
    }

    @Transactional
    public ThirdPartyDetail removeItem(TenantId t, UUID id, UUID itemId, UUID uid, String uname) {
        ThirdParty p = openParty(t, id);
        ThirdPartyItem i = loadItem(t, id, itemId);
        items.delete(i);
        trail.record(t.getValue(), ENTITY, id, "ITEM_REMOVED", i.getTitle(), uid, uname);
        return detail(t, p);
    }

    // ── Reads ──

    @Transactional(readOnly = true)
    public List<ThirdPartyResponse> list(TenantId t, ThirdPartyStatus status) {
        List<ThirdParty> ps = parties.search(t, status);
        var byParty = ps.isEmpty() ? java.util.Map.<UUID, List<ThirdPartyItem>>of()
                : items.findForParties(t, ps.stream().map(ThirdParty::getId).toList()).stream().collect(java.util.stream.Collectors.groupingBy(ThirdPartyItem::getThirdPartyId));
        return ps.stream().map(p -> view(p, byParty.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public ThirdPartyDetail get(TenantId t, UUID id) { return detail(t, load(t, id)); }

    @Transactional(readOnly = true)
    public ThirdPartySummary summary(TenantId t) {
        List<ThirdPartyResponse> all = list(t, null);
        return new ThirdPartySummary(all.stream().filter(p -> p.status() == ThirdPartyStatus.ONBOARDING).count(), all.stream().filter(p -> p.status() == ThirdPartyStatus.APPROVED).count(),
                all.stream().filter(p -> p.status() == ThirdPartyStatus.SUSPENDED).count(),
                all.stream().filter(p -> p.status() == ThirdPartyStatus.APPROVED && p.reviewOverdue()).count(),
                all.stream().mapToLong(p -> p.status() == ThirdPartyStatus.OFFBOARDED ? 0 : p.expiringCount()).sum(),
                all.stream().mapToLong(p -> p.status() == ThirdPartyStatus.OFFBOARDED ? 0 : p.expiredCount()).sum());
    }

    // ── internals ──

    /** Files currently attached to one item. */
    @Transactional(readOnly = true)
    public List<za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.EvidenceRef> itemFiles(TenantId t, UUID id, UUID itemId) {
        loadItem(t, id, itemId);
        return evidence.listFor(t, SOURCE_MODULE, ITEM_ENTITY, itemId).stream()
                .map(e -> new za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.EvidenceRef(e.id(), e.fileName(), e.fileSizeBytes(), e.uploadedByName())).toList();
    }

    private ThirdParty load(TenantId t, UUID id) { return parties.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Third party", id.toString())); }

    private ThirdParty openParty(TenantId t, UUID id) {
        ThirdParty p = load(t, id);
        if (p.getStatus() == ThirdPartyStatus.OFFBOARDED) throw new HandyFlowException("An offboarded third party cannot be changed", HttpStatus.CONFLICT, "INVALID_STATE");
        return p;
    }

    private ThirdPartyItem loadItem(TenantId t, UUID partyId, UUID itemId) {
        ThirdPartyItem i = items.findForTenant(t, itemId).orElseThrow(() -> new ResourceNotFoundException("Item", itemId.toString()));
        if (!i.getThirdPartyId().equals(partyId)) throw new ResourceNotFoundException("Item", itemId.toString());
        return i;
    }

    private int evidenceCount(TenantId t, UUID itemId) { return evidence.listFor(t, SOURCE_MODULE, ITEM_ENTITY, itemId).size(); }

    private ThirdPartyDetail detail(TenantId t, ThirdParty p) {
        List<ThirdPartyItem> is = items.findForParty(t, p.getId());
        LocalDate today = LocalDate.now();
        return new ThirdPartyDetail(view(p, is), is.stream().map(i -> new ItemResponse(i.getId(), i.getKind(), i.getTitle(), i.getIssuer(), i.getReferenceNo(), i.isRequired(), i.getStatus(), i.getValidTo(),
                PrivacyPartyRules.expiry(i.getValidTo(), today).name(), i.getVerifiedByName(), i.getVerifiedOn(), i.getNote(), evidenceCount(t, i.getId()))).toList());
    }

    private ThirdPartyResponse view(ThirdParty p, List<ThirdPartyItem> is) {
        LocalDate today = LocalDate.now();
        boolean live = p.getStatus() != ThirdPartyStatus.OFFBOARDED;
        long expiring = is.stream().filter(i -> i.getStatus() == ItemStatus.VERIFIED && PrivacyPartyRules.expiry(i.getValidTo(), today) == PrivacyPartyRules.Expiry.EXPIRING).count();
        long expired = is.stream().filter(i -> i.getStatus() == ItemStatus.VERIFIED && PrivacyPartyRules.expiry(i.getValidTo(), today) == PrivacyPartyRules.Expiry.EXPIRED).count();
        List<String> blockers = p.getStatus() == ThirdPartyStatus.ONBOARDING || p.getStatus() == ThirdPartyStatus.SUSPENDED ? p.blockers(is, today) : List.of();
        return new ThirdPartyResponse(p.getId(), p.getReference(), p.getName(), p.getPartyType(), p.getCriticality(), p.getRiskRating(), p.getStatus(), p.getOwnerName(), p.getSupplierRef(),
                p.isHandlesPersonalInfo(), p.getReviewMonths(), p.getLastReviewedOn(), p.getNextReviewDue(), live && PrivacyPartyRules.reviewOverdue(p.getNextReviewDue(), today), p.getApprovedByName(),
                p.getApprovedOn(), p.getNotes(), p.getCreatedByName(), blockers, is.size(), (int) expiring, (int) expired);
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
