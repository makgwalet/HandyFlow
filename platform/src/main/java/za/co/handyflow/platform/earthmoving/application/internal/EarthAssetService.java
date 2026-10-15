package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;
import za.co.handyflow.platform.earthmoving.domain.model.EarthStatusEvent;
import za.co.handyflow.platform.earthmoving.domain.model.ReleaseRules;
import za.co.handyflow.platform.earthmoving.domain.model.AlertRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthWorkOrderRepository;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.MeterRules;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarthAssetService {

    private final EarthAssetRepository assetRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final OperatorLogRepository operatorLogRepository;
    private final EarthDeploymentRepository deploymentRepository;
    private final EarthStatusEventRepository statusEvents;
    private final EarthmovingIncidentRepository incidentRepository;
    private final EarthWorkOrderRepository workOrders;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;
    private final NotificationService notificationService;
    // FIX: was a bespoke earthmoving-only FleetNotificationRecipients port,
    // permanently backed by a no-op stub. TenantAdminRecipients already
    // exists as the shared, cross-module version of exactly this problem
    // (see TenantAdminRecipientsImpl in the identity module) — earthmoving
    // now depends on that instead of maintaining its own parallel,
    // never-implemented port. This is what actually turns notifications on.
    private final TenantAdminRecipients tenantAdminRecipients;
    private final EarthAlertService alerts;
    private final EarthSiteService sites;
    private final EarthMeterService meters;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    private final EarthDeployCheckService deployChecks;

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
        access.require(EarthmovingRules.WRITE, "register equipment");
        String ownership = EarthmovingRules.checkAssetDetails(req.name(), req.assetType(), req.ownershipType(), req.year(),
                req.hourlyRate(), req.dailyRate(), req.hireStartDate(), req.hireEndDate(), null, Year.now().getValue());
        // The unique index (V424) is the real guarantee against two concurrent creates; this gives a clear message first.
        if (req.fleetNumber() != null && !req.fleetNumber().isBlank()
                && assetRepository.existsActiveByFleetNumber(tenantId, req.fleetNumber().trim())) {
            throw new IllegalArgumentException(
                    "Fleet number \"" + req.fleetNumber() + "\" is already in use by another asset.");
        }

        EarthAsset asset = EarthAsset.create(
                tenantId, req.name().trim(), blankToNull(req.fleetNumber()), req.assetType().trim().toUpperCase(),
                req.make(), req.model(), req.year(),
                req.serialNumber(), req.registration(),
                ownership,
                req.hireSupplier(), req.hireStartDate(), req.hireEndDate(),
                req.dailyRate(), req.hourlyRate(), req.notes()
        );
        asset.useMeter(req.meterType());
        saveAssetOrExplain(asset);
        trail.record(tenantId, "EARTHMOVING_ASSET_CREATED", "EARTHMOVING_ASSET", asset.getId(), null, snapshot(asset));
        log.info("Registered earthmoving asset={} fleet={} tenant={}",
                asset.getName(), asset.getFleetNumber(), tenantId);
        return toResponse(asset);
    }

    /** Edit descriptive details. Before patch E1 there was no way to correct a wrong serial number or rate. */
    @Transactional
    public AssetResponse updateAsset(TenantId tenantId, UUID id, UpdateAssetRequest req) {
        access.require(EarthmovingRules.WRITE, "edit equipment");
        EarthAsset asset = findActive(tenantId, id);
        String ownership = EarthmovingRules.checkAssetDetails(req.name(), req.assetType(), req.ownershipType(), req.year(),
                req.hourlyRate(), req.dailyRate(), req.hireStartDate(), req.hireEndDate(),
                req.serviceIntervalHours(), Year.now().getValue());
        String fleet = blankToNull(req.fleetNumber());
        if (fleet != null && assetRepository.existsActiveByFleetNumberExcluding(tenantId, fleet, id)) {
            throw new IllegalArgumentException("Fleet number \"" + fleet + "\" is already in use by another asset.");
        }
        Map<String, Object> before = snapshot(asset);
        asset.updateDetails(req.name(), fleet, req.assetType(), req.make(), req.model(), req.year(),
                req.serialNumber(), req.registration(), ownership, req.hireSupplier(), req.hireStartDate(),
                req.hireEndDate(), req.dailyRate(), req.hourlyRate(), req.serviceIntervalHours(), req.notes());
        saveAssetOrExplain(asset);
        trail.record(tenantId, "EARTHMOVING_ASSET_UPDATED", "EARTHMOVING_ASSET", id, before, snapshot(asset));
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse updateStatus(TenantId tenantId, UUID id, UpdateAssetStatusRequest req, UUID actor) {
        EarthAsset asset = findActive(tenantId, id);
        AssetStatus previousStatus = asset.getStatus();
        AssetStatus target = parseStatus(req.status());

        String needed = EarthmovingRules.authorityForStatusChange(previousStatus, target);
        if (needed == null) {
            throw new IllegalArgumentException(
                    "A machine is deployed with the Deploy action, which records the site and client. Use that instead.");
        }
        access.require(needed, "make this status change");

        String reason = req.note() == null ? null : req.note().trim();
        if (EarthmovingRules.needsReason(previousStatus, target)) {
            reason = EarthmovingRules.requireReason(reason, "this status change");
        }
        boolean release = EarthmovingRules.isRelease(previousStatus, target);
        if (release && incidentRepository.countOpenCritical(tenantId, id) > 0) {
            throw new IllegalStateException(
                    "This machine has an unresolved critical incident. Resolve it before releasing the machine.");
        }
        UUID releaseWorkOrder = null;
        String overrideReason = null;
        List<String> releaseProblems = List.of();
        if (release) {
            var blocking = workOrders.findOpenBlocking(tenantId, id);
            if (!blocking.isEmpty()) {
                throw new IllegalStateException("Open work orders must be completed or cancelled before this machine is released: "
                        + blocking.stream().map(w -> w.getNumber()).collect(java.util.stream.Collectors.joining(", ")) + ".");
            }
            var last = workOrders.findCompletedForAsset(tenantId, id, org.springframework.data.domain.PageRequest.of(0, 1))
                    .stream().findFirst().orElse(null);
            Instant wentOut = statusEvents.findLatestInto(tenantId, id, previousStatus.name(), org.springframework.data.domain.PageRequest.of(0, 1))
                    .stream().findFirst().map(EarthStatusEvent::getHappenedAt).orElse(null);
            releaseProblems = ReleaseRules.problems(wentOut, last == null ? null : last.getCompletedAt(),
                    last == null ? null : last.getCompletedBy(), actor);
            if (!releaseProblems.isEmpty()) {
                String why = req.overrideReason() == null ? "" : req.overrideReason().trim();
                if (why.isEmpty()) {
                    throw new IllegalStateException(String.join(" ", releaseProblems)
                            + " To release anyway, give a reason for overriding this check.");
                }
                overrideReason = EarthmovingRules.requireReason(why, "overriding the release check");
            }
            if (last != null && ReleaseRules.isEvidence(wentOut, last.getCompletedAt())) releaseWorkOrder = last.getId();
        }

        switch (target) {
            case AVAILABLE -> asset.returnToYard();
            case MAINTENANCE -> asset.sendToMaintenance();
            case BREAKDOWN -> asset.breakdown();
            case HIRED_OUT -> asset.hireOut();
            case RETIRED -> asset.retire();
            case DEPLOYED -> throw new IllegalStateException("unreachable");
        }
        // EarthAsset.changeStatus() throws InvalidAssetStatusTransitionException (409) for an illegal transition.

        assetRepository.save(asset);
        statusEvents.save(EarthStatusEvent.of(tenantId, id, previousStatus, target, reason, actor, asset.getCurrentHours()).withWorkOrder(releaseWorkOrder).withOverride(overrideReason));
        trail.record(tenantId, release ? "EARTHMOVING_ASSET_RELEASED" : "EARTHMOVING_ASSET_STATUS_CHANGED",
                "EARTHMOVING_ASSET", id,
                Map.of("status", previousStatus.name()),
                reason == null ? Map.of("status", target.name()) : Map.of("status", target.name(), "reason", reason));
        if (release && target == AssetStatus.AVAILABLE && previousStatus == AssetStatus.BREAKDOWN) {
            alerts.resolveByKey(tenantId, "BREAKDOWN:" + id, actor, "Machine released from breakdown");
        }
        if (overrideReason != null) {
            trail.record(tenantId, "EARTHMOVING_ASSET_RELEASE_OVERRIDDEN", "EARTHMOVING_ASSET", id,
                    Map.of("problems", String.join(" ", releaseProblems)),
                    Map.of("status", target.name(), "overrideReason", overrideReason));
        }
        log.info("Asset status changed asset={} {}->{} actor={}", id, previousStatus, target, actor);

        // Look the site supervisor up BEFORE the deployment closes, so a breakdown is routed to the site it happened at.
        UUID siteSupervisor = target == AssetStatus.BREAKDOWN ? sites.supervisorForAsset(tenantId, id).orElse(null) : null;

        // The deployment closes whenever the machine leaves DEPLOYED, for any reason (see EarthDeployment).
        if (previousStatus == AssetStatus.DEPLOYED && target != AssetStatus.DEPLOYED) {
            closeOpenDeploymentIfAny(tenantId, id, target.name());
        }

        if (target == AssetStatus.BREAKDOWN) {
            notifyBreakdown(tenantId, asset, siteSupervisor);
        }
        return toResponse(asset);
    }

    @Transactional(readOnly = true)
    public Page<StatusEventResponse> getStatusHistory(TenantId tenantId, UUID id, Pageable pageable) {
        findActive(tenantId, id);
        return statusEvents.findByAsset(tenantId, id, pageable).map(e -> new StatusEventResponse(
                e.getId(), e.getAssetId(), e.getFromStatus(), e.getToStatus(), e.getReason(),
                e.getActorUserId(), e.getMeterHours(), e.getHappenedAt(), e.getWorkOrderId(), e.getOverrideReason()));
    }

    @Transactional
    public AssetResponse updateHours(TenantId tenantId, UUID id, UpdateHoursRequest req, UUID actor) {
        access.require(EarthmovingRules.WRITE, "update an hour meter");
        EarthAsset asset = findActive(tenantId, id);
        boolean wasDue = asset.isDueForService();
        BigDecimal before = asset.getCurrentHours();

        asset.updateHours(req.currentHours());
        assetRepository.save(asset);
        meters.record(tenantId, asset, before, "MANUAL", actor);
        trail.record(tenantId, "EARTHMOVING_HOURS_UPDATED", "EARTHMOVING_ASSET", id,
                Map.of("hours", String.valueOf(before)), Map.of("hours", String.valueOf(req.currentHours())));
        log.info("Hour meter updated asset={} hours={}", id, req.currentHours());
        publisher.publishEvent(new MeterChanged(tenantId, id));

        // Only notify on the transition into "due", not on every later reading while it stays due.
        if (!wasDue && asset.isDueForService()) {
            notifyServiceDue(tenantId, asset);
        }
        return toResponse(asset);
    }

    @Transactional
    public AssetResponse deploy(TenantId tenantId, UUID id, DeployAssetRequest req, UUID actor) {
        access.require(EarthmovingRules.DEPLOY, "deploy equipment");
        EarthAsset asset = findActive(tenantId, id);
        EarthmovingRules.checkDeployDates(req.startDate(), req.expectedEndDate());
        EarthmovingRules.checkDeployable(asset.getStatus(), incidentRepository.countOpenCritical(tenantId, id),
                asset.getOwnershipType(), asset.getHireEndDate(), LocalDate.now());
        AssetStatus previous = asset.getStatus();
        // Warnings never stop a deployment, but deploying despite one is kept on record.
        List<String> warnings = deployChecks.pending(tenantId, asset, java.time.Instant.now());
        EarthSiteService.DeployTarget target = sites.resolveForDeploy(tenantId, req);
        if (req.contractId() != null) deployChecks.requireContract(tenantId, req.contractId());
        // A missing or unsigned hire contract is also only a warning, and is kept on record with the others.
        List<String> allWarnings = new java.util.ArrayList<>(warnings);
        allWarnings.addAll(deployChecks.contractWarnings(tenantId, asset,
                new EarthDeployCheckService.HireContext(target.customerId(), target.siteId(), req.startDate(), req.expectedEndDate(), req.contractId())));
        asset.deployTo(target.siteName(), target.clientName());
        assetRepository.save(asset);

        EarthDeployment deployment = EarthDeployment.create(
                tenantId, id, target.siteName(), target.clientName(),
                req.contactName(), req.contactPhone(),
                req.startDate(), req.expectedEndDate(), req.notes()
        ).withLinks(target.siteId(), target.customerId(), target.projectId());
        deployment.linkContract(req.contractId());
        deploymentRepository.save(deployment);
        statusEvents.save(EarthStatusEvent.of(tenantId, id, previous, AssetStatus.DEPLOYED,
                "Deployed to " + target.siteName(), actor, asset.getCurrentHours()));
        trail.record(tenantId, "EARTHMOVING_ASSET_DEPLOYED", "EARTHMOVING_ASSET", id,
                Map.of("status", previous.name()),
                Map.of("status", "DEPLOYED", "site", target.siteName(),
                        "client", target.clientName() == null ? "" : target.clientName(),
                        "siteId", String.valueOf(target.siteId()),
                        "customerId", String.valueOf(target.customerId()),
                        "projectId", String.valueOf(target.projectId()),
                        "warningsShown", String.join(" | ", allWarnings)));

        log.info("Asset deployed asset={} site={} client={}", id, target.siteName(), target.clientName());
        return toResponse(asset);
    }

    @Transactional
    public void deleteAsset(TenantId tenantId, UUID id, UUID deletedByUserId) {
        access.require(EarthmovingRules.ADMIN, "delete equipment");
        EarthAsset asset = findActive(tenantId, id);
        if (asset.getStatus() == AssetStatus.DEPLOYED) {
            throw new IllegalStateException("A deployed machine cannot be deleted. Return it first.");
        }
        asset.softDelete(deletedByUserId);
        assetRepository.save(asset);
        trail.record(tenantId, "EARTHMOVING_ASSET_DELETED", "EARTHMOVING_ASSET", id, snapshot(asset), null);
    }

    // ── Deployments ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DeploymentResponse> getDeploymentHistory(TenantId tenantId, UUID assetId, Pageable pageable) {
        findActive(tenantId, assetId);
        return deploymentRepository.findByAsset(tenantId, assetId, pageable).map(this::toDeploymentResponse);
    }

    /** No-op if there is no open deployment for this asset. */
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

    /**
     * Records maintenance that was done. It no longer puts the machine back in service by itself: finishing the work and
     * releasing the machine are separate events (patch E1). Release is a status change needing EARTHMOVING_RELEASE.
     */
    @Transactional
    public MaintenanceResponse recordMaintenance(TenantId tenantId, UUID assetId, CreateMaintenanceRequest req) {
        access.require(EarthmovingRules.MAINTAIN, "record maintenance");
        EarthAsset asset = findActive(tenantId, assetId);
        String type = req.type().trim().toUpperCase();
        EarthmovingRules.checkMaintenance(asset.getStatus(), type, req.hoursAtService(), asset.getLastServiceHours(), req.cost());

        boolean isService = "SERVICE".equals(type) && req.hoursAtService() != null;
        BigDecimal next = isService && asset.getServiceIntervalHours() != null
                ? req.hoursAtService().add(asset.getServiceIntervalHours()) : null;
        MaintenanceRecord record = MaintenanceRecord.create(
                tenantId, assetId, type, req.description(),
                req.performedAt(), req.hoursAtService(),
                req.cost(), req.supplier(), req.invoiceRef(), next
        );
        maintenanceRepository.save(record);

        if (isService) {
            asset.recordService(req.hoursAtService());
            // A service reading above the recorded hours means the meter was not yet updated: bring it up.
            if (asset.getCurrentHours() == null || req.hoursAtService().compareTo(asset.getCurrentHours()) > 0) {
                BigDecimal beforeReading = asset.getCurrentHours();
                asset.updateHours(req.hoursAtService());
                meters.record(tenantId, asset, beforeReading, "SERVICE", null);
            }
            assetRepository.save(asset);
            alerts.resolveByKey(tenantId, "SERVICE_DUE:" + assetId, null, "Service recorded");
        }
        trail.record(tenantId, "EARTHMOVING_MAINTENANCE_RECORDED", "EARTHMOVING_ASSET", assetId, null,
                Map.of("type", type, "description", req.description(),
                        "cost", req.cost() == null ? "" : req.cost().toPlainString()));
        log.info("Maintenance recorded asset={} type={}", assetId, type);
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
        access.require(EarthmovingRules.WRITE, "start a shift");
        EarthAsset asset = findActive(tenantId, assetId);
        EarthmovingRules.checkShiftStart(asset.getStatus(), req.startHours(), asset.getCurrentHours());

        operatorLogRepository.findOpenLogForAsset(assetId).ifPresent(open -> {
            throw new IllegalStateException(
                    "Asset already has an open operator shift (started " + open.getStartedAt()
                            + ") — complete it before starting a new one.");
        });

        OperatorLog opLog = OperatorLog.create(
                tenantId, assetId, req.guardId(), req.operatorName(), req.siteName(),
                req.startedAt(), req.startHours()
        );
        try {
            // flush now so the one-open-shift index catches two submissions made at the same moment
            operatorLogRepository.saveAndFlush(opLog);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Asset already has an open operator shift — complete it before starting a new one.");
        }
        return toLogResponse(opLog);
    }

    @Transactional
    public OperatorLogResponse completeOperatorLog(TenantId tenantId, UUID assetId, UUID logId,
                                                   CompleteOperatorLogRequest req) {
        access.require(EarthmovingRules.WRITE, "complete a shift");
        EarthAsset asset = findActive(tenantId, assetId);
        OperatorLog opLog = operatorLogRepository.findByIdAndAssetId(logId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("OperatorLog", logId.toString()));

        if (opLog.getEndedAt() != null) {
            throw new IllegalStateException("Operator log " + logId + " is already completed");
        }
        opLog.complete(req.endedAt(), req.endHours(), req.fuelUsedLitres(), req.notes());

        // The shift's closing reading is the machine's latest reading when it is higher than what we hold.
        if (req.endHours() != null && (asset.getCurrentHours() == null || req.endHours().compareTo(asset.getCurrentHours()) > 0)) {
            boolean wasDue = asset.isDueForService();
            BigDecimal beforeReading = asset.getCurrentHours();
            asset.updateHours(req.endHours());
            assetRepository.save(asset);
            meters.record(tenantId, asset, beforeReading, "SHIFT", null);
            if (!wasDue && asset.isDueForService()) notifyServiceDue(tenantId, asset);
            publisher.publishEvent(new MeterChanged(tenantId, assetId));
        }
        log.info("Operator log completed asset={} log={} hoursLogged={}", assetId, logId, opLog.getHoursLogged());
        return toLogResponse(opLog);
    }

    // ── Notifications ─────────────────────────────────────────────────────
    // See FleetNotificationRecipients for why this doesn't reach into an
    // Identity/User module directly, and NotificationService's Javadoc for
    // why these calls are safe to make from inside an @Transactional method
    // (they only actually deliver email/SMS after this transaction commits).

    private void notifyBreakdown(TenantId tenantId, EarthAsset asset, UUID siteSupervisor) {
        alerts.raise(tenantId, "BREAKDOWN", AlertRules.severityFor("BREAKDOWN", "HIGH"), asset.getId(), "ASSET", asset.getId(),
                "Breakdown reported: " + displayName(asset),
                displayName(asset) + " was reported as broken down" + (asset.getCurrentSite() != null ? " at " + asset.getCurrentSite() : "") + ".",
                "BREAKDOWN:" + asset.getId(), siteSupervisor == null ? null : java.util.List.of(siteSupervisor));
    }

    private void notifyServiceDue(TenantId tenantId, EarthAsset asset) {
        alerts.raise(tenantId, "SERVICE_DUE", "WARNING", asset.getId(), "ASSET", asset.getId(),
                "Service due: " + displayName(asset),
                displayName(asset) + " has reached " + asset.getCurrentHours() + " " + MeterRules.word(asset.getMeterType()) + " and is due for service (interval: "
                        + asset.getServiceIntervalHours() + " " + MeterRules.unit(asset.getMeterType()) + ").",
                "SERVICE_DUE:" + asset.getId(), null);
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

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static Map<String, Object> snapshot(EarthAsset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.getName());
        m.put("fleetNumber", a.getFleetNumber());
        m.put("assetType", a.getAssetType());
        m.put("make", a.getMake());
        m.put("model", a.getModel());
        m.put("year", a.getYear());
        m.put("serialNumber", a.getSerialNumber());
        m.put("registration", a.getRegistration());
        m.put("ownershipType", a.getOwnershipType());
        m.put("meterType", a.getMeterType());
        m.put("hireSupplier", a.getHireSupplier());
        m.put("dailyRate", a.getDailyRate() == null ? null : a.getDailyRate().toPlainString());
        m.put("hourlyRate", a.getHourlyRate() == null ? null : a.getHourlyRate().toPlainString());
        m.put("status", a.getStatus() == null ? null : a.getStatus().name());
        return m;
    }

    /** Flush now so a duplicate fleet number caught by the unique index comes back as a clear message, not a 500. */
    private void saveAssetOrExplain(EarthAsset asset) {
        try {
            assetRepository.saveAndFlush(asset);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Fleet number \"" + asset.getFleetNumber() + "\" is already in use by another asset.");
        }
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
                a.getNotes(), a.getCreatedAt(),
                a.getMeterType(), MeterRules.unit(a.getMeterType())
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
                d.getDeployedAt(), d.getReturnedAt(), d.getEndReason(), d.getNotes(), d.getContractId()
        );
    }
}