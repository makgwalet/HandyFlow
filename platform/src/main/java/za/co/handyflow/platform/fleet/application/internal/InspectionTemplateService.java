package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.InspectionTemplate;
import za.co.handyflow.platform.fleet.domain.model.InspectionType;
import za.co.handyflow.platform.fleet.domain.model.IssueSeverity;
import za.co.handyflow.platform.fleet.domain.model.TemplateItemSpec;
import za.co.handyflow.platform.fleet.domain.repository.InspectionTemplateRepository;
import za.co.handyflow.platform.fleet.dto.InspectionTemplateItemResponse;
import za.co.handyflow.platform.fleet.dto.InspectionTemplateResponse;
import za.co.handyflow.platform.fleet.dto.SaveInspectionTemplateRequest;
import za.co.handyflow.platform.fleet.dto.TemplateItemRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionTemplateService {

    static final String STARTER_TEMPLATE_NAME = "Daily vehicle inspection";

    private final InspectionTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public List<InspectionTemplateResponse> list(TenantId tenantId) {
        return templateRepository.findAllActive(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InspectionTemplateResponse get(TenantId tenantId, UUID id) {
        return toResponse(find(tenantId, id));
    }

    @Transactional
    public InspectionTemplateResponse create(TenantId tenantId, SaveInspectionTemplateRequest req) {
        String name = req.name().trim();
        assertNameFree(tenantId, name, UUID.randomUUID());
        InspectionTemplate t = InspectionTemplate.create(
                tenantId, name, req.inspectionType(), blankToNull(req.description()),
                req.intervalDays(), normaliseVehicleType(req.appliesToVehicleType()),
                req.signatureRequired(), toSpecs(req.items()));
        templateRepository.save(t);
        log.info("Inspection template created id={} name={} tenant={}", t.getId(), name, tenantId);
        return toResponse(t);
    }

    /** Full replace. Safe for history: past inspections hold their own copy of the questions. */
    @Transactional
    public InspectionTemplateResponse update(TenantId tenantId, UUID id, SaveInspectionTemplateRequest req) {
        InspectionTemplate t = find(tenantId, id);
        String name = req.name().trim();
        assertNameFree(tenantId, name, id);
        t.update(name, req.inspectionType(), blankToNull(req.description()), req.intervalDays(),
                normaliseVehicleType(req.appliesToVehicleType()), req.signatureRequired(),
                req.active() == null || req.active(), toSpecs(req.items()));
        templateRepository.save(t);
        return toResponse(t);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id, UUID deletedBy) {
        InspectionTemplate t = find(tenantId, id);
        t.softDelete(deletedBy);
        templateRepository.save(t);
    }

    /**
     * Creates a ready-made "Daily vehicle inspection" (tyres, lights, fluids, body,
     * safety equipment) if the tenant doesn't already have one by that name. No schedule
     * is set — give it an intervalDays when you want vehicles flagged as overdue.
     */
    @Transactional
    public InspectionTemplateResponse createStarter(TenantId tenantId) {
        if (templateRepository.existsOtherWithName(tenantId, STARTER_TEMPLATE_NAME, UUID.randomUUID())) {
            throw new IllegalArgumentException("A template named \"" + STARTER_TEMPLATE_NAME + "\" already exists");
        }
        List<TemplateItemSpec> specs = new ArrayList<>();
        for (String pos : List.of("Front left", "Front right", "Rear left", "Rear right")) {
            specs.add(new TemplateItemSpec("Tyres", pos + " tyre", true, true, IssueSeverity.HIGH));
        }
        specs.add(new TemplateItemSpec("Lights", "Headlights", true, false, IssueSeverity.MEDIUM));
        specs.add(new TemplateItemSpec("Lights", "Brake lights", true, false, IssueSeverity.MEDIUM));
        specs.add(new TemplateItemSpec("Lights", "Indicators", true, false, IssueSeverity.MEDIUM));
        specs.add(new TemplateItemSpec("Fluids", "Engine oil level", true, false, IssueSeverity.HIGH));
        specs.add(new TemplateItemSpec("Fluids", "Coolant level", true, false, IssueSeverity.HIGH));
        specs.add(new TemplateItemSpec("Fluids", "Brake fluid level", true, false, IssueSeverity.CRITICAL));
        specs.add(new TemplateItemSpec("Body", "Body damage", true, true, IssueSeverity.MEDIUM));
        specs.add(new TemplateItemSpec("Body", "Windscreen", true, true, IssueSeverity.MEDIUM));
        specs.add(new TemplateItemSpec("Body", "Mirrors", true, false, IssueSeverity.MEDIUM));
        specs.add(new TemplateItemSpec("Safety", "Fire extinguisher", true, false, IssueSeverity.HIGH));
        specs.add(new TemplateItemSpec("Safety", "First aid kit", true, false, IssueSeverity.LOW));
        specs.add(new TemplateItemSpec("Safety", "Warning triangle", true, false, IssueSeverity.LOW));

        InspectionTemplate t = InspectionTemplate.create(tenantId, STARTER_TEMPLATE_NAME, InspectionType.DAILY,
                "Starter checklist — edit to suit your fleet.", null, null, false, specs);
        templateRepository.save(t);
        return toResponse(t);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private InspectionTemplate find(TenantId tenantId, UUID id) {
        return templateRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("InspectionTemplate", id.toString()));
    }

    private void assertNameFree(TenantId tenantId, String name, UUID excludeId) {
        if (templateRepository.existsOtherWithName(tenantId, name, excludeId)) {
            throw new IllegalArgumentException("An inspection template named \"" + name + "\" already exists");
        }
    }

    private List<TemplateItemSpec> toSpecs(List<TemplateItemRequest> items) {
        return items.stream().map(i -> new TemplateItemSpec(
                i.section().trim(), i.label().trim(),
                i.required() == null || i.required(),
                i.photoRequiredOnFail() != null && i.photoRequiredOnFail(),
                i.severity() != null ? i.severity() : IssueSeverity.MEDIUM)).toList();
    }

    private String normaliseVehicleType(String raw) {
        String v = blankToNull(raw);
        return v == null ? null : v.toUpperCase();
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private InspectionTemplateResponse toResponse(InspectionTemplate t) {
        List<InspectionTemplateItemResponse> items = t.getItems().stream()
                .map(i -> new InspectionTemplateItemResponse(i.getId(), i.getSection(), i.getLabel(),
                        i.getSortOrder(), i.isRequired(), i.isPhotoRequiredOnFail(), i.getSeverity().name()))
                .toList();
        return new InspectionTemplateResponse(t.getId(), t.getName(), t.getInspectionType().name(),
                t.getDescription(), t.getIntervalDays(), t.getAppliesToVehicleType(),
                t.isSignatureRequired(), t.isActive(), items, t.getCreatedAt(), t.getUpdatedAt());
    }
}