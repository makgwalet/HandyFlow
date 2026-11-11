package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
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
import java.util.List;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public List<ComplianceResponse> list(TenantId tenantId) {
        LocalDate today = LocalDate.now(SAST);
        return items.findAllActive(tenantId).stream().map(i -> toResponse(i, today)).toList();
    }

    @Transactional
    public ComplianceResponse create(TenantId tenantId, ComplianceRequest req) {
        String label = resolveLabel(tenantId, req.subjectType(), req.subjectId(), req.subjectLabel());
        FuelComplianceItem i = FuelComplianceItem.create(tenantId, req.itemType(), req.title(), req.referenceNo(), req.subjectType(),
                req.subjectId(), label, req.issuedOn(), req.expiresOn(), req.notes());
        return toResponse(items.save(i), LocalDate.now(SAST));
    }

    /** What the item is (type, title, dates, notes); who it is for cannot change: add a new item instead. */
    @Transactional
    public ComplianceResponse update(TenantId tenantId, UUID id, ComplianceRequest req) {
        FuelComplianceItem i = find(tenantId, id);
        i.update(req.itemType(), req.title(), req.referenceNo(), req.issuedOn(), req.expiresOn(), req.notes());
        return toResponse(items.save(i), LocalDate.now(SAST));
    }

    @Transactional
    public ComplianceResponse renew(TenantId tenantId, UUID id, RenewRequest req, UUID by) {
        FuelComplianceItem i = find(tenantId, id);
        LocalDate previous = i.renew(req.newExpiresOn(), req.issuedOn());
        items.save(i);
        renewals.save(FuelComplianceRenewal.of(tenantId, id, previous, req.newExpiresOn(), by, req.note()));
        return toResponse(i, LocalDate.now(SAST));
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

    private ComplianceResponse toResponse(FuelComplianceItem i, LocalDate today) {
        return new ComplianceResponse(i.getId(), i.getItemType(), i.getTitle(), i.getReferenceNo(), i.getSubjectType(), i.getSubjectId(),
                i.getSubjectLabel(), i.getIssuedOn(), i.getExpiresOn(), i.getNotes(), i.statusOn(today), ChronoUnit.DAYS.between(today, i.getExpiresOn()));
    }
}
