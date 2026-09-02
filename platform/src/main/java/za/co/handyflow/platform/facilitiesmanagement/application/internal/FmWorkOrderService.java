package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmTechnician;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Centerpiece service — owns the work order lifecycle, including the
 * cross-entity callback into {@link FmPpmScheduleService} when a
 * PPM-sourced work order completes (advances the schedule's own
 * {@code nextDueDate}), and the technician-or-vendor assignment shape
 * mirroring {@code FacilityWorkOrderService} exactly, extended with a
 * {@code clientId} everywhere since — unlike Module 5a — every work
 * order here belongs to an external client, not the tenant's own site.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmWorkOrderService {

    private final FmWorkOrderRepository workOrderRepository;
    private final FmClientRepository clientRepository;
    private final FmSiteRepository siteRepository;
    private final FmAssetRepository assetRepository;
    private final FmTechnicianRepository technicianRepository;
    private final FmVendorRepository vendorRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmPpmScheduleService ppmScheduleService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Transactional(readOnly = true)
    public Page<FmWorkOrderResponse> getWorkOrders(TenantId tenantId, UUID clientId, String status, Pageable pageable) {
        String normalizedStatus = status != null ? status.toUpperCase() : null;
        if (clientId != null) {
            return workOrderRepository.findAllForClient(tenantId, clientId, normalizedStatus, pageable).map(this::toResponse);
        }
        return workOrderRepository.findAll(tenantId, normalizedStatus, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmWorkOrderResponse getWorkOrder(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<FmWorkOrderResponse> getWorkOrdersForAsset(TenantId tenantId, UUID assetId, Pageable pageable) {
        return workOrderRepository.findByAsset(tenantId, assetId, pageable).map(this::toResponse);
    }

    @Transactional
    public FmWorkOrderResponse createWorkOrder(TenantId tenantId, CreateFmWorkOrderRequest req) {
        clientRepository.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", req.clientId().toString()));
        siteRepository.findActiveByIdForClient(tenantId, req.clientId(), req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FmSite", req.siteId().toString()));
        if (req.assetId() != null) {
            assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FmAsset", req.assetId().toString()));
        }

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FmWorkOrder wo = FmWorkOrder.create(tenantId, number, req.clientId(), req.siteId(), req.assetId(), null,
                req.category(), req.priority(), req.description(), req.reportedBy(), req.scheduledDate());
        workOrderRepository.save(wo);
        log.info("FM work order created number={} client={} tenant={}", number, req.clientId(), tenantId);

        if ("EMERGENCY".equals(wo.getPriority()) || "URGENT".equals(wo.getPriority())) {
            notifyUrgentWorkOrder(tenantId, wo);
        }
        return toResponse(wo);
    }

    /** Used by {@link FmNotificationScheduler} to raise a work order from a due PPM schedule. */
    @Transactional
    public FmWorkOrder createFromPpmSchedule(TenantId tenantId, UUID clientId, UUID assetId, UUID siteId,
                                              UUID ppmScheduleId, String taskName, LocalDate dueDate) {
        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FmWorkOrder wo = FmWorkOrder.create(tenantId, number, clientId, siteId, assetId, ppmScheduleId,
                "PPM", "NORMAL", "Planned preventive maintenance: " + taskName, "System (PPM schedule)", dueDate);
        workOrderRepository.save(wo);
        log.info("FM PPM work order generated number={} schedule={} tenant={}", number, ppmScheduleId, tenantId);
        return wo;
    }

    @Transactional
    public FmWorkOrderResponse assign(TenantId tenantId, UUID id, AssignFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id);

        String technicianName = req.technicianName();
        if (req.technicianId() != null && technicianName == null) {
            technicianName = technicianRepository.findActiveById(tenantId, req.technicianId())
                    .map(FmTechnician::getName).orElse(null);
        }
        String vendorName = req.vendorName();
        if (req.vendorId() != null && vendorName == null) {
            vendorName = vendorRepository.findActiveById(tenantId, req.vendorId())
                    .map(FmVendor::getCompanyName).orElse(null);
        }

        wo.assign(req.technicianId(), technicianName, req.vendorId(), vendorName);
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse start(TenantId tenantId, UUID id) {
        FmWorkOrder wo = findActive(tenantId, id);
        wo.start();
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse putOnHold(TenantId tenantId, UUID id, HoldFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id);
        wo.putOnHold(req.reason());
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse complete(TenantId tenantId, UUID id, CompleteFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id);
        wo.complete(req.completionNotes(), req.cost(), req.completedAt());
        workOrderRepository.save(wo);
        log.info("FM work order completed number={} tenant={}", wo.getWorkOrderNumber(), tenantId);

        // Cross-entity callback: a PPM-sourced work order completing is what
        // actually advances the schedule's nextDueDate — mirrors
        // FacilityWorkOrderService.complete()'s own identical pattern.
        if (wo.getPpmScheduleId() != null) {
            LocalDate completedDate = wo.getCompletedAt() != null
                    ? wo.getCompletedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
            ppmScheduleService.applyCompletion(wo.getPpmScheduleId(), completedDate);
        }
        return toResponse(wo);
    }

    @Transactional
    public FmWorkOrderResponse cancel(TenantId tenantId, UUID id, CancelFmWorkOrderRequest req) {
        FmWorkOrder wo = findActive(tenantId, id);
        wo.cancel(req.reason());
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    private void notifyUrgentWorkOrder(TenantId tenantId, FmWorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FM_WORKORDER_URGENT)
                .title((wo.getPriority().equals("EMERGENCY") ? "EMERGENCY" : "Urgent") + " work order: " + wo.getWorkOrderNumber())
                .message(wo.getDescription())
                .actionUrl("/facilitiesmanagement/work-orders/" + wo.getId())
                .sourceModule("facilitiesmanagement")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    FmWorkOrder findActive(TenantId tenantId, UUID id) {
        return workOrderRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmWorkOrder", id.toString()));
    }

    private FmWorkOrderResponse toResponse(FmWorkOrder w) {
        return new FmWorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getClientId(), w.getSiteId(), w.getAssetId(),
                w.getPpmScheduleId(), w.getCategory(), w.getPriority(), w.getStatus(), w.getDescription(), w.getReportedBy(),
                w.getTechnicianId(), w.getTechnicianName(), w.getVendorId(), w.getVendorName(), w.getScheduledDate(),
                w.getCompletedAt(), w.getCompletionNotes(), w.getCost(), w.isInvoiced(), w.getCancellationReason(), w.getCreatedAt());
    }
}
