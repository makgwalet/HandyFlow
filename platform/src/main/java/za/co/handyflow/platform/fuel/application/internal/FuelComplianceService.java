package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceEvidence;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.model.FuelLegalEntity;
import za.co.handyflow.platform.fuel.dto.ComplianceLinkageDtos.*;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceRenewal;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ComplianceRequest;
import za.co.handyflow.platform.fuel.dto.ComplianceResponse;
import za.co.handyflow.platform.fuel.dto.RenewRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Stage 4B: licences, calibrations, inspections and certificates with expiry. An expired tanker, driver or company licence blocks allocation. */
@Service
@RequiredArgsConstructor
public class FuelComplianceService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelComplianceItemRepository items;
    private final FuelComplianceRenewalRepository renewals;
    private final FuelTankerRepository tankers;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;
    private final HrFacade hr;
    private final FuelLegalEntityRepository legalEntities;
    private final FuelComplianceEvidenceRepository evidence;

    @Transactional(readOnly = true)
    public List<ComplianceResponse> list(TenantId tenantId) {
        LocalDate today = LocalDate.now(SAST);
        Map<UUID, String> names = entityNames(tenantId);
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : evidence.countActiveByItem(tenantId)) counts.put((UUID) row[0], ((Number) row[1]).longValue());
        return items.findAllActive(tenantId).stream().map(i -> toResponse(i, today, names.get(i.getLegalEntityId()), counts.getOrDefault(i.getId(), 0L))).toList();
    }

    /** Licences by legal entity and status today. Information only: the system does not know which licences a business must hold. */
    @Transactional(readOnly = true)
    public Coverage coverage(TenantId tenantId) {
        LocalDate today = LocalDate.now(SAST);
        List<FuelComplianceItem> licences = items.findAllActive(tenantId).stream().filter(i -> "LICENCE".equals(i.getItemType())).toList();
        List<CoverageRow> rows = new ArrayList<>();
        for (FuelLegalEntity e : legalEntities.findAllActive(tenantId)) {
            long valid = 0, expiring = 0, expired = 0;
            TreeSet<String> activities = new TreeSet<>();
            for (FuelComplianceItem i : licences) {
                if (!e.getId().equals(i.getLegalEntityId())) continue;
                switch (i.statusOn(today)) { case "EXPIRED" -> expired++; case "EXPIRING" -> expiring++; default -> valid++; }
                if (i.getLicensedActivity() != null) activities.add(i.getLicensedActivity());
            }
            rows.add(new CoverageRow(e.getId(), e.getName(), valid, expiring, expired, List.copyOf(activities)));
        }
        return new Coverage(rows, licences.stream().filter(i -> i.getLegalEntityId() == null).count());
    }

    @Transactional(readOnly = true)
    public List<EvidenceView> listEvidence(TenantId tenantId, UUID itemId) {
        find(tenantId, itemId);
        return evidence.findByItem(tenantId, itemId).stream().map(FuelComplianceService::view).toList();
    }

    @Transactional
    public EvidenceView addEvidence(TenantId tenantId, UUID itemId, EvidenceRequest req, UUID by) {
        find(tenantId, itemId);
        return view(evidence.save(FuelComplianceEvidence.create(tenantId, itemId, req.fileRef(), req.note(), by)));
    }

    @Transactional
    public EvidenceView supersedeEvidence(TenantId tenantId, UUID evidenceId, String reason, UUID by) {
        FuelComplianceEvidence e = evidence.findByIdForUpdate(tenantId, evidenceId).orElseThrow(() -> new ResourceNotFoundException("Evidence", evidenceId.toString()));
        e.supersede(reason, by);
        return view(evidence.save(e));
    }

    private static EvidenceView view(FuelComplianceEvidence e) {
        return new EvidenceView(e.getId(), e.getItemId(), e.getFileRef(), e.getNote(), e.getCapturedBy(), e.getCapturedAt(), e.getStatus(), e.getSupersededReason());
    }

    private Map<UUID, String> entityNames(TenantId tenantId) {
        Map<UUID, String> m = new HashMap<>();
        for (FuelLegalEntity e : legalEntities.findAllActive(tenantId)) m.put(e.getId(), e.getName());
        return m;
    }

    /** The legal entity must belong to this tenant. Null means "not linked". */
    private String requireEntity(TenantId tenantId, UUID legalEntityId) {
        if (legalEntityId == null) return null;
        return legalEntities.findActiveById(tenantId, legalEntityId).map(FuelLegalEntity::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Legal entity", legalEntityId.toString()));
    }

    @Transactional
    public ComplianceResponse create(TenantId tenantId, ComplianceRequest req) {
        String label = resolveLabel(tenantId, req.subjectType(), req.subjectId(), req.subjectLabel());
        FuelComplianceItem i = FuelComplianceItem.create(tenantId, req.itemType(), req.title(), req.referenceNo(), req.subjectType(),
                req.subjectId(), label, req.issuedOn(), req.expiresOn(), req.notes());
        String entityName = requireEntity(tenantId, req.legalEntityId());
        i.applyScope(req.legalEntityId(), req.licensedActivity(), req.issuingAuthority());
        return toResponse(items.save(i), LocalDate.now(SAST), entityName, 0L);
    }

    /** What the item is (type, title, dates, notes); who it is for cannot change: add a new item instead. */
    @Transactional
    public ComplianceResponse update(TenantId tenantId, UUID id, ComplianceRequest req) {
        FuelComplianceItem i = find(tenantId, id);
        i.update(req.itemType(), req.title(), req.referenceNo(), req.issuedOn(), req.expiresOn(), req.notes());
        String entityName = requireEntity(tenantId, req.legalEntityId());
        i.applyScope(req.legalEntityId(), req.licensedActivity(), req.issuingAuthority());
        return toResponse(items.save(i), LocalDate.now(SAST), entityName, evidence.countActive(tenantId, id));
    }

    @Transactional
    public ComplianceResponse renew(TenantId tenantId, UUID id, RenewRequest req, UUID by) {
        FuelComplianceItem i = find(tenantId, id);
        LocalDate previous = i.renew(req.newExpiresOn(), req.issuedOn());
        items.save(i);
        renewals.save(FuelComplianceRenewal.of(tenantId, id, previous, req.newExpiresOn(), by, req.note()));
        return toResponse(i, LocalDate.now(SAST), i.getLegalEntityId() == null ? null : entityNames(tenantId).get(i.getLegalEntityId()), evidence.countActive(tenantId, id));
    }

    @Transactional
    public void remove(TenantId tenantId, UUID id) {
        FuelComplianceItem i = find(tenantId, id);
        i.softDelete();
        items.save(i);
    }

    /**
     * Refuses to put an unlicensed tanker, driver or company on the road. Lists every expired item so it can be fixed in one go.
     * Called by allocation before anything is changed.
     */
    @Transactional(readOnly = true)
    public void requireCompliantForAllocation(TenantId tenantId, UUID tankerId, String tankerLabel, UUID driverEmployeeId, String driverLabel) {
        LocalDate today = LocalDate.now(SAST);
        StringBuilder problems = new StringBuilder();
        for (FuelComplianceItem i : items.findExpiredFor(tenantId, "TANKER", tankerId, today)) describe(problems, i, "COMPANY".equals(i.getSubjectType()) ? "The company" : tankerLabel);
        for (FuelComplianceItem i : items.findExpiredFor(tenantId, "DRIVER", driverEmployeeId, today)) {
            if ("COMPANY".equals(i.getSubjectType())) continue; // already reported once above
            describe(problems, i, driverLabel);
        }
        if (problems.length() > 0) throw new IllegalStateException("Cannot allocate: " + problems.toString().trim() + " Renew it in Compliance first.");
    }

    private void describe(StringBuilder sb, FuelComplianceItem i, String who) {
        String text = who + " has an expired " + i.getTitle() + " (expired " + i.getExpiresOn() + ").";
        if (sb.indexOf(text) < 0) sb.append(text).append(' ');
    }

    private String resolveLabel(TenantId tenantId, String subjectType, UUID subjectId, String typed) {
        if (subjectType == null) throw new IllegalArgumentException("Choose who or what this is for.");
        return switch (subjectType) {
            case "TANKER" -> tankers.findActiveById(tenantId, require(subjectId, "tanker")).map(t -> t.getRegistration()).orElseThrow(() -> new ResourceNotFoundException("Tanker", subjectId.toString()));
            case "TANK" -> tanks.findActiveById(tenantId, require(subjectId, "tank")).map(t -> t.getName()).orElseThrow(() -> new ResourceNotFoundException("Tank", subjectId.toString()));
            case "DEPOT" -> depots.findActiveById(tenantId, require(subjectId, "depot")).map(d -> d.getName()).orElseThrow(() -> new ResourceNotFoundException("Depot", subjectId.toString()));
            case "DRIVER" -> hr.findEmployeeById(tenantId, require(subjectId, "driver")).map(e -> e.fullName()).orElseThrow(() -> new ResourceNotFoundException("Driver", subjectId.toString()));
            case "COMPANY" -> typed == null || typed.isBlank() ? "Company" : typed.trim();
            case "OTHER" -> typed == null || typed.isBlank() ? "Other" : typed.trim();
            default -> throw new IllegalArgumentException("Unknown subject: " + subjectType);
        };
    }

    private static UUID require(UUID id, String what) {
        if (id == null) throw new IllegalArgumentException("Choose which " + what + " this is for.");
        return id;
    }

    private FuelComplianceItem find(TenantId tenantId, UUID id) {
        return items.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Compliance item", id.toString()));
    }

    private ComplianceResponse toResponse(FuelComplianceItem i, LocalDate today, String entityName, long evidenceCount) {
        return new ComplianceResponse(i.getId(), i.getItemType(), i.getTitle(), i.getReferenceNo(), i.getSubjectType(), i.getSubjectId(),
                i.getSubjectLabel(), i.getIssuedOn(), i.getExpiresOn(), i.getNotes(), i.statusOn(today), ChronoUnit.DAYS.between(today, i.getExpiresOn()),
                i.getLegalEntityId(), entityName, i.getLicensedActivity(), i.getIssuingAuthority(), evidenceCount);
    }
}
