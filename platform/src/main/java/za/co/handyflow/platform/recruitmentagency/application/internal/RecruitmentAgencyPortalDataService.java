package za.co.handyflow.platform.recruitmentagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruitmentagency.domain.model.*;
import za.co.handyflow.platform.recruitmentagency.domain.repository.*;
import za.co.handyflow.platform.recruitmentagency.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Direct mirror of payrollbureau.PayrollBureauPortalDataService's
 * pattern: every method starts with requireAccess(), tenant resolved
 * from the GRANT, never TenantContext.
 * <p>
 * Scoped to requisitions, placements (candidate pipeline visibility),
 * and invoices — a recruitment agency's client needs to see what roles
 * are being worked, who's been submitted and at what stage, and what
 * they owe. Does NOT expose the full candidate pool (RecAgencyCandidate)
 * — only placements (candidates actually submitted against THIS
 * client's own requisitions), same boundary reasoning as not exposing
 * budget lines in the Projects module's client portal elsewhere in
 * this platform. A client should not browse the agency's entire
 * candidate database, only the ones put forward for their own roles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitmentAgencyPortalDataService {

    private final RecPortalAccessGrantRepository grantRepo;
    private final RecAgencyClientRepository clientRepo;
    private final RecAgencyRequisitionRepository requisitionRepo;
    private final RecAgencyPlacementRepository placementRepo;
    private final RecAgencyCandidateRepository candidateRepo;
    private final RecAgencyInvoiceRepository invoiceRepo;

    @Transactional(readOnly = true)
    public List<PortalClientSummaryResponse> getMyClients(UUID portalUserId) {
        return grantRepo.findActiveGrantsForUser(portalUserId).stream()
                .map(g -> clientRepo.findById(g.getClientId())
                        .map(c -> new PortalClientSummaryResponse(c.getId(), c.getTradingName()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequisitionResponse> getMyRequisitions(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        String clientName = clientRepo.findById(clientId).map(RecAgencyClient::getTradingName).orElse("Unknown");
        return requisitionRepo.findByClient(clientId).stream()
                .map(r -> toRequisitionResponse(r, clientName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlacementResponse> getMyPlacements(UUID portalUserId, UUID clientId, UUID requisitionId) {
        requireAccess(portalUserId, clientId);
        RecAgencyRequisition requisition = requisitionRepo.findById(requisitionId)
                .filter(r -> r.getClientId().equals(clientId)) // confirm the requisition genuinely belongs to this client
                .orElseThrow(() -> new HandyFlowException("Requisition not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return placementRepo.findByRequisition(requisitionId).stream()
                .map(p -> {
                    RecAgencyCandidate c = candidateRepo.findById(p.getCandidateId()).orElseThrow();
                    return toPlacementResponse(p, requisition.getTitle(), c.getFullName());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AgencyInvoiceResponse> getMyInvoices(UUID portalUserId, UUID clientId, Pageable pageable) {
        requireAccess(portalUserId, clientId);
        return invoiceRepo.findByClient(clientId, pageable).map(this::toInvoiceResponse);
    }

    private RecPortalAccessGrant requireAccess(UUID portalUserId, UUID clientId) {
        return grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client", HttpStatus.FORBIDDEN, "NO_ACCESS"));
    }

    private RequisitionResponse toRequisitionResponse(RecAgencyRequisition r, String clientName) {
        int candidateCount = placementRepo.findByRequisition(r.getId()).size();
        return new RequisitionResponse(r.getId(), r.getClientId(), clientName, r.getRequisitionNumber(),
                r.getTitle(), r.getDescription(), r.getSalaryMin(), r.getSalaryMax(), r.getLocation(),
                r.getEmploymentType(), r.getStatus(), r.getTargetStartDate(), r.getNotes(),
                candidateCount, r.getCreatedAt());
    }

    private PlacementResponse toPlacementResponse(RecAgencyPlacement p, String requisitionTitle, String candidateName) {
        return new PlacementResponse(p.getId(), p.getRequisitionId(), requisitionTitle,
                p.getCandidateId(), candidateName, p.getClientId(), p.getStage(),
                p.getOfferedSalary(), p.getPlacementFeeAmount(), p.getPlacedAt(),
                p.getGuaranteeEndsAt(), p.getNotes(), p.getCreatedAt());
    }

    private AgencyInvoiceResponse toInvoiceResponse(RecAgencyInvoice inv) {
        return new AgencyInvoiceResponse(inv.getId(), inv.getInvoiceNumber(), inv.getDescription(),
                inv.getInvoiceDate(), inv.getDueDate(), inv.getSubtotal(), inv.getVatAmount(),
                inv.getTotal(), inv.getAmountPaid(), inv.balance(), inv.getStatus(),
                inv.getSentAt(), inv.getPaidAt(), inv.getPlacementId());
    }
}