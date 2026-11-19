package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.InfoGovDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * FICA / RMCP support. Applies only to a tenant that has declared itself an accountable institution in its FICA profile: until then every party and
 * escalation endpoint answers 409 FICA_NOT_APPLICABLE, so the platform does not push financial-crime duties onto businesses they do not bind.
 *
 * What it does: records the RMCP pointer and review date, tracks customer due diligence (identification, beneficial owners, sanctions screening,
 * senior approval for high-risk parties and PEPs, periodic review) and internal escalations of unusual activity. What it does not do: decide a party's
 * risk rating, screen anyone, or file any report with an authority. The organisation's own RMCP governs. The whole area needs LEGALCOMPLIANCE_FICA_MANAGE:
 * the controller offers no read access to ordinary compliance readers.
 */
@Service
@RequiredArgsConstructor
public class FicaService {

    static final String PROFILE = "FICA_PROFILE", PARTY = "FICA_PARTY", ESCALATION = "FICA_ESCALATION";

    private final FicaProfileRepository profiles;
    private final FicaPartyRepository parties;
    private final FicaBeneficialOwnerRepository owners;
    private final FicaEscalationRepository escalations;
    private final PolicyRepository policies;
    private final RegulatoryAuditService trail;

    // ═══ Profile ═══

    @Transactional(readOnly = true)
    public FicaProfileResponse profile(TenantId t) {
        return profiles.findByTenant(t).map(p -> viewProfile(t, p)).orElse(new FicaProfileResponse(false, false, null, null, null, null, null, null, false, null));
    }

    @Transactional
    public FicaProfileResponse saveProfile(TenantId t, FicaProfileRequest r, UUID uid, String uname) {
        if (r.rmcpPolicyId() != null) policies.findForTenant(t, r.rmcpPolicyId()).orElseThrow(() -> new ResourceNotFoundException("Policy", r.rmcpPolicyId().toString()));
        FicaProfile p = profiles.findByTenant(t).orElseGet(() -> FicaProfile.create(t));
        guarded(() -> { p.update(r.accountableInstitution(), r.basis(), r.complianceOfficerName(), r.rmcpPolicyId(), r.rmcpApprovedOn(), r.rmcpNextReview(), uname); return null; });
        profiles.save(p);
        trail.record(t.getValue(), PROFILE, p.getId(), "PROFILE_SAVED", (r.accountableInstitution() ? "Accountable institution: " : "Not an accountable institution: ") + r.basis().trim(), uid, uname);
        return viewProfile(t, p);
    }

    private FicaProfileResponse viewProfile(TenantId t, FicaProfile p) {
        String policy = p.getRmcpPolicyId() == null ? null : policies.findForTenant(t, p.getRmcpPolicyId()).map(Policy::getTitle).orElse(null);
        return new FicaProfileResponse(true, p.isAccountableInstitution(), p.getBasis(), p.getComplianceOfficerName(), p.getRmcpPolicyId(), policy, p.getRmcpApprovedOn(), p.getRmcpNextReview(),
                p.isAccountableInstitution() && PrivacyPartyRules.reviewOverdue(p.getRmcpNextReview(), LocalDate.now()), p.getUpdatedByName());
    }

    private void requireApplicable(TenantId t) {
        if (profiles.findByTenant(t).map(FicaProfile::isAccountableInstitution).orElse(false) == false)
            throw new HandyFlowException("FICA is not switched on for this organisation. Complete the FICA profile and confirm you are an accountable institution first.", HttpStatus.CONFLICT, "FICA_NOT_APPLICABLE");
    }

    // ═══ Parties ═══

    @Transactional
    public FicaPartyResponse onboard(TenantId t, FicaPartyRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = guarded(() -> FicaParty.onboard(t, String.format("FICA-%05d", parties.nextRef()), r.partyName(), r.partyType(), r.identifierHint(), r.riskRating(), r.pep(), r.notes(), uid, uname));
        parties.save(p);
        trail.record(t.getValue(), PARTY, p.getId(), "ONBOARDED", p.getReference() + " " + p.getPartyType() + " risk " + p.getRiskRating() + (p.isPep() ? ", PEP" : ""), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse update(TenantId t, UUID id, FicaPartyRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = loadParty(t, id);
        guarded(() -> { p.update(r.partyName(), r.partyType(), r.identifierHint(), r.riskRating(), r.pep(), r.notes()); return null; });
        parties.save(p);
        trail.record(t.getValue(), PARTY, id, "UPDATED", "Risk " + p.getRiskRating() + (p.isPep() ? ", PEP" : "") + ", status " + p.getStatus(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse screened(TenantId t, UUID id, ScreenedRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = loadParty(t, id);
        guarded(() -> { p.screenedForSanctions(r.screenedOn(), LocalDate.now()); return null; });
        parties.save(p);
        trail.record(t.getValue(), PARTY, id, "SANCTIONS_SCREENED", "On " + r.screenedOn(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse verify(TenantId t, UUID id, FicaVerifyRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = loadParty(t, id);
        if (uid != null && uid.equals(p.getCreatedBy()))
            throw new HandyFlowException("Segregation of duties: you onboarded this party, so you cannot verify it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        List<FicaBeneficialOwner> os = owners.findForParty(t, id);
        LocalDate today = LocalDate.now();
        guarded(() -> { p.verify(uid, uname, today, os, r == null ? null : r.seniorApprover(), PrivacyPartyRules.ficaNextReview(today, p.getRiskRating(), p.isPep())); return null; });
        parties.save(p);
        trail.record(t.getValue(), PARTY, id, "VERIFIED", "Next review " + p.getNextReviewDue() + (p.getSeniorApprovalName() == null ? "" : ", senior approval " + p.getSeniorApprovalName()), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse reopen(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = loadParty(t, id);
        guarded(() -> { p.reopen(r.reason()); return null; });
        parties.save(p);
        trail.record(t.getValue(), PARTY, id, "REOPENED", r.reason().trim(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse decline(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = loadParty(t, id);
        guarded(() -> { p.decline(r.reason()); return null; });
        parties.save(p);
        trail.record(t.getValue(), PARTY, id, "DECLINED", r.reason().trim(), uid, uname);
        return view(t, p);
    }

    @Transactional(readOnly = true)
    public List<FicaPartyResponse> listParties(TenantId t, FicaPartyStatus status) { requireApplicable(t); return parties.search(t, status).stream().map(p -> view(t, p)).toList(); }

    @Transactional(readOnly = true)
    public FicaPartyResponse getParty(TenantId t, UUID id) { requireApplicable(t); return view(t, loadParty(t, id)); }

    // ── Beneficial owners ──

    @Transactional
    public FicaPartyResponse addOwner(TenantId t, UUID partyId, OwnerRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = editableOwners(t, partyId);
        FicaBeneficialOwner o = guarded(() -> FicaBeneficialOwner.add(t, partyId, r.ownerName(), r.ownershipPercent(), r.controlBasis()));
        owners.save(o);
        trail.record(t.getValue(), PARTY, partyId, "OWNER_ADDED", o.getOwnerName(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse updateOwner(TenantId t, UUID partyId, UUID ownerId, OwnerRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = editableOwners(t, partyId);
        FicaBeneficialOwner o = loadOwner(t, partyId, ownerId);
        guarded(() -> { o.update(r.ownerName(), r.ownershipPercent(), r.controlBasis()); return null; });
        owners.save(o);
        trail.record(t.getValue(), PARTY, partyId, "OWNER_UPDATED", o.getOwnerName(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse verifyOwner(TenantId t, UUID partyId, UUID ownerId, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = editableOwners(t, partyId);
        FicaBeneficialOwner o = loadOwner(t, partyId, ownerId);
        o.markVerified();
        owners.save(o);
        trail.record(t.getValue(), PARTY, partyId, "OWNER_VERIFIED", o.getOwnerName(), uid, uname);
        return view(t, p);
    }

    @Transactional
    public FicaPartyResponse removeOwner(TenantId t, UUID partyId, UUID ownerId, UUID uid, String uname) {
        requireApplicable(t);
        FicaParty p = editableOwners(t, partyId);
        FicaBeneficialOwner o = loadOwner(t, partyId, ownerId);
        owners.delete(o);
        trail.record(t.getValue(), PARTY, partyId, "OWNER_REMOVED", o.getOwnerName(), uid, uname);
        return view(t, p);
    }

    /** Beneficial owners of a verified or declined party are frozen: reopen the party first so the change is reviewed again. */
    private FicaParty editableOwners(TenantId t, UUID partyId) {
        FicaParty p = loadParty(t, partyId);
        if (p.getStatus() == FicaPartyStatus.VERIFIED || p.getStatus() == FicaPartyStatus.DECLINED)
            throw new HandyFlowException("Beneficial owners cannot be changed while the party is " + p.getStatus().name().toLowerCase() + ". Reopen it first.", HttpStatus.CONFLICT, "INVALID_STATE");
        return p;
    }

    private FicaBeneficialOwner loadOwner(TenantId t, UUID partyId, UUID ownerId) {
        FicaBeneficialOwner o = owners.findForTenant(t, ownerId).orElseThrow(() -> new ResourceNotFoundException("Beneficial owner", ownerId.toString()));
        if (!o.getPartyId().equals(partyId)) throw new ResourceNotFoundException("Beneficial owner", ownerId.toString());
        return o;
    }

    // ═══ Escalations ═══

    @Transactional
    public EscalationResponse raise(TenantId t, EscalationRequest r, UUID uid, String uname) {
        requireApplicable(t);
        if (r.partyId() != null) loadParty(t, r.partyId());
        FicaEscalation e = guarded(() -> FicaEscalation.raise(t, String.format("ESC-%05d", escalations.nextRef()), r.partyId(), r.description(), uid, uname));
        escalations.save(e);
        trail.record(t.getValue(), ESCALATION, e.getId(), "RAISED", e.getReference(), uid, uname);
        return view(t, e);
    }

    @Transactional
    public EscalationResponse startReview(TenantId t, UUID id, UUID uid, String uname) {
        requireApplicable(t);
        FicaEscalation e = loadEscalation(t, id);
        guarded(() -> { e.startReview(); return null; });
        escalations.save(e);
        trail.record(t.getValue(), ESCALATION, id, "UNDER_REVIEW", "Review started", uid, uname);
        return view(t, e);
    }

    @Transactional
    public EscalationResponse decide(TenantId t, UUID id, EscalationDecideRequest r, UUID uid, String uname) {
        requireApplicable(t);
        FicaEscalation e = loadEscalation(t, id);
        if (uid != null && uid.equals(e.getRaisedById()))
            throw new HandyFlowException("Segregation of duties: you raised this escalation, so you cannot decide it", HttpStatus.FORBIDDEN, "SOD_VIOLATION");
        guarded(() -> { e.decide(r.decision(), r.note(), r.externalReference(), uid, uname); return null; });
        escalations.save(e);
        trail.record(t.getValue(), ESCALATION, id, "DECIDED", r.decision() + ": " + r.note().trim(), uid, uname);
        return view(t, e);
    }

    @Transactional(readOnly = true)
    public List<EscalationResponse> listEscalations(TenantId t, EscalationStatus status) { requireApplicable(t); return escalations.search(t, status).stream().map(e -> view(t, e)).toList(); }

    @Transactional(readOnly = true)
    public FicaSummary summary(TenantId t) {
        requireApplicable(t);
        List<FicaParty> all = parties.search(t, null);
        LocalDate today = LocalDate.now();
        return new FicaSummary(all.stream().filter(p -> p.getStatus() == FicaPartyStatus.PENDING).count(), all.stream().filter(p -> p.getStatus() == FicaPartyStatus.ENHANCED_REQUIRED).count(),
                all.stream().filter(p -> p.getStatus() == FicaPartyStatus.VERIFIED).count(),
                all.stream().filter(p -> p.getStatus() == FicaPartyStatus.VERIFIED && PrivacyPartyRules.reviewOverdue(p.getNextReviewDue(), today)).count(),
                escalations.search(t, null).stream().filter(e -> e.getStatus() != EscalationStatus.DECIDED).count());
    }

    // ── internals ──

    private FicaParty loadParty(TenantId t, UUID id) { return parties.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("FICA party", id.toString())); }

    private FicaEscalation loadEscalation(TenantId t, UUID id) { return escalations.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Escalation", id.toString())); }

    private FicaPartyResponse view(TenantId t, FicaParty p) {
        return new FicaPartyResponse(p.getId(), p.getReference(), p.getPartyName(), p.getPartyType(), p.getIdentifierHint(), p.getRiskRating(), p.isPep(), p.getStatus(), p.getSanctionsScreenedOn(),
                p.getVerifiedOn(), p.getVerifiedByName(), p.getSeniorApprovalName(), p.getNextReviewDue(),
                p.getStatus() == FicaPartyStatus.VERIFIED && PrivacyPartyRules.reviewOverdue(p.getNextReviewDue(), LocalDate.now()), p.getNotes(), p.getCreatedByName(),
                owners.findForParty(t, p.getId()).stream().map(OwnerResponse::of).toList());
    }

    private EscalationResponse view(TenantId t, FicaEscalation e) {
        String party = e.getPartyId() == null ? null : parties.findForTenant(t, e.getPartyId()).map(p -> p.getReference() + " — " + p.getPartyName()).orElse(null);
        return new EscalationResponse(e.getId(), e.getReference(), e.getPartyId(), party, e.getDescription(), e.getRaisedByName(), e.getStatus(), e.getDecision(), e.getDecisionNote(),
                e.getDecidedByName(), e.getDecidedAt(), e.getExternalReference());
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
