package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.FacilityAssetReliabilityResponse;
import za.co.handyflow.platform.facilities.dto.FacilityVendorPerformanceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;

/**
 * CSV exports of the management reports and registers. Manager views only (callers limited to their own jobs or requests
 * are refused), limited to the caller's sites, and audited because they put cost and compliance data into a file. A
 * result larger than {@value #MAX_ROWS} rows is refused rather than silently cut short.
 */
@Service
@RequiredArgsConstructor
public class FacilityExportService {

    static final int MAX_ROWS = 5000;

    public record Export(String filename, String csv) {}

    private final FacilityReportService reportService;
    private final FacilityComplianceCertificateRepository certificateRepository;
    private final FacilityCorrectiveActionRepository actionRepository;
    private final FacilityTechnicianLicenceRepository licenceRepository;
    private final FacilityTechnicianRepository technicianRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;
    private final FacilityStockValuationService valuationService;

    @Transactional
    public Export workOrders(TenantId tenantId, LocalDate from, LocalDate to) {
        List<FacilityWorkOrder> orders = reportService.workOrdersCreatedBetween(tenantId, from, to);
        requireWithinLimit(orders.size());
        AccessScope scope = accessPolicy.scope(tenantId);
        Map<UUID, String> sites = siteNames(tenantId, scope);
        Map<UUID, FacilityAsset> assets = new HashMap<>();
        List<UUID> assetIds = orders.stream().map(FacilityWorkOrder::getAssetId).filter(Objects::nonNull).distinct().toList();
        if (!assetIds.isEmpty()) assetRepository.findAllById(assetIds).forEach(a -> assets.put(a.getId(), a));
        List<List<?>> rows = new ArrayList<>();
        for (FacilityWorkOrder w : orders) {
            FacilityAsset a = assets.get(w.getAssetId());
            rows.add(Arrays.asList(w.getWorkOrderNumber(), sites.get(w.getSiteId()), a == null ? null : a.getAssetTag(),
                    a == null ? null : a.getName(), w.getCategory(), w.getPriority(), w.getStatus(), w.getDescription(),
                    w.getReportedBy(), w.getTechnicianName(), w.getVendorName(), w.getScheduledDate(),
                    w.getCreatedAt() == null ? null : FacilityTime.toLocalDate(w.getCreatedAt()),
                    w.getCompletedAt() == null ? null : FacilityTime.toLocalDate(w.getCompletedAt()),
                    w.getReworkCount(), w.getDowntimeMinutes(), w.getFailureCause(), w.getCost()));
        }
        return done(tenantId, "work-orders", from, to, FacilityCsv.build(List.of("Work order", "Site", "Asset tag", "Asset", "Category",
                "Priority", "Status", "Description", "Reported by", "Technician", "Vendor", "Scheduled", "Created", "Completed",
                "Rework count", "Downtime (min)", "Failure cause", "Cost"), rows), rows.size());
    }

    @Transactional
    public Export vendorPerformance(TenantId tenantId, LocalDate from, LocalDate to) {
        FacilityVendorPerformanceResponse r = reportService.vendorPerformance(tenantId, from, to);
        List<List<?>> rows = new ArrayList<>();
        for (FacilityVendorPerformanceResponse.Row v : r.vendors()) {
            rows.add(Arrays.asList(v.vendorName(), v.jobs(), v.completed(), v.cancelled(), v.open(), v.avgCompletionHours(),
                    v.reworkJobs(), v.reworkRatePercent(), v.totalCost(), v.avgCost()));
        }
        return done(tenantId, "vendor-performance", from, to, FacilityCsv.build(List.of("Vendor", "Jobs", "Completed", "Cancelled",
                "Open", "Avg completion (h)", "Rework jobs", "Rework rate (%)", "Total cost", "Avg cost"), rows), rows.size());
    }

    @Transactional
    public Export assetReliability(TenantId tenantId, LocalDate from, LocalDate to) {
        FacilityAssetReliabilityResponse r = reportService.assetReliability(tenantId, from, to, FacilityReportService.MAX_ASSET_LIMIT);
        List<List<?>> rows = new ArrayList<>();
        for (FacilityAssetReliabilityResponse.Row a : r.assets()) {
            rows.add(Arrays.asList(a.assetTag(), a.assetName(), a.failures(), a.repeatFailures(), a.downtimeMinutes(),
                    a.mttrHours(), a.mtbfHours(), a.topFailureCause(), a.totalCost()));
        }
        return done(tenantId, "asset-reliability", from, to, FacilityCsv.build(List.of("Asset tag", "Asset", "Failures",
                "Repeat failures", "Downtime (min)", "MTTR (h)", "MTBF (h)", "Top cause", "Cost"), rows), rows.size());
    }

    @Transactional
    public Export certificates(TenantId tenantId) {
        AccessScope scope = requireManagementScope(tenantId);
        Page<FacilityComplianceCertificate> page = certificateRepository.findAllInScope(tenantId, null, scope.allSites(),
                scope.siteIds(), PageRequest.of(0, MAX_ROWS));
        requireWithinLimit(page.getTotalElements());
        Map<UUID, String> sites = siteNames(tenantId, scope);
        LocalDate today = FacilityTime.today();
        List<List<?>> rows = new ArrayList<>();
        for (FacilityComplianceCertificate c : page.getContent()) {
            rows.add(Arrays.asList(sites.get(c.getSiteId()), c.getCertificateType(), c.getCertificateNumber(), c.getIssuedBy(),
                    c.getIssueDate(), c.getExpiryDate(), c.getExpiryDate() == null ? null : java.time.temporal.ChronoUnit.DAYS.between(today, c.getExpiryDate()),
                    c.getStatus()));
        }
        return done(tenantId, "certificates", null, null, FacilityCsv.build(List.of("Site", "Type", "Number", "Issued by", "Issued",
                "Expires", "Days to expiry", "Status"), rows), rows.size());
    }

    @Transactional
    public Export correctiveActions(TenantId tenantId, String status) {
        AccessScope scope = requireManagementScope(tenantId);
        String s = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        Page<FacilityCorrectiveAction> page = actionRepository.findAllInScope(tenantId, null, s, scope.allSites(), scope.siteIds(),
                PageRequest.of(0, MAX_ROWS));
        requireWithinLimit(page.getTotalElements());
        Map<UUID, String> sites = siteNames(tenantId, scope);
        LocalDate today = FacilityTime.today();
        List<List<?>> rows = new ArrayList<>();
        for (FacilityCorrectiveAction a : page.getContent()) {
            rows.add(Arrays.asList(sites.get(a.getSiteId()), a.getSourceType(), a.getDescription(), a.getPriority(), a.isCritical(),
                    a.getStatus(), a.getDueDate(), a.isOverdue(today), a.getClosureNote(),
                    a.getClosedAt() == null ? null : FacilityTime.toLocalDate(a.getClosedAt()),
                    a.getCreatedAt() == null ? null : FacilityTime.toLocalDate(a.getCreatedAt())));
        }
        return done(tenantId, "corrective-actions", null, null, FacilityCsv.build(List.of("Site", "Source", "Description", "Priority",
                "Critical", "Status", "Due", "Overdue", "Closure note", "Closed", "Raised"), rows), rows.size());
    }

    @Transactional
    public Export stockValuation(TenantId tenantId, UUID storeId) {
        requireManagementScope(tenantId);
        var v = valuationService.valuation(tenantId, storeId);
        requireWithinLimit(v.lines().size());
        List<List<?>> rows = new ArrayList<>();
        for (var l : v.lines()) {
            rows.add(Arrays.asList(l.storeCode(), l.sku(), l.name(), l.unit(), l.onHand(), l.unitCost(), l.value(), l.itemActive()));
        }
        rows.add(Arrays.asList("TOTAL", null, null, null, null, null, v.totalValue(), null));
        return done(tenantId, "stock-valuation", null, null, FacilityCsv.build(List.of("Store", "SKU", "Item", "Unit", "On hand",
                "Average cost", "Value", "Item active"), rows), v.lines().size());
    }

    @Transactional
    public Export technicianLicences(TenantId tenantId) {
        requireManagementScope(tenantId);
        List<FacilityTechnicianLicence> licences = licenceRepository.findAllForTenant(tenantId);
        requireWithinLimit(licences.size());
        Map<UUID, String> names = new HashMap<>();
        technicianRepository.findAllActive(tenantId, PageRequest.of(0, MAX_ROWS)).forEach(t -> names.put(t.getId(), t.getName()));
        LocalDate today = FacilityTime.today();
        List<List<?>> rows = new ArrayList<>();
        for (FacilityTechnicianLicence l : licences) {
            rows.add(Arrays.asList(names.get(l.getTechnicianId()), l.getLicenceType(), l.getReference(), l.getIssuedBy(), l.getIssueDate(),
                    l.getExpiryDate(), l.getExpiryDate() == null ? null : java.time.temporal.ChronoUnit.DAYS.between(today, l.getExpiryDate()),
                    l.getStatus(), l.isInForce(today)));
        }
        return done(tenantId, "technician-licences", null, null, FacilityCsv.build(List.of("Technician", "Licence", "Reference",
                "Issued by", "Issued", "Expires", "Days to expiry", "Status", "In force"), rows), rows.size());
    }

    private Export done(TenantId tenantId, String report, LocalDate from, LocalDate to, String csv, int rows) {
        auditService.record(tenantId, "REPORT_EXPORTED", "REPORT", null,
                "report=" + report + (from != null ? " from=" + from + " to=" + to : "") + " rows=" + rows);
        String name = "facilities-" + report + (from != null ? "-" + from + "-to-" + to : "-" + FacilityTime.today()) + ".csv";
        return new Export(name, csv);
    }

    private AccessScope requireManagementScope(TenantId tenantId) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (scope.assignedOnly() || scope.requesterOnly())
            throw new HandyFlowException("Not permitted for this access level", HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        return scope;
    }

    private static void requireWithinLimit(long rows) {
        if (rows > MAX_ROWS)
            throw new IllegalArgumentException("This export has " + rows + " rows; the limit is " + MAX_ROWS + ". Narrow the period or filter.");
    }

    private Map<UUID, String> siteNames(TenantId tenantId, AccessScope scope) {
        Map<UUID, String> names = new HashMap<>();
        siteRepository.findAllActiveInScope(tenantId, scope.allSites(), scope.siteIds(), PageRequest.of(0, MAX_ROWS))
                .forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }
}
