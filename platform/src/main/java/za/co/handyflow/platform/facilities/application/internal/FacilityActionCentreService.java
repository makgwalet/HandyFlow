package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionSchedule;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendor;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.facilities.domain.repository.FacilityComplianceCertificateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityInspectionScheduleRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityInspectionTemplateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorDocumentRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityVendorRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityCorrectiveActionRepository;
import za.co.handyflow.platform.facilities.dto.ActionCentreResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The compliance action centre: certificates and corrective actions in the caller's site scope, ranked by severity. */
@Service
@RequiredArgsConstructor
public class FacilityActionCentreService {

    static final int WARNING_DAYS = 30;

    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityCorrectiveActionRepository actionRepository;
    private final FacilityVendorDocumentRepository vendorDocumentRepository;
    private final FacilityVendorRepository vendorRepository;
    private final FacilityInspectionScheduleRepository inspectionScheduleRepository;
    private final FacilityInspectionTemplateRepository templateRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public ActionCentreResponse get(TenantId tenantId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        LocalDate today = FacilityTime.today();
        List<FacilityVendorDocument> vendorDocs = vendorDocumentRepository.findLapsedOrExpiringForActiveVendors(tenantId, today.plusDays(WARNING_DAYS));
        Map<UUID, String> vendorNames = new HashMap<>();
        if (!vendorDocs.isEmpty()) {
            for (FacilityVendor v : vendorRepository.findActiveByIds(tenantId, vendorDocs.stream().map(FacilityVendorDocument::getVendorId).distinct().toList())) {
                vendorNames.put(v.getId(), v.getCompanyName());
            }
        }
        List<FacilityInspectionSchedule> overdueInspections = inspectionScheduleRepository.findOverdueInScope(tenantId, today, scope.allSites(), scope.siteIds());
        Map<UUID, String> labels = new HashMap<>();
        if (!overdueInspections.isEmpty()) {
            Map<UUID, String> templates = new HashMap<>();
            templateRepository.findAllForTenant(tenantId, true).forEach(t -> templates.put(t.getId(), t.getName()));
            Map<UUID, String> sites = new HashMap<>();
            for (FacilityInspectionSchedule s : overdueInspections) {
                String site = sites.computeIfAbsent(s.getSiteId(), id -> siteRepository.findActiveById(tenantId, id).map(FacilitySite::getName).orElse("site"));
                labels.put(s.getId(), templates.getOrDefault(s.getTemplateId(), "Checklist") + " at " + site);
            }
        }
        return FacilityActionCentre.build(
                certificateRepository.findLapsedOrExpiringInScope(tenantId, today.plusDays(WARNING_DAYS), scope.allSites(), scope.siteIds()),
                actionRepository.findUnfinishedInScope(tenantId, scope.allSites(), scope.siteIds()),
                vendorDocs, vendorNames, overdueInspections, labels, today, WARNING_DAYS);
    }
}
