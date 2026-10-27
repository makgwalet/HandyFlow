package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAsset;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAssetRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalRaiseRequest;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client self-service: a portal user reports a fault for one of their own sites.
 * <p>
 * The request lands as REQUESTED — exactly like a staff-logged one — and does nothing until staff approve it,
 * so a client can never create billable work on their own. The client chooses the site, asset, description and
 * whether it is a safety issue or an emergency; <b>priority is never client-set</b>: it comes from the same floor
 * rules as staff requests ({@link FmPriorityRules}) and staff confirm or raise it on approval. The agreement's
 * coverage and SLA are stamped at submission, as for any other request.
 * <p>
 * Intake is throttled per client ({@link #MAX_REQUESTS_PER_DAY} in 24 hours) so a faulty integration or a
 * compromised portal login cannot flood the helpdesk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmPortalRequestService {

    static final int MAX_REQUESTS_PER_DAY = 20;
    private static final Set<String> CLIENT_CATEGORIES = Set.of("CORRECTIVE", "EMERGENCY", "OTHER");

    private final FmPortalDataService portalDataService;
    private final FmSiteRepository siteRepository;
    private final FmAssetRepository assetRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmContractTermsService contractTermsService;
    private final FmAuditService auditService;
    private final FmAlerts alerts;

    @Transactional
    public FmWorkOrder raise(UUID portalUserId, UUID selectedClientId, FmPortalRaiseRequest req) {
        FmPortalDataService.Scope scope = portalDataService.resolveScope(portalUserId, selectedClientId);
        TenantId tenantId = scope.tenantId();
        UUID clientId = scope.clientId();

        String category = req.category() == null || req.category().isBlank() ? "CORRECTIVE" : req.category().trim().toUpperCase();
        if (!CLIENT_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("category must be one of " + CLIENT_CATEGORIES);
        }
        String description = req.description() == null ? "" : req.description().trim();
        if (description.length() < 10) {
            throw new IllegalArgumentException("Please describe the problem in a little more detail (at least 10 characters)");
        }

        // The site must be one of THIS client's; anything else looks like it does not exist.
        siteRepository.findActiveByIdForClient(tenantId, clientId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", req.siteId().toString()));
        if (req.assetId() != null) {
            FmAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FmAsset", req.assetId().toString()));
            if (!asset.getSiteId().equals(req.siteId())) {
                throw new IllegalArgumentException("The selected asset does not belong to the selected site");
            }
        }

        long recent = workOrderRepository.countPortalRequestsSince(tenantId, clientId, Instant.now().minus(Duration.ofHours(24)));
        if (recent >= MAX_REQUESTS_PER_DAY) {
            throw new HandyFlowException("Too many requests have been submitted today — please phone the helpdesk for anything urgent",
                    HttpStatus.TOO_MANY_REQUESTS, "REQUEST_LIMIT");
        }

        String priority = FmPriorityRules.resolve(null, category, req.safetyImpact());
        UUID duplicateOf = null;
        if (req.assetId() != null) {
            List<UUID> open = workOrderRepository.findOpenReactiveIdsForAsset(tenantId, req.assetId(), PageRequest.of(0, 1));
            if (!open.isEmpty()) duplicateOf = open.get(0);
        }
        String reportedBy = req.contactName() != null && !req.contactName().isBlank() ? req.contactName().trim() : "Client portal";

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FmWorkOrder wo = FmWorkOrder.createPortalRequest(tenantId, number, clientId, req.siteId(), req.assetId(), category,
                priority, description, reportedBy, portalUserId, req.safetyImpact(), duplicateOf);
        contractTermsService.applyTerms(tenantId, wo);
        workOrderRepository.save(wo);

        auditService.recordPortal(tenantId, portalUserId, "PORTAL_REQUEST_RAISED", "WORK_ORDER", wo.getId(),
                number + " client=" + clientId + " site=" + req.siteId() + " priority=" + priority + " safety=" + req.safetyImpact()
                        + (duplicateOf != null ? " possibleDuplicateOf=" + duplicateOf : ""));
        log.info("FM portal request raised number={} client={} tenant={}", number, clientId, tenantId);

        if (req.safetyImpact() || "EMERGENCY".equals(priority)) {
            alerts.toAdmins(tenantId, NotificationType.FM_WORKORDER_URGENT,
                    (req.safetyImpact() ? "Safety-related client request" : "Emergency client request") + ": " + number,
                    description, "/facilitiesmanagement/work-orders/" + wo.getId(), wo.getId().toString());
        } else {
            alerts.toAdmins(tenantId, NotificationType.FM_WORKORDER_REQUESTED, "Client request submitted: " + number,
                    "A client reported: " + description, "/facilitiesmanagement/work-orders/" + wo.getId(), wo.getId().toString());
        }
        return wo;
    }
}
