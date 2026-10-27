package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Inspection templates and runs. Templates are tenant-wide; inspections follow the caller's site scope
 * (out-of-scope = not found). Completing an inspection with failed checks raises one corrective action per
 * failure and alerts the tenant's administrators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityInspectionService {

    private final FacilityInspectionTemplateRepository templateRepository;
    private final FacilityInspectionRepository inspectionRepository;
    private final FacilityCorrectiveActionRepository actionRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;
    private final FacilityCorrectiveActionService actionService;
    private final FacilityAlerts alerts;

    // ── Templates ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InspectionTemplateResponse> listTemplates(TenantId tenantId, boolean includeInactive) {
        return templateRepository.findAllForTenant(tenantId, includeInactive).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InspectionTemplateResponse getTemplate(TenantId tenantId, UUID id) {
        return toResponse(findTemplate(tenantId, id));
    }

    @Transactional
    public InspectionTemplateResponse createTemplate(TenantId tenantId, InspectionTemplateRequest req) {
        requireUniqueName(tenantId, req.name(), new UUID(0, 0));
        FacilityInspectionTemplate t = FacilityInspectionTemplate.create(tenantId, req.name(), req.description(),
                req.assetCategory(), specs(req));
        templateRepository.save(t);
        auditService.record(tenantId, "INSPECTION_TEMPLATE_CREATED", "INSPECTION_TEMPLATE", t.getId(),
                t.getName() + " items=" + t.getItems().size());
        return toResponse(t);
    }

    @Transactional
    public InspectionTemplateResponse updateTemplate(TenantId tenantId, UUID id, InspectionTemplateRequest req) {
        FacilityInspectionTemplate t = findTemplate(tenantId, id);
        requireUniqueName(tenantId, req.name(), id);
        t.update(req.name(), req.description(), req.assetCategory(), specs(req));
        templateRepository.save(t);
        auditService.record(tenantId, "INSPECTION_TEMPLATE_UPDATED", "INSPECTION_TEMPLATE", id,
                t.getName() + " revision=" + t.getRevision() + " items=" + t.getItems().size());
        return toResponse(t);
    }

    @Transactional
    public InspectionTemplateResponse setTemplateActive(TenantId tenantId, UUID id, boolean active) {
        FacilityInspectionTemplate t = findTemplate(tenantId, id);
        t.setActive(active);
        templateRepository.save(t);
        auditService.record(tenantId, active ? "INSPECTION_TEMPLATE_ACTIVATED" : "INSPECTION_TEMPLATE_DEACTIVATED",
                "INSPECTION_TEMPLATE", id, t.getName());
        return toResponse(t);
    }

    // ── Inspections ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InspectionResponse> list(TenantId tenantId, UUID siteId, UUID assetId, String status, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (siteId != null) scope.requireSite(siteId);
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return inspectionRepository.findAllInScope(tenantId, siteId, assetId, s, scope.allSites(), scope.siteIds(), pageable)
                .map(i -> toResponse(i, List.of()));
    }

    @Transactional(readOnly = true)
    public InspectionResponse get(TenantId tenantId, UUID id) {
        FacilityInspection i = find(tenantId, id);
        List<UUID> actions = actionRepository.findBySource(tenantId, "INSPECTION", id).stream()
                .map(FacilityCorrectiveAction::getId).toList();
        return toResponse(i, actions);
    }

    @Transactional
    public InspectionResponse start(TenantId tenantId, StartInspectionRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));
        if (req.assetId() != null) {
            FacilityAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", req.assetId().toString()));
            if (!asset.getSiteId().equals(req.siteId()))
                throw new IllegalArgumentException("The selected asset does not belong to the selected site");
        }
        FacilityInspectionTemplate template = findTemplate(tenantId, req.templateId());
        FacilityInspection i = FacilityInspection.start(tenantId, template, req.siteId(), req.assetId(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        inspectionRepository.save(i);
        auditService.record(tenantId, "INSPECTION_STARTED", "INSPECTION", i.getId(),
                template.getName() + " site=" + req.siteId() + (req.assetId() != null ? " asset=" + req.assetId() : ""));
        return toResponse(i, List.of());
    }

    @Transactional
    public InspectionResponse answer(TenantId tenantId, UUID id, InspectionAnswersRequest req) {
        FacilityInspection i = find(tenantId, id);
        for (InspectionAnswersRequest.Answer a : req.answers()) i.answer(a.itemId(), a.outcome(), a.comment());
        inspectionRepository.save(i);
        return toResponse(i, List.of());
    }

    @Transactional
    public InspectionResponse complete(TenantId tenantId, UUID id, CompleteInspectionRequest req) {
        FacilityInspection i = find(tenantId, id);
        i.complete(req != null ? req.notes() : null);
        inspectionRepository.save(i);
        List<UUID> actions = i.failures().isEmpty() ? List.of() : actionService.createFromFailures(tenantId, i);
        auditService.record(tenantId, "INSPECTION_COMPLETED", "INSPECTION", id,
                i.getTemplateName() + " result=" + i.getResult() + " failures=" + i.failures().size()
                        + " critical=" + i.hasCriticalFailure() + " actions=" + actions.size());
        if ("FAIL".equals(i.getResult())) {
            alerts.toAdmins(tenantId, NotificationType.FACILITY_INSPECTION_FAILED,
                    (i.hasCriticalFailure() ? "CRITICAL failure: " : "Inspection failed: ") + i.getTemplateName(),
                    i.failures().size() + " check(s) failed; " + actions.size() + " corrective action(s) raised.",
                    "/facilities/inspections/" + id, id.toString());
        }
        return toResponse(i, actions);
    }

    @Transactional
    public InspectionResponse cancel(TenantId tenantId, UUID id, String reason) {
        FacilityInspection i = find(tenantId, id);
        i.cancel(reason);
        inspectionRepository.save(i);
        auditService.record(tenantId, "INSPECTION_CANCELLED", "INSPECTION", id, i.getTemplateName() + " reason=" + i.getCancelReason());
        return toResponse(i, List.of());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FacilityInspection find(TenantId tenantId, UUID id) {
        AccessScope scope = accessPolicy.scope(tenantId);
        FacilityInspection i = inspectionRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityInspection", id.toString()));
        if (!scope.canSeeSite(i.getSiteId())) throw new ResourceNotFoundException("FacilityInspection", id.toString());
        return i;
    }

    private FacilityInspectionTemplate findTemplate(TenantId tenantId, UUID id) {
        return templateRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityInspectionTemplate", id.toString()));
    }

    private void requireUniqueName(TenantId tenantId, String name, UUID excludeId) {
        if (name != null && templateRepository.existsByName(tenantId, name.trim(), excludeId)) {
            throw new HandyFlowException("A template called '" + name.trim() + "' already exists", HttpStatus.CONFLICT, "TEMPLATE_NAME_TAKEN");
        }
    }

    private static List<FacilityInspectionTemplate.ItemSpec> specs(InspectionTemplateRequest req) {
        return req.items().stream().map(it -> new FacilityInspectionTemplate.ItemSpec(it.prompt(), it.critical())).toList();
    }

    private InspectionTemplateResponse toResponse(FacilityInspectionTemplate t) {
        return new InspectionTemplateResponse(t.getId(), t.getName(), t.getDescription(), t.getAssetCategory(), t.isActive(),
                t.getRevision(), t.getItems().stream()
                .map(it -> new InspectionTemplateResponse.Item(it.getId(), it.getPosition(), it.getPrompt(), it.isCritical())).toList(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    static InspectionResponse toResponse(FacilityInspection i, List<UUID> actions) {
        int total = i.getResults().size();
        return new InspectionResponse(i.getId(), i.getTemplateId(), i.getTemplateName(), i.getTemplateRevision(), i.getSiteId(),
                i.getAssetId(), i.getInspectorUserId(), i.getInspectorName(), i.getStatus(), i.getResult(), i.getNotes(),
                total - i.unanswered().size(), total, i.getStartedAt(), i.getCompletedAt(),
                i.getResults().stream().map(r -> new InspectionResponse.Result(r.getItemId(), r.getPosition(), r.getPrompt(),
                        r.isCritical(), r.getOutcome(), r.getComment())).toList(), actions);
    }
}
