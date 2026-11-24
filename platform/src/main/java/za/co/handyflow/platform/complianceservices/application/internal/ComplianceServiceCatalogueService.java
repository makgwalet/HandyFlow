package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ServiceCatalogueDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The practice's service catalogue. A service is available to sell only once a second person has approved a dated
 * version of it, and an approved version is never changed: a change is a new version. It holds no prices.
 */
@Service
@RequiredArgsConstructor
public class ComplianceServiceCatalogueService {

    private final ComplianceServiceDefinitionRepository services;
    private final ComplianceServiceVersionRepository versions;
    private final ComplianceServiceItemRepository items;

    @Transactional(readOnly = true)
    public List<ServiceSummary> list(TenantId tenantId, LocalDate asOf) {
        Map<UUID, List<ComplianceServiceVersion>> byService = versions.findAllForTenant(tenantId).stream()
                .collect(Collectors.groupingBy(ComplianceServiceVersion::getServiceId));
        return services.findAllForTenant(tenantId).stream().map(s -> {
            List<ComplianceServiceVersion> vs = byService.getOrDefault(s.getId(), List.of());
            ComplianceServiceVersion latest = vs.stream().max(Comparator.comparingInt(ComplianceServiceVersion::getVersionNumber)).orElse(null);
            Integer inForce = ObligationRules.effectiveOn(vs.stream().map(ComplianceServiceVersion::span).toList(), asOf);
            ComplianceServiceVersion shown = inForce == null ? latest
                    : vs.stream().filter(v -> v.getVersionNumber() == inForce).findFirst().orElse(latest);
            return new ServiceSummary(s.getId(), s.getCode(), shown == null ? null : shown.getName(), shown == null ? null : shown.getCategory(), inForce,
                    latest == null ? 0 : latest.getVersionNumber(), latest == null ? null : latest.getReviewStatus(),
                    shown == null ? null : shown.getPricingModel(), shown == null ? null : shown.getEstimatedHours());
        }).toList();
    }

    @Transactional(readOnly = true)
    public ServiceDetail get(TenantId tenantId, UUID id) {
        return detail(tenantId, find(tenantId, id));
    }

    @Transactional
    public ServiceDetail create(TenantId tenantId, CreateServiceRequest r, UUID by) {
        String code;
        try {
            code = ObligationRules.code(r.code());
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
        if (services.findByCode(tenantId, code).isPresent()) {
            throw new HandyFlowException("A service with code " + code + " already exists", HttpStatus.CONFLICT, "SERVICE_CODE_EXISTS");
        }
        SaveVersionRequest req = r.version() == null ? emptyRequest() : r.version();
        ComplianceServiceDefinition s = services.save(ComplianceServiceDefinition.create(tenantId, code, by));
        ComplianceServiceVersion v = ComplianceServiceVersion.draft(tenantId, s.getId(), 1, by);
        fill(v, req);
        versions.save(v);
        saveItems(tenantId, v.getId(), req);
        return detail(tenantId, s);
    }

    @Transactional
    public ServiceDetail saveDraft(TenantId tenantId, UUID serviceId, UUID versionId, SaveVersionRequest r, UUID by) {
        ComplianceServiceDefinition s = find(tenantId, serviceId);
        ComplianceServiceVersion v = findVersion(tenantId, serviceId, versionId);
        fill(v, r);
        versions.save(v);
        saveItems(tenantId, v.getId(), r);
        return detail(tenantId, s);
    }

    /** Starts the next version as a copy of the latest one, so changes are made on a new record. */
    @Transactional
    public ServiceDetail newVersion(TenantId tenantId, UUID serviceId, UUID by) {
        ComplianceServiceDefinition s = find(tenantId, serviceId);
        List<ComplianceServiceVersion> vs = versions.findForService(tenantId, serviceId);
        if (vs.stream().anyMatch(v -> !v.isApproved())) {
            throw new HandyFlowException("There is already a draft or a version in review", HttpStatus.CONFLICT, "SERVICE_DRAFT_EXISTS");
        }
        ComplianceServiceVersion last = vs.stream().max(Comparator.comparingInt(ComplianceServiceVersion::getVersionNumber))
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceServiceVersion", serviceId.toString()));
        ComplianceServiceVersion next = ComplianceServiceVersion.draft(tenantId, serviceId, last.getVersionNumber() + 1, by);
        next.copyFrom(last);
        versions.save(next);
        int i = 0;
        for (ComplianceServiceItem e : items.findForVersions(tenantId, List.of(last.getId()))) {
            items.save(ComplianceServiceItem.of(tenantId, next.getId(), new ServiceCatalogueRules.Item(e.getKind(), e.getText(), e.isMandatory()), i++));
        }
        return detail(tenantId, s);
    }

    @Transactional
    public ServiceDetail submit(TenantId tenantId, UUID serviceId, UUID versionId, UUID by) {
        ComplianceServiceDefinition s = find(tenantId, serviceId);
        ComplianceServiceVersion v = findVersion(tenantId, serviceId, versionId);
        long deliverables = items.findForVersions(tenantId, List.of(v.getId())).stream().filter(i -> "DELIVERABLE".equals(i.getKind())).count();
        transition(() -> v.submit(by, deliverables));
        versions.save(v);
        return detail(tenantId, s);
    }

    @Transactional
    public ServiceDetail approve(TenantId tenantId, UUID serviceId, UUID versionId, ApproveRequest r, UUID by) {
        ComplianceServiceDefinition s = find(tenantId, serviceId);
        ComplianceServiceVersion v = findVersion(tenantId, serviceId, versionId);
        LocalDate from = r == null ? null : r.effectiveFrom();
        transition(() -> v.approve(by, from, r == null ? null : r.exceptionReason(), r == null ? null : r.note()));
        // the version before it stops the day before this one starts
        versions.findForService(tenantId, serviceId).stream()
                .filter(p -> p.isApproved() && p.getVersionNumber() < v.getVersionNumber())
                .max(Comparator.comparingInt(ComplianceServiceVersion::getVersionNumber))
                .ifPresent(prev -> {
                    if (prev.getEffectiveTo() == null || !prev.getEffectiveTo().isBefore(from)) {
                        transition(() -> prev.endOn(ObligationRules.endOfPrevious(prev.getEffectiveFrom(), from)));
                        versions.save(prev);
                    }
                });
        versions.save(v);
        return detail(tenantId, s);
    }

    @Transactional
    public ServiceDetail reject(TenantId tenantId, UUID serviceId, UUID versionId, String reason, UUID by) {
        ComplianceServiceDefinition s = find(tenantId, serviceId);
        ComplianceServiceVersion v = findVersion(tenantId, serviceId, versionId);
        transition(() -> v.reject(by, reason));
        versions.save(v);
        return detail(tenantId, s);
    }

    /** Stops selling the version now in force from a date. Work already sold on it keeps its version. */
    @Transactional
    public ServiceDetail retire(TenantId tenantId, UUID serviceId, LocalDate on, UUID by) {
        ComplianceServiceDefinition s = find(tenantId, serviceId);
        LocalDate date = on == null ? LocalDate.now() : on;
        ComplianceServiceVersion open = versions.findForService(tenantId, serviceId).stream()
                .filter(v -> v.isApproved() && v.getEffectiveTo() == null)
                .max(Comparator.comparingInt(ComplianceServiceVersion::getVersionNumber))
                .orElseThrow(() -> new HandyFlowException("No approved version is open-ended", HttpStatus.CONFLICT, "SERVICE_NOT_IN_FORCE"));
        transition(() -> open.endOn(date));
        versions.save(open);
        return detail(tenantId, s);
    }

    // ---- helpers ----

    private void fill(ComplianceServiceVersion v, SaveVersionRequest r) {
        try {
            v.edit(new ComplianceServiceVersion.Content(r.name(), r.category(), r.description(), r.applicabilityNote(), r.exclusions(), r.estimatedHours(),
                    r.slaDays(), r.responsibleRole(), r.requiresReview() == null || r.requiresReview(), r.pricingModel(), r.billingTrigger(), r.changeSummary()));
            checked(r);
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "SERVICE_VERSION_LOCKED");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
    }

    private static List<ServiceCatalogueRules.Item> checked(SaveVersionRequest r) {
        List<ServiceCatalogueRules.Item> out = new ArrayList<>();
        if (r.items() != null) for (ItemInput i : r.items()) out.add(ServiceCatalogueRules.item(i.kind(), i.text(), i.mandatory()));
        ServiceCatalogueRules.noDuplicates(out);
        return out;
    }

    private void saveItems(TenantId tenantId, UUID versionId, SaveVersionRequest r) {
        items.deleteForVersion(versionId);
        int i = 0;
        for (ServiceCatalogueRules.Item item : checked(r)) items.save(ComplianceServiceItem.of(tenantId, versionId, item, i++));
    }

    private static void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "SERVICE_WORKFLOW");
        } catch (IllegalArgumentException e) {
            throw bad(e);
        }
    }

    private static SaveVersionRequest emptyRequest() {
        return new SaveVersionRequest(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ServiceDetail detail(TenantId tenantId, ComplianceServiceDefinition s) {
        List<ComplianceServiceVersion> vs = versions.findForService(tenantId, s.getId());
        List<UUID> ids = vs.stream().map(ComplianceServiceVersion::getId).toList();
        Map<UUID, List<ItemView>> its = ids.isEmpty() ? Map.of() : items.findForVersions(tenantId, ids).stream()
                .collect(Collectors.groupingBy(ComplianceServiceItem::getVersionId, LinkedHashMap::new,
                        Collectors.mapping(e -> new ItemView(e.getKind(), e.getText(), e.isMandatory()), Collectors.toList())));
        Integer inForce = ObligationRules.effectiveOn(vs.stream().map(ComplianceServiceVersion::span).toList(), LocalDate.now());
        return new ServiceDetail(s.getId(), s.getCode(), vs.stream().map(v -> new VersionView(v.getId(), v.getVersionNumber(), v.getReviewStatus(),
                v.getName(), v.getCategory(), v.getDescription(), v.getApplicabilityNote(), v.getExclusions(), v.getEstimatedHours(), v.getSlaDays(),
                v.getResponsibleRole(), v.isRequiresReview(), v.getPricingModel(), v.getBillingTrigger(), v.getChangeSummary(), v.getAuthoredBy(),
                v.getSubmittedBy(), v.getSubmittedAt(), v.getReviewedBy(), v.getReviewedAt(), v.getReviewNote(), v.getReviewExceptionReason(),
                v.getEffectiveFrom(), v.getEffectiveTo(), inForce != null && inForce == v.getVersionNumber(), its.getOrDefault(v.getId(), List.of()))).toList());
    }

    private ComplianceServiceDefinition find(TenantId tenantId, UUID id) {
        return services.findByIdForTenant(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceServiceDefinition", id.toString()));
    }

    private ComplianceServiceVersion findVersion(TenantId tenantId, UUID serviceId, UUID versionId) {
        return versions.findOne(tenantId, serviceId, versionId).orElseThrow(() -> new ResourceNotFoundException("ComplianceServiceVersion", versionId.toString()));
    }

    private static HandyFlowException bad(IllegalArgumentException e) {
        return new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "SERVICE_INVALID");
    }
}
