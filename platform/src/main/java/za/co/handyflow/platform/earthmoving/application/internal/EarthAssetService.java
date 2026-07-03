package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDeployment;
import za.co.handyflow.platform.earthmoving.domain.model.MaintenanceRecord;
import za.co.handyflow.platform.earthmoving.domain.model.OperatorLog;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarthAssetService {

    private final EarthAssetRepository assetRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final OperatorLogRepository operatorLogRepository;
    private final EarthDeploymentRepository deploymentRepository;
    private final NotificationService notificationService;
    // FIX: was a bespoke earthmoving-only FleetNotificationRecipients port,
    // permanently backed by a no-op stub. TenantAdminRecipients already
    // exists as the shared, cross-module version of exactly this problem
    // (see TenantAdminRecipientsImpl in the identity module) — earthmoving
    // now depends on that instead of maintaining its own parallel,
    // never-implemented port. This is what actually turns notifications on.
    private final TenantAdminRecipients tenantAdminRecipients;

    // ── Assets ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AssetResponse> getAssets(TenantId tenantId, String status,
                                         String assetType, Pageable pageable) {
        if ((status == null || status.isBlank()) && (assetType == null || assetType.isBlank()))
            return assetRepository.findAllActive(tenantId, pageable).map(this::toResponse);
        if (assetType == null || assetType.isBlank())
            return assetRepository.findByStatus(tenantId, parseStatus(status), pageable).map(this::toResponse);
        if (status == null || status.isBlank())
            return assetRepository.findByType(tenantId, assetType.toUpperCase(), pageable).map(this::toResponse);
        return assetRepository.findByStatusAndType(tenantId, parseStatus(status),
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
        // FIX: fleet_number is now unique per tenant at the DB level (see
        // uq_earthmoving_assets_tenant_fleet_number). Checking here first
        // means a duplicate produces a clear 400 with the actual fleet
        // number named, instead of a raw constraint-violation stack trace
        // surfacing as a generic 500. The DB constraint remains the real
        // guarantee against a race between two concurrent creates — this is
        // just a friendlier fast path in front of it.
        if (req.fleetNumber() != null && !req.fleetNumber().isBlank()
                && assetRepository.existsActiveByFleetNumber(tenantId, req.fleetNumber())) {
            throw new IllegalArgumentException(
                    "Fleet number \"" + req.fleetNumber() + "\" is already in use by another asset.");
        }

        EarthAsset asset = EarthAsset.create(
                tenantId, req.name(), req.fleetNumber(), req.assetType(),
                req.make(), req.model(), req.year(),
                req.serialNumber(), req.registration(),
                req.ownershipType() != null ? req.ownershipType() : "OWN",
                req.hireSupplier(), req.hireStartDate(), req.hireEndDate(),
                req.dailyRate(), req.hourlyRate(), req.notes()
        );
        assetRepository.save(asset);
        log.info("Registered earthmoving asset={} fleet={} tenant={}",
                asset.getName(), asset.getFleetNumber(), tenantId);
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateStatus(TenantId tenantId, UUID id, UpdateAssetStatusRequest req) {
        EarthAsset asset = findActive(tenantId, id);
        AssetStatus previousStatus = asset.getStatus();
        AssetStatus target = parseStatus(req.status());

        switch (target) {
            case AVAILABLE -> asset.returnToYard();
            case DEPLOYED -> asset.markDeployed();
            case MAINTENANCE -> asset.sendToMaintenance();
            case BREAKDOWN -> asset.breakdown();
            case HIRED_OUT -> asset.hireOut();
            case RETIRED -> asset.retire();
        }
        // NOTE: EarthAsset.changeStatus() throws InvalidAssetStatusTransitionException
        // (a subclass of IllegalStateException) if the transition isn't legal from
        // the asset's current state — see AssetStatus for the full transition table.
        // The global exception handler maps that to HTTP 409 Conflict.

        assetRepository.save(asset);
        log.info("Asset status changed asset={} status={}", id, target);

        // Close the deployment record whenever the asset LEAVES DEPLOYED, for
        // any reason — not only when it's formally "returned to yard". A
        // machine that breaks down or gets pulled into maintenance mid-job
        // has, just as much, stopped being deployed. See EarthDeployment's
        // Javadoc for why this matters (without it, a broken-down machine
        // would show as "still deployed" forever).
        if (previousStatus == AssetStatus.DEPLOYED && target != AssetStatus.DEPLOYED) {
            closeOpenDeploymentIfAny(tenantId, id, target.name());
        }

        if (target == AssetStatus.BREAKDOWN) {
            notifyBreakdown(tenantId, asset);
        }
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateHours(TenantId tenantId, UUID id, UpdateHoursRequest req) {
        EarthAsset asset = findActive(tenantId, id);
        boolean wasDue = asset.isDueForService();

        asset.updateHours(req.currentHours());
        assetRepository.save(asset);
        log.info("Hour meter updated asset={} hours={}", id, req.currentHours());

        // Only notify on the transition into "due", not on every subsequent
        // hour update while it remains due — otherwise every reading after
        // the threshold spams a fresh notification.
        if (!wasDue && asset.isDueForService()) {
            notifyServiceDue(tenantId, asset);
        }
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse deploy(TenantId tenantId, UUID id, DeployAssetRequest req) {
        EarthAsset asset = findActive(tenantId, id);
        asset.deployTo(req.siteName(), req.clientName());
        // NOTE: throws InvalidAssetStatusTransitionException if the asset isn't
        // currently AVAILABLE — the original code let you "deploy" a machine
        // that was mid-service or already deployed elsewhere.
        assetRepository.save(asset);

        // FIX: contactName/contactPhone/startDate/expectedEndDate/notes were
        // accepted by this DTO from day one but never persisted anywhere —
        // only siteName/clientName made it onto the asset. See EarthDeployment
        // for the full explanation; this is now a proper history record.
        EarthDeployment deployment = EarthDeployment.create(
                tenantId, id, req.siteName(), req.clientName(),
                req.contactName(), req.contactPhone(),
                req.startDate(), req.expectedEndDate(), req.notes()
        );
        deploymentRepository.save(deployment);

        log.info("Asset deployed asset={} site={} client={}", id, req.siteName(), req.clientName());
        return toResponse(asset);
    }

    @Transactional
    public void deleteAsset(TenantId tenantId, UUID id, UUID deletedByUserId) {
        EarthAsset asset = findActive(tenantId, id);
        // FIX: previously always passed null here, losing who deleted the asset.
        asset.softDelete(deletedByUserId);
        assetRepository.save(asset);
    }

    // ── Deployments ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DeploymentResponse> getDeploymentHistory(TenantId tenantId, UUID assetId, Pageable pageable) {
        findActive(tenantId, assetId);
        return deploymentRepository.findByAsset(tenantId, assetId, pageable).map(this::toDeploymentResponse);
    }

    /**
     * No-op if there's no open deployment for this asset — that's the
     * expected, common case for every status transition that ISN'T "leaving
     * DEPLOYED" (this is only called from those specific transitions, but
     * stays defensive rather than assuming a row must exist).
     */
    private void closeOpenDeploymentIfAny(TenantId tenantId, UUID assetId, String endReason) {
        deploymentRepository.findOpenForAsset(tenantId, assetId)
                .ifPresent(d -> {
                    d.close(endReason);
                    log.info("Deployment closed asset={} reason={}", assetId, endReason);
                });
    }

    // ── Maintenance ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getMaintenanceHistory(TenantId tenantId, UUID assetId, Pageable pageable) {
        findActive(tenantId, assetId);
        return maintenanceRepository.findByAsset(assetId, pageable).map(this::toMaintenanceResponse);
    }

    @Transactional
    public MaintenanceResponse recordMaintenance(TenantId tenantId, UUID assetId, CreateMaintenanceRequest req) {
        EarthAsset asset = findActive(tenantId, assetId);
        MaintenanceRecord record = MaintenanceRecord.create(
                tenantId, assetId, req.type(), req.description(),
                req.performedAt(), req.hoursAtService(),
                req.cost(), req.supplier(), req.invoiceRef()
        );
        maintenanceRepository.save(record);

        if ("SERVICE".equalsIgnoreCase(req.type()) && req.hoursAtService() != null) {
            asset.recordService(req.hoursAtService());
            if (asset.getStatus() == AssetStatus.MAINTENANCE) {
                // Any deployment this asset had would already have been closed
                // when it transitioned INTO MAINTENANCE (see updateStatus) —
                // nothing further to do here regarding deployment history.
                asset.returnToYard();
            }
            assetRepository.save(asset);
        }
        log.info("Maintenance recorded asset={} type={}", assetId, req.type());
        notifyMaintenanceRecorded(tenantId, asset, record);
        return toMaintenanceResponse(record);
    }

    // ── Operator Logs ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<OperatorLogResponse> getOperatorLogs(TenantId tenantId, UUID assetId, Pageable pageable) {
        findActive(tenantId, assetId);
        return operatorLogRepository.findByAsset(assetId, pageable).map(this::toLogResponse);
    }

    @Transactional
    public OperatorLogResponse startOperatorLog(TenantId tenantId, UUID assetId, CreateOperatorLogRequest req) {
        findActive(tenantId, assetId);

        // FIX: nothing previously stopped a second shift starting on a machine
        // that already had one open — you'd end up with two concurrent
        // "active" operators on record for the same machine.
        operatorLogRepository.findOpenLogForAsset(assetId).ifPresent(open -> {
            throw new IllegalStateException(
                    "Asset already has an open operator shift (started " + open.getStartedAt()
                            + ") — complete it before starting a new one.");
        });

        OperatorLog opLog = OperatorLog.create(
                tenantId, assetId, req.guardId(), req.operatorName(), req.siteName(),
                req.startedAt(), req.startHours()
        );
        operatorLogRepository.save(opLog);
        return toLogResponse(opLog);
    }

    // NEW: OperatorLog.complete() existed on the entity but had no service
    // method or endpoint wired to it — operators could start a shift but the
    // API gave you no way to end one.
    @Transactional
    public OperatorLogResponse completeOperatorLog(TenantId tenantId, UUID assetId, UUID logId,
                                                   CompleteOperatorLogRequest req) {
        findActive(tenantId, assetId);
        OperatorLog opLog = operatorLogRepository.findByIdAndAssetId(logId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("OperatorLog", logId.toString()));

        if (opLog.getEndedAt() != null) {
            throw new IllegalStateException("Operator log " + logId + " is already completed");
        }
        opLog.complete(req.endedAt(), req.endHours(), req.fuelUsedLitres(), req.notes());
        log.info("Operator log completed asset={} log={} hoursLogged={}", assetId, logId, opLog.getHoursLogged());
        return toLogResponse(opLog);
    }

    // ── Notifications ─────────────────────────────────────────────────────
    // See FleetNotificationRecipients for why this doesn't reach into an
    // Identity/User module directly, and NotificationService's Javadoc for
    // why these calls are safe to make from inside an @Transactional method
    // (they only actually deliver email/SMS after this transaction commits).

    private void notifyBreakdown(TenantId tenantId, EarthAsset asset) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.ASSET_BREAKDOWN)
                .title("Breakdown reported: " + displayName(asset))
                .message(displayName(asset) + " was reported as broken down"
                        + (asset.getCurrentSite() != null ? " at " + asset.getCurrentSite() : "") + ".")
                .actionUrl("/earthmoving/assets/" + asset.getId())
                .sourceModule("earthmoving")
                .sourceEntityId(asset.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyServiceDue(TenantId tenantId, EarthAsset asset) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.ASSET_SERVICE_DUE)
                .title("Service due: " + displayName(asset))
                .message(displayName(asset) + " has reached " + asset.getCurrentHours()
                        + " hours and is due for service (interval: " + asset.getServiceIntervalHours() + " hrs).")
                .actionUrl("/earthmoving/assets/" + asset.getId())
                .sourceModule("earthmoving")
                .sourceEntityId(asset.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyMaintenanceRecorded(TenantId tenantId, EarthAsset asset, MaintenanceRecord record) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.ASSET_MAINTENANCE_RECORDED)
                .title("Maintenance recorded: " + displayName(asset))
                .message(record.getType() + " recorded for " + displayName(asset) + ": " + record.getDescription())
                .actionUrl("/earthmoving/assets/" + asset.getId())
                .sourceModule("earthmoving")
                .sourceEntityId(asset.getId().toString())
                .recipients(recipients)
                .build());
    }

    private String displayName(EarthAsset asset) {
        return asset.getFleetNumber() != null ? asset.getFleetNumber() + " (" + asset.getName() + ")" : asset.getName();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private EarthAsset findActive(TenantId tenantId, UUID id) {
        return assetRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id.toString()));
    }

    private AssetStatus parseStatus(String raw) {
        try {
            return AssetStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + raw +
                    ". Valid values: AVAILABLE, DEPLOYED, MAINTENANCE, BREAKDOWN, HIRED_OUT, RETIRED");
        }
    }

    private AssetResponse toResponse(EarthAsset a) {
        return new AssetResponse(
                a.getId(), a.getName(), a.getFleetNumber(), a.getAssetType(),
                a.getMake(), a.getModel(), a.getYear(),
                a.getSerialNumber(), a.getRegistration(),
                a.getOwnershipType() != null ? a.getOwnershipType() : "OWN",
                a.getHireSupplier(), a.getHireStartDate(), a.getHireEndDate(),
                a.getStatus().name(),
                a.getCurrentSite(), a.getCurrentClient(),
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

    private DeploymentResponse toDeploymentResponse(EarthDeployment d) {
        return new DeploymentResponse(
                d.getId(), d.getAssetId(), d.getSiteName(), d.getClientName(),
                d.getContactName(), d.getContactPhone(),
                d.getPlannedStartDate(), d.getPlannedEndDate(),
                d.getDeployedAt(), d.getReturnedAt(), d.getEndReason(), d.getNotes()
        );
    }
}