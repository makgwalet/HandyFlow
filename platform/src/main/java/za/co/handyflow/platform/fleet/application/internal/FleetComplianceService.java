package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.ComplianceStatus;
import za.co.handyflow.platform.fleet.domain.model.InspectionStatus;
import za.co.handyflow.platform.fleet.domain.model.InspectionTemplate;
import za.co.handyflow.platform.fleet.domain.model.IssueStatus;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocument;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentType;
import za.co.handyflow.platform.fleet.domain.model.VehicleInsurance;
import za.co.handyflow.platform.fleet.domain.repository.FleetIssueRepository;
import za.co.handyflow.platform.fleet.domain.repository.InspectionRepository;
import za.co.handyflow.platform.fleet.domain.repository.InspectionTemplateRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleDocumentRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleInsuranceRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.ComplianceItemResponse;
import za.co.handyflow.platform.fleet.dto.VehicleComplianceResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-vehicle compliance board: licence disc, roadworthy, insurance, tracking, service.
 * Expiry dates come from the vehicle itself (the single source the scheduler alerts on,
 * kept in sync by the vault and insurance profile); this service adds the traffic-light
 * status and links each item to its current vault document. The inspection item reports
 * DUE when a scheduled template (one with intervalDays) that applies to this vehicle has
 * no completed inspection inside its interval.
 */
@Service
@RequiredArgsConstructor
public class FleetComplianceService {

    private final VehicleRepository vehicleRepository;
    private final VehicleDocumentRepository documentRepository;
    private final VehicleInsuranceRepository insuranceRepository;
    private final InspectionTemplateRepository inspectionTemplateRepository;
    private final InspectionRepository inspectionRepository;
    private final FleetIssueRepository issueRepository;

    @Transactional(readOnly = true)
    public VehicleComplianceResponse getCompliance(TenantId tenantId, UUID vehicleId) {
        Vehicle v = vehicleRepository.findActiveById(tenantId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", vehicleId.toString()));

        Map<VehicleDocumentType, UUID> currentDocs = new EnumMap<>(VehicleDocumentType.class);
        for (VehicleDocument d : documentRepository.findByVehicleAndStatus(tenantId, vehicleId, VehicleDocumentStatus.CURRENT)) {
            currentDocs.putIfAbsent(d.getType(), d.getId());   // list is newest-first
        }
        VehicleInsurance ins = insuranceRepository.findByTenantIdAndVehicleId(tenantId, vehicleId).orElse(null);

        List<ComplianceItemResponse> items = new ArrayList<>();
        items.add(expiryItem("LICENCE_DISC", "Licence disc", v.getLicenceDiscExpiry(),
                currentDocs.get(VehicleDocumentType.LICENCE_DISC), null));
        items.add(expiryItem("ROADWORTHY", "Roadworthy certificate", v.getRoadworthyExpiry(),
                currentDocs.get(VehicleDocumentType.ROADWORTHY), null));

        if (ins != null && !ins.isInsured()) {
            items.add(new ComplianceItemResponse("INSURANCE", "Insurance", ComplianceStatus.MISSING,
                    null, null, currentDocs.get(VehicleDocumentType.INSURANCE),
                    "Vehicle is recorded as not insured"));
        } else {
            items.add(expiryItem("INSURANCE", "Insurance", v.getInsuranceExpiry(),
                    currentDocs.get(VehicleDocumentType.INSURANCE),
                    ins != null && ins.getInsurer() != null ? ins.getInsurer() : null));
        }

        items.add(trackingItem(ins, currentDocs.get(VehicleDocumentType.TRACKING_CERTIFICATE)));
        items.add(serviceItem(v));
        items.add(inspectionItem(tenantId, v));

        ComplianceStatus overall = items.stream()
                .map(ComplianceItemResponse::status)
                .min(Comparator.comparingInt(ComplianceStatus::severity))
                .orElse(ComplianceStatus.NOT_APPLICABLE);
        return new VehicleComplianceResponse(v.getId(), v.getRegistration(), overall, items);
    }

    private ComplianceItemResponse expiryItem(String key, String label, LocalDate expiry,
                                              UUID documentId, String detail) {
        if (expiry == null) {
            return new ComplianceItemResponse(key, label, ComplianceStatus.MISSING, null, null,
                    documentId, "No expiry date recorded");
        }
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, expiry);
        // Same 30-day window the vehicle's own isXExpiringSoon() flags use.
        ComplianceStatus status = days < 0 ? ComplianceStatus.EXPIRED
                : expiry.isBefore(today.plusDays(30)) ? ComplianceStatus.EXPIRING_SOON
                : ComplianceStatus.VALID;
        return new ComplianceItemResponse(key, label, status, expiry, days, documentId, detail);
    }

    private ComplianceItemResponse trackingItem(VehicleInsurance ins, UUID certificateDocId) {
        if (ins == null) {
            return new ComplianceItemResponse("TRACKING", "Tracking", ComplianceStatus.NOT_APPLICABLE,
                    null, null, certificateDocId, "No insurance profile recorded");
        }
        if (!ins.isTrackingRequired()) {
            return new ComplianceItemResponse("TRACKING", "Tracking", ComplianceStatus.NOT_APPLICABLE,
                    null, null, certificateDocId, "Not required by insurer");
        }
        if (ins.hasTrackingDevice()) {
            String provider = ins.getTrackingProvider() != null ? ins.getTrackingProvider() + " — " : "";
            return new ComplianceItemResponse("TRACKING", "Tracking", ComplianceStatus.VALID,
                    null, null, certificateDocId, provider + "device " + ins.getTrackingDeviceNumber());
        }
        return new ComplianceItemResponse("TRACKING", "Tracking", ComplianceStatus.MISSING,
                null, null, certificateDocId, "Tracking required by insurer but no device recorded");
    }

    private ComplianceItemResponse serviceItem(Vehicle v) {
        int sinceService = v.getCurrentOdometer() - v.getLastServiceKm();
        boolean due = v.isDueForService();
        return new ComplianceItemResponse("SERVICE", "Service",
                due ? ComplianceStatus.DUE : ComplianceStatus.VALID, null, null, null,
                sinceService + " km since last service (interval " + v.getServiceIntervalKm() + " km"
                        + (v.getServiceIntervalDays() != null ? " / " + v.getServiceIntervalDays() + " days" : "") + ")");
    }

    private ComplianceItemResponse inspectionItem(TenantId tenantId, Vehicle v) {
        long openIssues = issueRepository.countByVehicleAndStatuses(tenantId, v.getId(), IssueStatus.ACTIVE);
        String issues = openIssues > 0 ? openIssues + " open issue(s)" : null;

        List<InspectionTemplate> scheduled = inspectionTemplateRepository.findScheduled(tenantId).stream()
                .filter(t -> t.getAppliesToVehicleType() == null
                        || t.getAppliesToVehicleType().equalsIgnoreCase(v.getVehicleType()))
                .toList();
        if (scheduled.isEmpty()) {
            return new ComplianceItemResponse("INSPECTION", "Inspection", ComplianceStatus.NOT_APPLICABLE,
                    null, null, null, join("No inspection schedule applies", issues));
        }

        Instant now = Instant.now();
        List<String> overdue = new ArrayList<>();
        for (InspectionTemplate t : scheduled) {
            Instant last = inspectionRepository.findLastCompletedAt(
                    tenantId, v.getId(), t.getId(), InspectionStatus.COMPLETED);
            if (last == null || last.plus(t.getIntervalDays(), ChronoUnit.DAYS).isBefore(now)) {
                overdue.add(t.getName());
            }
        }
        if (overdue.isEmpty()) {
            return new ComplianceItemResponse("INSPECTION", "Inspection", ComplianceStatus.VALID,
                    null, null, null, join("All scheduled inspections up to date", issues));
        }
        return new ComplianceItemResponse("INSPECTION", "Inspection", ComplianceStatus.DUE,
                null, null, null, join("Overdue: " + String.join(", ", overdue), issues));
    }

    private String join(String a, String b) {
        return b == null ? a : a + " · " + b;
    }
}