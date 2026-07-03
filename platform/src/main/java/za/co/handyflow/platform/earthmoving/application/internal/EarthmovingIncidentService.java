package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.earthmoving.domain.model.AssetStatus;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingIncident;
import za.co.handyflow.platform.earthmoving.domain.model.InvalidAssetStatusTransitionException;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthmovingIncidentRepository;
import za.co.handyflow.platform.earthmoving.dto.CreateIncidentRequest;
import za.co.handyflow.platform.earthmoving.dto.IncidentResponse;
import za.co.handyflow.platform.earthmoving.dto.ResolveIncidentRequest;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * NAMED "EarthmovingIncidentService" (not plain "IncidentService") — the
 * security module has its own IncidentService for guard/dispatch incidents;
 * Spring's bean scanner uses the simple class name by default regardless of
 * package, so two beans named "incidentService" is a startup-time collision,
 * not a compile error. See EarthmovingIncident and EarthmovingIncidentRepository
 * for the same fix applied at the entity and repository layers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthmovingIncidentService {

    private static final Set<String> AUTO_BREAKDOWN_TYPES = Set.of("BREAKDOWN", "ACCIDENT");

    private final EarthmovingIncidentRepository incidentRepository;
    private final EarthAssetRepository assetRepository;
    private final NotificationService notificationService;
    // FIX: now uses the shared TenantAdminRecipients port (see
    // TenantAdminRecipientsImpl in the identity module) instead of the
    // earthmoving-only FleetNotificationRecipients, which was permanently
    // backed by a no-op stub and never actually notified anyone.
    private final TenantAdminRecipients tenantAdminRecipients;

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidents(TenantId tenantId, String status, String severity, Pageable pageable) {
        Page<EarthmovingIncident> page;
        if (status != null && !status.isBlank()) {
            page = incidentRepository.findByStatus(tenantId, status.toUpperCase(), pageable);
        } else if (severity != null && !severity.isBlank()) {
            page = incidentRepository.findBySeverity(tenantId, severity.toUpperCase(), pageable);
        } else {
            page = incidentRepository.findAll(tenantId, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsForAsset(TenantId tenantId, UUID assetId, Pageable pageable) {
        return incidentRepository.findByAsset(tenantId, assetId, pageable).map(this::toResponse);
    }

    @Transactional
    public IncidentResponse reportIncident(TenantId tenantId, CreateIncidentRequest req, UUID reportedByUserId) {
        EarthAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", req.assetId().toString()));

        EarthmovingIncident incident = EarthmovingIncident.create(
                tenantId, req.assetId(), req.type().toUpperCase(), req.severity().toUpperCase(),
                req.title(), req.description(), req.operatorName(), req.siteName(),
                req.latitude(), req.longitude(), reportedByUserId
        );
        incidentRepository.save(incident);
        log.info("Incident reported id={} asset={} type={} severity={}",
                incident.getId(), req.assetId(), incident.getType(), incident.getSeverity());

        maybeAutoBreakdown(asset, incident);
        notifyIncidentReported(tenantId, asset, incident);

        return toResponse(incident);
    }

    @Transactional
    public IncidentResponse resolveIncident(TenantId tenantId, UUID id, ResolveIncidentRequest req, UUID resolvedByUserId) {
        EarthmovingIncident incident = incidentRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", id.toString()));
        incident.resolve(resolvedByUserId, req.resolutionNotes());
        log.info("Incident resolved id={}", id);
        return toResponse(incident);
    }

    private void maybeAutoBreakdown(EarthAsset asset, EarthmovingIncident incident) {
        if (!AUTO_BREAKDOWN_TYPES.contains(incident.getType())) return;
        if (asset.getStatus() == AssetStatus.BREAKDOWN) return;

        try {
            asset.breakdown();
            assetRepository.save(asset);
            log.info("Asset auto-transitioned to BREAKDOWN by incident={} asset={}", incident.getId(), asset.getId());
        } catch (InvalidAssetStatusTransitionException e) {
            log.warn("Could not auto-transition asset={} to BREAKDOWN for incident={}: {}",
                    asset.getId(), incident.getId(), e.getMessage());
        }
    }

    private void notifyIncidentReported(TenantId tenantId, EarthAsset asset, EarthmovingIncident incident) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        boolean critical = "CRITICAL".equals(incident.getSeverity());
        String assetLabel = asset.getFleetNumber() != null
                ? asset.getFleetNumber() + " (" + asset.getName() + ")" : asset.getName();

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INCIDENT_REPORTED)
                .severity(critical ? NotificationSeverity.CRITICAL : null)
                .channels(critical ? Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS) : null)
                .title("[" + incident.getSeverity() + "] " + incident.getType() + ": " + assetLabel)
                .message(incident.getTitle() + (incident.getSiteName() != null ? " at " + incident.getSiteName() : ""))
                .actionUrl("/earthmoving/incidents/" + incident.getId())
                .sourceModule("earthmoving")
                .sourceEntityId(incident.getId().toString())
                .recipients(recipients)
                .build());
    }

    private IncidentResponse toResponse(EarthmovingIncident i) {
        return new IncidentResponse(
                i.getId(), i.getAssetId(), i.getType(), i.getSeverity(), i.getTitle(), i.getDescription(),
                i.getOperatorName(), i.getSiteName(), i.getLatitude(), i.getLongitude(),
                i.getStatus(), i.getReportedAt(), i.getResolvedAt(), i.getResolutionNotes()
        );
    }
}