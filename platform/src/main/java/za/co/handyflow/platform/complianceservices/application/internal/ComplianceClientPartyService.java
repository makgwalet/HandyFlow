package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.ClientPartyRules;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientParty;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientSite;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientPartyRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientSiteRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientPartyDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The people behind a client and the sites it runs. */
@Service
@RequiredArgsConstructor
public class ComplianceClientPartyService {

    private final ComplianceClientRepository clientRepository;
    private final ComplianceClientPartyRepository partyRepository;
    private final ComplianceClientSiteRepository siteRepository;

    // ---- parties ----

    @Transactional(readOnly = true)
    public PartiesResponse parties(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return build(partyRepository.findForClient(tenantId, clientId));
    }

    @Transactional
    public PartiesResponse addParty(TenantId tenantId, UUID clientId, SavePartyRequest r, UUID by) {
        requireClient(tenantId, clientId);
        ComplianceClientParty p;
        try {
            p = ComplianceClientParty.create(tenantId, clientId, details(r), by);
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        List<ComplianceClientParty> all = partyRepository.findForClient(tenantId, clientId);
        checkRules(p, all);
        partyRepository.save(p);
        return build(partyRepository.findForClient(tenantId, clientId));
    }

    @Transactional
    public PartiesResponse updateParty(TenantId tenantId, UUID clientId, UUID id, SavePartyRequest r, UUID by) {
        ComplianceClientParty p = findParty(tenantId, clientId, id);
        try {
            p.apply(details(r), by);
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        checkRules(p, partyRepository.findForClient(tenantId, clientId));
        partyRepository.save(p);
        return build(partyRepository.findForClient(tenantId, clientId));
    }

    @Transactional
    public PartiesResponse resign(TenantId tenantId, UUID clientId, UUID id, LocalDate on, UUID by) {
        ComplianceClientParty p = findParty(tenantId, clientId, id);
        try {
            p.resign(on, by);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CLIENT_PARTY_ALREADY_RESIGNED");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        partyRepository.save(p);
        return build(partyRepository.findForClient(tenantId, clientId));
    }

    /** An active holder cannot appear twice in one role, and active shareholdings cannot add up to more than 100%. */
    static void checkRules(ComplianceClientParty p, List<ComplianceClientParty> all) {
        if (!p.isActive()) return;
        List<ComplianceClientParty> others = all.stream().filter(o -> !o.getId().equals(p.getId()) && o.isActive()).toList();
        if (p.getIdNumber() != null && others.stream().anyMatch(o -> p.getIdNumber().equals(o.getIdNumber()) && p.getRole().equals(o.getRole()))) {
            throw new HandyFlowException(p.getFullName() + " is already recorded as " + p.getRole().toLowerCase().replace('_', ' ') + " for this client",
                    HttpStatus.CONFLICT, "CLIENT_PARTY_DUPLICATE");
        }
        if ("SHAREHOLDER".equals(p.getRole()) && p.getOwnershipPercent() != null) {
            BigDecimal other = others.stream().filter(o -> "SHAREHOLDER".equals(o.getRole()) && o.getOwnershipPercent() != null)
                    .map(ComplianceClientParty::getOwnershipPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (ClientPartyRules.exceedsWhole(other, p.getOwnershipPercent())) {
                throw new HandyFlowException("Shareholdings would add up to more than 100% (others already hold " + other.stripTrailingZeros().toPlainString() + "%)",
                        HttpStatus.CONFLICT, "CLIENT_SHAREHOLDING_OVER_100");
            }
        }
    }

    private static ComplianceClientParty.Details details(SavePartyRequest r) {
        return new ComplianceClientParty.Details(r.fullName(), r.partyType(), r.idType(), r.idNumber(), r.role(),
                r.ownershipPercent(), r.appointedOn(), r.resignedOn(), r.notes());
    }

    private PartiesResponse build(List<ComplianceClientParty> all) {
        var s = ClientPartyRules.summarise(all.stream()
                .map(p -> new ClientPartyRules.Holding(p.getRole(), p.getOwnershipPercent(), p.isActive())).toList());
        return new PartiesResponse(all.stream().map(p -> new PartyResponse(p.getId(), p.getFullName(), p.getPartyType(), p.getIdType(),
                p.getIdNumber(), p.getRole(), p.getOwnershipPercent(), p.getAppointedOn(), p.getResignedOn(), p.isActive(),
                p.getNotes(), p.getCreatedAt())).toList(),
                new OwnershipView(s.shareholdingDeclared(), s.fullyDeclared(), s.hasDirector(), s.hasBeneficialOwner(),
                        s.beneficialOwnerGap(), s.directors()));
    }

    // ---- sites ----

    @Transactional(readOnly = true)
    public List<SiteResponse> sites(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return siteRepository.findForClient(tenantId, clientId).stream().map(ComplianceClientPartyService::toSite).toList();
    }

    @Transactional
    public List<SiteResponse> addSite(TenantId tenantId, UUID clientId, SaveSiteRequest r, UUID by) {
        requireClient(tenantId, clientId);
        ComplianceClientSite s;
        try {
            s = ComplianceClientSite.create(tenantId, clientId, r.name(), r.siteType(), r.address(), r.employeeCount(), by);
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        requireSingleHeadOffice(s, siteRepository.findForClient(tenantId, clientId));
        siteRepository.save(s);
        return sites(tenantId, clientId);
    }

    @Transactional
    public List<SiteResponse> updateSite(TenantId tenantId, UUID clientId, UUID id, SaveSiteRequest r, UUID by) {
        ComplianceClientSite s = findSite(tenantId, clientId, id);
        try {
            s.apply(r.name(), r.siteType(), r.address(), r.employeeCount(), by);
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        requireSingleHeadOffice(s, siteRepository.findForClient(tenantId, clientId));
        siteRepository.save(s);
        return sites(tenantId, clientId);
    }

    @Transactional
    public List<SiteResponse> setSiteActive(TenantId tenantId, UUID clientId, UUID id, boolean active, UUID by) {
        ComplianceClientSite s = findSite(tenantId, clientId, id);
        s.setActive(active, by);
        requireSingleHeadOffice(s, siteRepository.findForClient(tenantId, clientId));
        siteRepository.save(s);
        return sites(tenantId, clientId);
    }

    static void requireSingleHeadOffice(ComplianceClientSite s, List<ComplianceClientSite> all) {
        if (!"HEAD_OFFICE".equals(s.getSiteType()) || !s.isActive()) return;
        if (all.stream().anyMatch(o -> !Objects.equals(o.getId(), s.getId()) && o.isActive() && "HEAD_OFFICE".equals(o.getSiteType()))) {
            throw new HandyFlowException("This client already has a head office. Change that site first.",
                    HttpStatus.CONFLICT, "CLIENT_HEAD_OFFICE_EXISTS");
        }
    }

    private static SiteResponse toSite(ComplianceClientSite s) {
        return new SiteResponse(s.getId(), s.getName(), s.getSiteType(), s.getAddress(), s.getEmployeeCount(), s.isActive());
    }

    // ---- helpers ----

    private void requireClient(TenantId tenantId, UUID clientId) {
        clientRepository.findByIdForTenant(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceClientParty findParty(TenantId tenantId, UUID clientId, UUID id) {
        return partyRepository.findByIdForClient(tenantId, clientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClientParty", id.toString()));
    }

    private ComplianceClientSite findSite(TenantId tenantId, UUID clientId, UUID id) {
        return siteRepository.findByIdForClient(tenantId, clientId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceClientSite", id.toString()));
    }

    private static HandyFlowException bad(IllegalArgumentException e) {
        return new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "CLIENT_PARTY_INVALID");
    }
}
