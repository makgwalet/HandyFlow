package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.*;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Client due diligence: prepare, submit, have a second person approve, review on the date the risk rating sets. Nothing here deletes a record.
 * Whether matters and trust receipts are blocked until a client is verified is the firm's written decision ({@link #updateSettings}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpKycService implements LpKycGate {

    private final LpKycProfileRepository profileRepo;
    private final LpKycPartyRepository partyRepo;
    private final LpKycItemRepository itemRepo;
    private final LpKycSettingChangeRepository settingRepo;
    private final LpClientRepository clientRepo;
    private final LpTrustSettingsService trustSettings;
    private final LpWallGuard walls;

    // ---- the gate ------------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public void requireCleared(TenantId tenantId, UUID clientId, String action) {
        if (!enforced(tenantId)) return;
        LocalDate today = LocalDate.now();
        if (profileRepo.findForClient(tenantId, clientId).stream().anyMatch(p -> p.currentOn(today))) return;
        throw new IllegalStateException("This client's due diligence is not verified, or is past its review date, so you cannot " + action
                + ". Complete it under Client due diligence first.");
    }

    @Transactional(readOnly = true)
    public boolean enforced(TenantId tenantId) {
        return settingRepo.current(tenantId).map(LpKycSettingChange::isEnforce).orElse(false);
    }

    @Transactional(readOnly = true)
    public KycSettingsResponse settings(TenantId tenantId) {
        var history = settingRepo.latest(tenantId, PageRequest.of(0, 20)).stream()
                .map(c -> new KycSettingsResponse.Change(c.isEnforce(), c.getReason(), c.getChangedByName(), c.getChangedAt())).toList();
        return new KycSettingsResponse(enforced(tenantId), history);
    }

    @Transactional
    public KycSettingsResponse updateSettings(TenantId tenantId, boolean enforce, String reason, UUID by, String byName) {
        if (enforce == enforced(tenantId)) throw new IllegalStateException("That is already the current setting");
        settingRepo.save(LpKycSettingChange.record(tenantId, enforce, reason, by, byName));
        return settings(tenantId);
    }

    // ---- overview and reading ------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<KycOverviewRow> overview(TenantId tenantId, Pageable pageable) {
        var barred = walls.barred(tenantId);
        Page<LpClient> clients = clientRepo.findAllActiveExcluding(tenantId, barred.clientIds(), pageable);
        Map<UUID, List<LpKycProfile>> byClient = new HashMap<>();
        List<UUID> ids = clients.getContent().stream().map(LpClient::getId).toList();
        if (!ids.isEmpty()) profileRepo.findForClients(tenantId, ids).forEach(p -> byClient.computeIfAbsent(p.getClientId(), k -> new ArrayList<>()).add(p));
        LocalDate today = LocalDate.now();
        return clients.map(c -> row(c, byClient.getOrDefault(c.getId(), List.of()), today));
    }

    private static KycOverviewRow row(LpClient c, List<LpKycProfile> profiles, LocalDate today) {
        LpKycProfile open = profiles.stream().filter(p -> !"VERIFIED".equals(p.getStatus())).findFirst().orElse(null);
        LpKycProfile verified = profiles.stream().filter(p -> "VERIFIED".equals(p.getStatus())).findFirst().orElse(null);
        // a verified profile that has lapsed is shown as expired only when it is the latest word on the client
        String status = KycRules.overviewStatus(verified == null ? null : verified.getValidUntil(), open == null ? null : open.getStatus(), today);
        LpKycProfile shown = open != null && !(verified != null && verified.currentOn(today)) ? open : (verified != null ? verified : open);
        return new KycOverviewRow(c.getId(), c.getName(), c.getClientType(), status, shown == null ? null : shown.getId(),
                shown == null ? null : shown.getRiskRating(), verified == null ? null : verified.getValidUntil(), open != null && verified != null);
    }

    /** Every cycle for one client, newest first. */
    @Transactional(readOnly = true)
    public List<KycProfileResponse> forClient(TenantId tenantId, UUID clientId) {
        walls.requireClientAccess(tenantId, clientId);
        LpClient client = findClient(tenantId, clientId);
        return profileRepo.findForClient(tenantId, clientId).stream().map(p -> toResponse(p, client.getName())).toList();
    }

    @Transactional(readOnly = true)
    public KycProfileResponse get(TenantId tenantId, UUID id) {
        LpKycProfile p = find(tenantId, id);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    // ---- preparing -----------------------------------------------------------------------------------------------

    @Transactional
    public KycProfileResponse start(TenantId tenantId, StartKycRequest req, UUID by, String byName) {
        walls.requireClientAccess(tenantId, req.clientId());
        LpClient client = findClient(tenantId, req.clientId());
        List<LpKycProfile> existing = profileRepo.findForClient(tenantId, client.getId());
        if (existing.stream().anyMatch(p -> !"VERIFIED".equals(p.getStatus()))) throw new IllegalStateException("There is already a due diligence in progress for this client");
        if (existing.stream().anyMatch(p -> p.currentOn(LocalDate.now()))) throw new IllegalStateException("This client is verified and not yet due for review. Open a review instead");
        if (!existing.isEmpty()) return openReviewFrom(tenantId, existing.get(0), client, by, byName);
        if ("INDIVIDUAL".equals(client.getClientType()) && !"INDIVIDUAL".equals(req.subjectKind())) throw new IllegalArgumentException("An individual client is verified as an individual");
        if ("ENTITY".equals(client.getClientType()) && "INDIVIDUAL".equals(req.subjectKind())) throw new IllegalArgumentException("Say what kind of entity this is: company, trust or partnership");
        LpKycProfile p = LpKycProfile.start(tenantId, client.getId(), req.subjectKind(), by, byName, Instant.now());
        profileRepo.save(p);
        return toResponse(p, client.getName());
    }

    /** A review of a verified client (any time, including before it is due): a fresh cycle starting from the last one, with every check to be done again. */
    @Transactional
    public KycProfileResponse openReview(TenantId tenantId, UUID clientId, UUID by, String byName) {
        walls.requireClientAccess(tenantId, clientId);
        LpClient client = findClient(tenantId, clientId);
        List<LpKycProfile> existing = profileRepo.findForClient(tenantId, clientId);
        if (existing.isEmpty()) throw new IllegalStateException("Start due diligence for this client first");
        if (existing.stream().anyMatch(p -> !"VERIFIED".equals(p.getStatus()))) throw new IllegalStateException("There is already a due diligence in progress for this client");
        return openReviewFrom(tenantId, existing.get(0), client, by, byName);
    }

    private KycProfileResponse openReviewFrom(TenantId tenantId, LpKycProfile last, LpClient client, UUID by, String byName) {
        LpKycProfile next = last.reviewCopy(by, byName, Instant.now());
        profileRepo.save(next);
        for (LpKycParty party : partyRepo.findForProfile(tenantId, last.getId())) partyRepo.save(party.reviewCopy(next.getId()));
        return toResponse(next, client.getName());
    }

    @Transactional
    public KycProfileResponse assess(TenantId tenantId, UUID id, KycAssessmentRequest r) {
        LpKycProfile p = findOwn(tenantId, id);
        notFuture(r.pepCheckedOn(), "The date the politically exposed check was done");
        notFuture(r.sanctionsCheckedOn(), "The date of the sanctions screening");
        p.assess(r.geography(), r.service(), r.client(), r.channel(), r.pep(), r.pepCheckedOn(), r.pepNote(), r.sanctionsCheckedOn(), r.sanctionsResult(),
                r.businessNature(), r.sourceOfFunds(), r.sourceOfWealth());
        profileRepo.save(p);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    @Transactional
    public KycProfileResponse addParty(TenantId tenantId, UUID id, KycPartyRequest r, UUID by, String byName) {
        LpKycProfile p = findOwn(tenantId, id);
        requireDraft(p);
        notFuture(r.verifiedOn(), "The verification date");
        partyRepo.save(LpKycParty.create(tenantId, p.getId(), r.role(), r.name(), r.idNumber(), r.percent(), r.method(), r.pep(), r.verifiedOn(), by, byName, r.note()));
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    @Transactional
    public KycProfileResponse verifyParty(TenantId tenantId, UUID id, UUID partyId, LocalDate on, UUID by, String byName) {
        LpKycProfile p = findOwn(tenantId, id);
        requireDraft(p);
        notFuture(on, "The verification date");
        LpKycParty party = ownParty(tenantId, p, partyId);
        party.verify(on, by, byName);
        partyRepo.save(party);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    @Transactional
    public KycProfileResponse removeParty(TenantId tenantId, UUID id, UUID partyId) {
        LpKycProfile p = findOwn(tenantId, id);
        requireDraft(p);
        LpKycParty party = ownParty(tenantId, p, partyId);
        if (itemRepo.findForProfile(tenantId, p.getId()).stream().anyMatch(i -> partyId.equals(i.getPartyId()))) {
            throw new IllegalStateException("Documents are recorded against this person. Remove them first");
        }
        partyRepo.delete(party);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    @Transactional
    public KycProfileResponse addItem(TenantId tenantId, UUID id, KycItemRequest r, UUID by, String byName) {
        LpKycProfile p = findOwn(tenantId, id);
        requireDraft(p);
        notFuture(r.verifiedOn(), "The verification date");
        if (r.partyId() != null) ownParty(tenantId, p, r.partyId());
        itemRepo.save(LpKycItem.create(tenantId, p.getId(), r.kind(), r.partyId(), r.description(), r.documentRef(), r.evidenceId(), r.verifiedOn(), by, byName));
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    @Transactional
    public KycProfileResponse removeItem(TenantId tenantId, UUID id, UUID itemId) {
        LpKycProfile p = findOwn(tenantId, id);
        requireDraft(p);
        LpKycItem item = itemRepo.findActiveById(tenantId, itemId).filter(i -> i.getProfileId().equals(p.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("LpKycItem", itemId.toString()));
        itemRepo.delete(item);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    // ---- submitting and approving ------------------------------------------------------------------------------------

    @Transactional
    public KycProfileResponse submit(TenantId tenantId, UUID id, UUID by, String byName) {
        LpKycProfile p = findOwn(tenantId, id);
        p.submit(by, byName, missingFor(tenantId, p), Instant.now());
        profileRepo.save(p);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    @Transactional
    public KycProfileResponse returnToDraft(TenantId tenantId, UUID id, String reason) {
        LpKycProfile p = findOwn(tenantId, id);
        p.returnToDraft(reason);
        profileRepo.save(p);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    /** @param senior whether the approver holds the senior-management approval permission */
    @Transactional
    public KycProfileResponse approve(TenantId tenantId, UUID id, String note, boolean senior, UUID by, String byName) {
        LpKycProfile p = findOwn(tenantId, id);
        p.approve(by, byName, note, senior, trustSettings.selfApprovalAllowed(tenantId), missingFor(tenantId, p), LocalDate.now(), Instant.now());
        profileRepo.save(p);
        log.info("Due diligence verified profile={} client={} risk={} valid until {} tenant={}", p.getId(), p.getClientId(), p.getRiskRating(), p.getValidUntil(), tenantId);
        return toResponse(p, findClient(tenantId, p.getClientId()).getName());
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private List<String> missingFor(TenantId tenantId, LpKycProfile p) {
        List<KycRules.Party> parties = partyRepo.findForProfile(tenantId, p.getId()).stream()
                .map(x -> new KycRules.Party(x.getRole(), x.getName(), x.getOwnershipPercent(), x.getOwnerMethod(), x.verified())).toList();
        List<KycRules.Item> items = itemRepo.findForProfile(tenantId, p.getId()).stream().map(i -> new KycRules.Item(i.getKind(), null)).toList();
        return KycRules.missing(p.assessment(), parties, items);
    }

    private LpKycProfile find(TenantId tenantId, UUID id) {
        LpKycProfile p = profileRepo.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("LpKycProfile", id.toString()));
        walls.requireClientAccess(tenantId, p.getClientId());
        return p;
    }

    private LpKycProfile findOwn(TenantId tenantId, UUID id) { return find(tenantId, id); }

    private LpClient findClient(TenantId tenantId, UUID clientId) {
        return clientRepo.findActiveById(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("LpClient", clientId.toString()));
    }

    private LpKycParty ownParty(TenantId tenantId, LpKycProfile p, UUID partyId) {
        return partyRepo.findActiveById(tenantId, partyId).filter(x -> x.getProfileId().equals(p.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("LpKycParty", partyId.toString()));
    }

    private static void requireDraft(LpKycProfile p) {
        if (!p.draft()) throw new IllegalStateException("Only a draft can be changed");
    }

    private static void notFuture(LocalDate d, String what) {
        if (d != null && d.isAfter(LocalDate.now())) throw new IllegalArgumentException(what + " cannot be in the future");
    }

    private KycProfileResponse toResponse(LpKycProfile p, String clientName) {
        TenantId t = p.getTenantId();
        List<LpKycParty> parties = partyRepo.findForProfile(t, p.getId());
        List<LpKycItem> items = itemRepo.findForProfile(t, p.getId());
        List<String> missing = p.draft() ? missingFor(t, p) : List.of();
        return new KycProfileResponse(p.getId(), p.getClientId(), clientName, p.getSubjectKind(), p.getStatus(),
                p.getRiskGeography(), p.getRiskService(), p.getRiskClient(), p.getRiskChannel(), p.getRiskRating(), KycRules.levelOf(p.getRiskRating()), KycRules.reviewMonths(p.getRiskRating()),
                p.getPepStatus(), p.getPepCheckedOn(), p.getPepNote(), p.getSanctionsCheckedOn(), p.getSanctionsResult(),
                p.getBusinessNature(), p.getSourceOfFunds(), p.getSourceOfWealth(),
                p.getStartedByName(), p.getStartedAt(), p.getSubmittedBy(), p.getSubmittedByName(), p.getSubmittedAt(), p.getReturnReason(),
                p.getApprovedByName(), p.getApprovedAt(), p.getApprovalNote(), p.isSelfApproved(), p.isSeniorApproved(), p.getValidUntil(),
                KycRules.needsSenior(p.getRiskRating(), p.getPepStatus()), missing,
                parties.stream().map(x -> new KycProfileResponse.Party(x.getId(), x.getRole(), x.getName(), x.getIdNumber(), x.getOwnershipPercent(), x.getOwnerMethod(), x.getPepStatus(),
                        x.getVerifiedOn(), x.getVerifiedByName(), x.getNote())).toList(),
                items.stream().map(i -> new KycProfileResponse.Item(i.getId(), i.getKind(), i.getPartyId(), i.getDescription(), i.getDocumentRef(), i.getEvidenceId(),
                        i.getVerifiedOn(), i.getVerifiedByName())).toList());
    }
}
