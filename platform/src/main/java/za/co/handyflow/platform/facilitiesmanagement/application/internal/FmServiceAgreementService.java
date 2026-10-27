package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSlaTarget;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmServiceAgreementRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service agreements as contracts (see {@link FmServiceAgreement}). No delete — an agreement is a billing/legal
 * record, only ever ended. Limited to the caller's client scope; every create, change and end is audited with the
 * commercial terms before and after.
 * <p>
 * Overlap rule: a client may have several ACTIVE agreements, but two may not cover the same sites AND the same
 * categories in overlapping dates — otherwise a work order could resolve to two contracts. (Agreements that already
 * overlap from before this rule are left alone; they are only re-checked if their coverage or end date changes.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmServiceAgreementService {

    private static final LocalDate OPEN_ENDED = LocalDate.of(9999, 12, 31);

    private final FmServiceAgreementRepository agreementRepository;
    private final FmClientRepository clientRepository;
    private final FmSiteRepository siteRepository;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;
    private final FmContractTermsService contractTermsService;

    @Transactional(readOnly = true)
    public Page<FmServiceAgreementResponse> getAgreements(TenantId tenantId, UUID clientId, Pageable pageable) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        return agreementRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmServiceAgreementResponse getAgreement(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id, accessPolicy.scope(tenantId)));
    }

    /** "What terms would work here fall under?" — the same resolution work orders use. */
    @Transactional(readOnly = true)
    public FmResolvedTermsResponse previewTerms(TenantId tenantId, UUID clientId, UUID siteId, String category, LocalDate date) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        siteRepository.findActiveByIdForClient(tenantId, clientId, siteId)
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", siteId.toString()));
        String normalizedCategory = category == null ? "" : category.trim().toUpperCase();
        if (!FmServiceAgreement.CATEGORIES.contains(normalizedCategory)) {
            throw new IllegalArgumentException("category must be one of " + FmServiceAgreement.CATEGORIES);
        }
        LocalDate onDate = date != null ? date : FmTime.today();
        return contractTermsService.resolveAgreement(tenantId, clientId, siteId, normalizedCategory, onDate)
                .map(a -> new FmResolvedTermsResponse(true, a.getId(), a.getName(), a.getBillingType(),
                        a.isRetainer() ? "INCLUDED" : "CHARGEABLE", a.getHourlyRate(), a.getPartsMarkupPercent(), targetDtos(a)))
                .orElseGet(() -> new FmResolvedTermsResponse(false, null, null, null, "CHARGEABLE", null, null, List.of()));
    }

    @Transactional
    public FmServiceAgreementResponse createAgreement(TenantId tenantId, CreateFmServiceAgreementRequest req) {
        accessPolicy.scope(tenantId).requireClient(req.clientId());
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", req.clientId().toString()));
        requireSitesBelongToClient(tenantId, req.clientId(), req.siteIds());

        FmServiceAgreement agreement = FmServiceAgreement.create(tenantId, req.clientId(), req.name(), req.billingType(),
                req.monthlyFee(), req.hourlyRate(), req.partsMarkupPercent(), req.startDate(), req.endDate(),
                req.siteIds(), req.categories(), toTargets(req.slaTargets()));
        requireNoConflict(tenantId, agreement);
        agreementRepository.save(agreement);

        auditService.record(tenantId, "AGREEMENT_CREATED", "AGREEMENT", agreement.getId(),
                "client=" + req.clientId() + " " + describe(agreement));
        log.info("FM service agreement created id={} client={} tenant={}", agreement.getId(), req.clientId(), tenantId);
        return toResponse(agreement);
    }

    @Transactional
    public FmServiceAgreementResponse updateAgreement(TenantId tenantId, UUID id, UpdateFmServiceAgreementRequest req) {
        FmServiceAgreement agreement = findActive(tenantId, id, accessPolicy.scope(tenantId));
        if (req.siteIds() != null) requireSitesBelongToClient(tenantId, agreement.getClientId(), req.siteIds());

        String before = describe(agreement);
        boolean affectsCoverage = req.siteIds() != null || req.categories() != null
                || !Objects.equals(req.endDate(), agreement.getEndDate());

        agreement.update(req.name(), req.monthlyFee(), req.hourlyRate(), req.partsMarkupPercent(), req.endDate(),
                req.siteIds(), req.categories(), req.slaTargets() != null ? toTargets(req.slaTargets()) : null);
        if (affectsCoverage) requireNoConflict(tenantId, agreement);
        agreementRepository.save(agreement);

        auditService.record(tenantId, "AGREEMENT_UPDATED", "AGREEMENT", id, before + " -> " + describe(agreement));
        return toResponse(agreement);
    }

    @Transactional
    public FmServiceAgreementResponse end(TenantId tenantId, UUID id) {
        FmServiceAgreement agreement = findActive(tenantId, id, accessPolicy.scope(tenantId));
        agreement.end();
        agreementRepository.save(agreement);
        auditService.record(tenantId, "AGREEMENT_ENDED", "AGREEMENT", id,
                "client=" + agreement.getClientId() + " type=" + agreement.getBillingType() + " end=" + agreement.getEndDate());
        log.info("FM service agreement ended id={} tenant={}", id, tenantId);
        return toResponse(agreement);
    }

    FmServiceAgreement findActive(TenantId tenantId, UUID id, FmAccessScope scope) {
        FmServiceAgreement agreement = agreementRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmServiceAgreement", id.toString()));
        if (!scope.canSeeClient(agreement.getClientId())) {
            throw new ResourceNotFoundException("FmServiceAgreement", id.toString());
        }
        return agreement;
    }

    // ── Validation ───────────────────────────────────────────────────────────

    private void requireSitesBelongToClient(TenantId tenantId, UUID clientId, Set<UUID> siteIds) {
        if (siteIds == null) return;
        for (UUID siteId : siteIds) {
            siteRepository.findActiveByIdForClient(tenantId, clientId, siteId)
                    .orElseThrow(() -> new ResourceNotFoundException("FmSite", siteId.toString()));
        }
    }

    /** Reject an agreement that would cover the same sites and categories as another active one in overlapping dates. */
    private void requireNoConflict(TenantId tenantId, FmServiceAgreement a) {
        LocalDate end = a.getEndDate() != null ? a.getEndDate() : OPEN_ENDED;
        for (FmServiceAgreement other : agreementRepository.findActiveOverlapping(
                tenantId, a.getClientId(), a.getStartDate(), end, a.getId())) {
            if (sitesOverlap(a, other) && categoriesOverlap(a, other)) {
                throw new IllegalStateException("This agreement overlaps '" + (other.getName() != null ? other.getName() : other.getId())
                        + "' (" + other.getStartDate() + " to " + (other.getEndDate() != null ? other.getEndDate() : "open")
                        + "): both would cover the same sites and categories at the same time. "
                        + "Narrow the sites or categories, or change the dates.");
            }
        }
    }

    private boolean sitesOverlap(FmServiceAgreement a, FmServiceAgreement b) {
        if (a.getSiteIds().isEmpty() || b.getSiteIds().isEmpty()) return true; // empty = all sites
        return a.getSiteIds().stream().anyMatch(b.getSiteIds()::contains);
    }

    private boolean categoriesOverlap(FmServiceAgreement a, FmServiceAgreement b) {
        Set<String> ca = a.categorySet();
        Set<String> cb = b.categorySet();
        if (ca.isEmpty() || cb.isEmpty()) return true; // empty = all categories
        return ca.stream().anyMatch(cb::contains);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private List<FmSlaTarget> toTargets(List<FmSlaTargetDto> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(d -> FmSlaTarget.of(d.priority(), d.responseMinutes(), d.resolutionMinutes(), d.clock())).toList();
    }

    private List<FmSlaTargetDto> targetDtos(FmServiceAgreement a) {
        return a.getSlaTargets().stream()
                .sorted(Comparator.comparingInt(t -> FmServiceAgreement.PRIORITIES.indexOf(t.getPriority())))
                .map(t -> new FmSlaTargetDto(t.getPriority(), t.getResponseMinutes(), t.getResolutionMinutes(), t.getClock()))
                .toList();
    }

    private String describe(FmServiceAgreement a) {
        String sla = a.getSlaTargets().stream()
                .sorted(Comparator.comparingInt(t -> FmServiceAgreement.PRIORITIES.indexOf(t.getPriority())))
                .map(t -> t.getPriority() + ":" + t.getResponseMinutes() + "/" + t.getResolutionMinutes() + " " + t.getClock())
                .collect(Collectors.joining("; "));
        return "name=" + a.getName() + " type=" + a.getBillingType() + " monthlyFee=" + a.getMonthlyFee()
                + " hourlyRate=" + a.getHourlyRate() + " partsMarkup=" + a.getPartsMarkupPercent()
                + " sites=" + (a.getSiteIds().isEmpty() ? "ALL" : a.getSiteIds().size())
                + " categories=" + (a.categorySet().isEmpty() ? "ALL" : String.join(",", a.categorySet()))
                + " sla=[" + sla + "] period=" + a.getStartDate() + ".." + a.getEndDate();
    }

    private FmServiceAgreementResponse toResponse(FmServiceAgreement a) {
        return new FmServiceAgreementResponse(a.getId(), a.getClientId(), a.getBillingType(), a.getMonthlyFee(),
                a.getHourlyRate(), a.getStartDate(), a.getEndDate(), a.getStatus(), a.getCreatedAt(),
                a.getName(), a.getPartsMarkupPercent(),
                a.getSiteIds().stream().sorted().toList(), List.copyOf(a.categorySet()), targetDtos(a));
    }
}