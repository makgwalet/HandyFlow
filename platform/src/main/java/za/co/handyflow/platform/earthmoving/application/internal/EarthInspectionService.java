package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthInspectionItemRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthInspectionRepository;
import za.co.handyflow.platform.earthmoving.dto.CreateIncidentRequest;
import za.co.handyflow.platform.earthmoving.dto.CreateInspectionRequest;
import za.co.handyflow.platform.earthmoving.dto.InspectionResponse;
import za.co.handyflow.platform.earthmoving.dto.LatestInspectionResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Inspections (pre-use, handover and return). An UNSAFE finding is reported as a critical breakdown: the machine goes out of service, a work
 * order is raised and the people on the alert route are told, all through the existing incident path. It comes back only
 * through the normal release, which needs release authority. A DEFECT is reported as a medium incident so it is tracked
 * and does not get lost, without stopping the machine.
 */
@Service
@RequiredArgsConstructor
public class EarthInspectionService {

    private final EarthAssetRepository assets;
    private final EarthInspectionRepository inspections;
    private final EarthInspectionItemRepository items;
    private final EarthmovingIncidentService incidents;
    private final EarthChecklistService checklists;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    public List<String> checklist() { return InspectionRules.CHECKLIST; }

    @Transactional
    public InspectionResponse create(TenantId tenantId, CreateInspectionRequest req, UUID inspector) {
        access.require(EarthmovingRules.WRITE, "record an inspection");
        EarthAsset asset = assets.findActiveById(tenantId, req.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", req.assetId().toString()));
        if (asset.getStatus() == AssetStatus.RETIRED) throw new IllegalStateException("A retired machine cannot be inspected.");

        String type = InspectionRules.type(req.inspectionType());
        InspectionRules.checkMeter(type, req.meterReading());
        ChecklistRules.Active checklist = checklists.resolve(tenantId, type, req.templateVersionId());
        ChecklistRules.checkSignoff(checklist.requiresSignoff(), req.signOffName());
        List<ChecklistRules.Checked> answers = ChecklistRules.check(checklist.questions(), req.items().stream()
                .map(i -> new ChecklistRules.Given(i.item(), i.outcome(), i.comment(), i.valueNumber(), i.valueText(), i.evidenceRef())).toList());
        String result = ChecklistRules.overall(answers);

        EarthInspection inspection = EarthInspection.create(tenantId, asset.getId(), inspector, req.meterReading(), result, req.notes(), type);
        inspection.stamp(checklist.versionId(), checklist.versionNo(), req.signOffName());
        inspection = inspections.save(inspection);
        List<EarthInspectionItem> saved = new ArrayList<>();
        for (int n = 0; n < answers.size(); n++) {
            ChecklistRules.Checked a = answers.get(n);
            EarthInspectionItem row = EarthInspectionItem.of(tenantId, inspection.getId(), n + 1, a);
            if (!InspectionRules.OK.equals(a.outcome())) row.linkIncident(raiseIncident(tenantId, asset, a, inspector));
            saved.add(items.save(row));
        }
        trail.record(tenantId, "EARTHMOVING_INSPECTION_RECORDED", "EARTHMOVING_INSPECTION", inspection.getId(),
                Map.of("machine", asset.getName()), Map.of("type", type, "version", String.valueOf(checklist.versionNo()), "result", result, "findings", String.valueOf(answers.stream().filter(a -> !InspectionRules.OK.equals(a.outcome())).count())));
        return toResponse(inspection, asset.getName(), saved);
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> recent(TenantId tenantId, UUID assetId) {
        access.require(EarthmovingRules.READ, "view inspections");
        List<EarthInspection> found = inspections.findRecent(tenantId, assetId, PageRequest.of(0, 100));
        if (found.isEmpty()) return List.of();
        Map<UUID, List<EarthInspectionItem>> byInspection = items.findForInspections(tenantId, found.stream().map(EarthInspection::getId).toList())
                .stream().collect(Collectors.groupingBy(EarthInspectionItem::getInspectionId));
        Map<UUID, String> names = new HashMap<>();
        for (UUID id : found.stream().map(EarthInspection::getAssetId).distinct().toList())
            assets.findActiveById(tenantId, id).ifPresent(a -> names.put(id, label(a)));
        return found.stream().map(i -> toResponse(i, names.getOrDefault(i.getAssetId(), "Unknown machine"), byInspection.getOrDefault(i.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<LatestInspectionResponse> latest(TenantId tenantId) {
        access.require(EarthmovingRules.READ, "view inspections");
        Map<UUID, EarthInspection> byAsset = new LinkedHashMap<>();
        for (EarthInspection i : inspections.findLatestPerAsset(tenantId)) byAsset.merge(i.getAssetId(), i,
                (a, b) -> b.getPerformedAt().isAfter(a.getPerformedAt()) ? b : a);
        return byAsset.values().stream().map(i -> new LatestInspectionResponse(i.getAssetId(), i.getPerformedAt(), i.getResult())).toList();
    }

    private UUID raiseIncident(TenantId tenantId, EarthAsset asset, ChecklistRules.Checked a, UUID inspector) {
        String item = a.question().text();
        boolean unsafe = InspectionRules.UNSAFE.equals(a.outcome());
        return incidents.reportIncident(tenantId, new CreateIncidentRequest(asset.getId(),
                unsafe ? "BREAKDOWN" : "OTHER", unsafe ? "CRITICAL" : "MEDIUM",
                (unsafe ? "Unsafe at inspection: " : "Defect at inspection: ") + item, a.comment(), null, asset.getCurrentSite(), null, null), inspector).id();
    }

    private static String label(EarthAsset a) { return a.getFleetNumber() != null ? a.getFleetNumber() + " — " + a.getName() : a.getName(); }

    private static InspectionResponse toResponse(EarthInspection i, String machine, List<EarthInspectionItem> rows) {
        return new InspectionResponse(i.getId(), i.getAssetId(), machine, i.getInspectorUserId(), i.getPerformedAt(), i.getMeterReading(), i.getResult(), i.getNotes(),
                rows.stream().sorted(Comparator.comparingInt(EarthInspectionItem::getPosition))
                        .map(x -> new InspectionResponse.Item(x.getItem(), x.getOutcome(), x.getComment(), x.getIncidentId(), x.getSection(), x.getAnswerType(),
                                x.getValueNumber(), x.getValueText(), x.getEvidenceRef(), x.getUnit())).toList(),
                i.getInspectionType(), i.getTemplateVersionId(), i.getTemplateVersionNo(), i.getSignOffName());
    }
}
