package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.MaintenanceRecord;
import za.co.handyflow.platform.earthmoving.domain.model.OperatorLog;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarthAssetService {

    private final EarthAssetRepository  assetRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final OperatorLogRepository operatorLogRepository;

    // ── Assets ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AssetResponse> getAssets(TenantId tenantId, String status,
                                         String assetType, Pageable pageable) {
        // Both filters null — return everything
        if ((status == null || status.isBlank()) && (assetType == null || assetType.isBlank()))
            return assetRepository.findAllActive(tenantId, pageable).map(this::toResponse);
        // Status filter only
        if (assetType == null || assetType.isBlank())
            return assetRepository.findByStatus(tenantId, status.toUpperCase(), pageable).map(this::toResponse);
        // Type filter only
        if (status == null || status.isBlank())
            return assetRepository.findByType(tenantId, assetType.toUpperCase(), pageable).map(this::toResponse);
        // Both filters
        return assetRepository.findByStatusAndType(tenantId, status.toUpperCase(),
                assetType.toUpperCase(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AssetResponse getAsset(TenantId tenantId, UUID id) {
        return assetRepository.findActiveById(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id.toString()));
    }

    @Transactional
    public AssetResponse createAsset(TenantId tenantId, CreateAssetRequest req) {
        EarthAsset asset = EarthAsset.create(
                tenantId,
                req.name(),
                req.fleetNumber(),
                req.assetType(),
                req.make(), req.model(), req.year(),
                req.serialNumber(), req.registration(),
                req.ownershipType() != null ? req.ownershipType() : "OWN",
                req.hireSupplier(), req.hireStartDate(), req.hireEndDate(),
                req.dailyRate(), req.hourlyRate(),
                req.notes()
        );
        assetRepository.save(asset);
        log.info("Registered earthmoving asset={} fleet={} tenant={}",
                asset.getName(), asset.getFleetNumber(), tenantId);
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateStatus(TenantId tenantId, UUID id,
                                      UpdateAssetStatusRequest req) {
        EarthAsset asset = findActive(tenantId, id);
        String status = req.status().toUpperCase();
        switch (status) {
            case "AVAILABLE"   -> asset.returnToYard();
            case "DEPLOYED"    -> asset.deploy();
            case "MAINTENANCE" -> asset.sendToMaintenance();
            case "BREAKDOWN"   -> asset.breakdown();   // NEW — machines break down
            case "HIRED_OUT"   -> asset.hireOut();     // NEW — lend to third party
            case "RETIRED"     -> asset.retire();
            default -> throw new IllegalArgumentException("Unknown status: " + req.status() +
                    ". Valid values: AVAILABLE, DEPLOYED, MAINTENANCE, BREAKDOWN, HIRED_OUT, RETIRED");
        }
        assetRepository.save(asset);
        log.info("Asset status changed asset={} status={}", id, status);
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateHours(TenantId tenantId, UUID id, UpdateHoursRequest req) {
        EarthAsset asset = findActive(tenantId, id);
        asset.updateHours(req.currentHours());
        assetRepository.save(asset);
        log.info("Hour meter updated asset={} hours={}", id, req.currentHours());
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse deploy(TenantId tenantId, UUID id, DeployAssetRequest req) {
        EarthAsset asset = findActive(tenantId, id);
        asset.deployTo(req.siteName(), req.clientName());
        assetRepository.save(asset);
        log.info("Asset deployed asset={} site={} client={}", id, req.siteName(), req.clientName());
        return toResponse(asset);
    }

    @Transactional
    public void deleteAsset(TenantId tenantId, UUID id) {
        EarthAsset asset = findActive(tenantId, id);
        asset.softDelete(null);
        assetRepository.save(asset);
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getMaintenanceHistory(TenantId tenantId,
                                                           UUID assetId,
                                                           Pageable pageable) {
        findActive(tenantId, assetId);
        return maintenanceRepository.findByAsset(assetId, pageable)
                .map(this::toMaintenanceResponse);
    }

    @Transactional
    public MaintenanceResponse recordMaintenance(TenantId tenantId, UUID assetId,
                                                 CreateMaintenanceRequest req) {
        EarthAsset asset = findActive(tenantId, assetId);
        MaintenanceRecord record = MaintenanceRecord.create(
                tenantId, assetId, req.type(), req.description(),
                req.performedAt(), req.hoursAtService(),
                req.cost(), req.supplier(), req.invoiceRef()
        );
        maintenanceRepository.save(record);

        // Only reset service clock on SERVICE type
        if ("SERVICE".equalsIgnoreCase(req.type()) && req.hoursAtService() != null) {
            asset.recordService(req.hoursAtService());
            // If machine was in MAINTENANCE status, return it to AVAILABLE
            if ("MAINTENANCE".equals(asset.getStatus())) {
                asset.returnToYard();
            }
            assetRepository.save(asset);
        }
        log.info("Maintenance recorded asset={} type={}", assetId, req.type());
        return toMaintenanceResponse(record);
    }

    // ── Operator Logs ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<OperatorLogResponse> getOperatorLogs(TenantId tenantId,
                                                     UUID assetId,
                                                     Pageable pageable) {
        findActive(tenantId, assetId);
        return operatorLogRepository.findByAsset(assetId, pageable)
                .map(this::toLogResponse);
    }

    @Transactional
    public OperatorLogResponse startOperatorLog(TenantId tenantId, UUID assetId,
                                                CreateOperatorLogRequest req) {
        findActive(tenantId, assetId);
        OperatorLog opLog = OperatorLog.create(
                tenantId, assetId,
                req.guardId(), req.operatorName(), req.siteName(),
                req.startedAt(), req.startHours()
        );
        operatorLogRepository.save(opLog);
        return toLogResponse(opLog);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EarthAsset findActive(TenantId tenantId, UUID id) {
        return assetRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id.toString()));
    }

    private AssetResponse toResponse(EarthAsset a) {
        return new AssetResponse(
                a.getId(),
                a.getName(),
                a.getFleetNumber(),
                a.getAssetType(),
                a.getMake(), a.getModel(), a.getYear(),
                a.getSerialNumber(), a.getRegistration(),
                a.getOwnershipType() != null ? a.getOwnershipType() : "OWN",
                a.getHireSupplier(), a.getHireStartDate(), a.getHireEndDate(),
                a.getStatus(),
                a.getCurrentSite(),
                a.getCurrentClient(),
                a.getDailyRate(), a.getHourlyRate(),
                a.getCurrentHours(), a.getLastServiceHours(),
                a.getServiceIntervalHours(), a.isDueForService(),
                a.getNotes(), a.getCreatedAt()
        );
    }

    private MaintenanceResponse toMaintenanceResponse(MaintenanceRecord m) {
        return new MaintenanceResponse(
                m.getId(), m.getAssetId(), m.getType(), m.getDescription(),
                m.getPerformedAt(), m.getHoursAtService(),
                m.getCost(), m.getSupplier(), m.getInvoiceRef(), m.getCreatedAt()
        );
    }

    private OperatorLogResponse toLogResponse(OperatorLog l) {
        return new OperatorLogResponse(
                l.getId(), l.getAssetId(), l.getOperatorName(), l.getSiteName(),
                l.getStartedAt(), l.getEndedAt(), l.getHoursLogged(),
                l.getFuelUsedLitres(), l.getCreatedAt()
        );
    }
}
