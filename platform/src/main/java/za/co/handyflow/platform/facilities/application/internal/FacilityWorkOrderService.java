package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityWorkOrder;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
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
 * cross-entity callback into {@link FacilityPpmScheduleService} when a
 * PPM-sourced work order completes (advances the schedule's own
 * {@code nextDueDate} so the two records can never drift apart).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityWorkOrderService {

    private final FacilityWorkOrderRepository workOrderRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityPpmScheduleRepository ppmScheduleRepository;
    private final FacilityTechnicianRepository technicianRepository;
    private final FacilityVendorRepository vendorRepository;
    private final FacilityNumberGenerator numberGenerator;
    private final FacilityPpmScheduleService ppmScheduleService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> getWorkOrders(TenantId tenantId, String status, Pageable pageable) {
        return workOrderRepository.findAll(tenantId, status != null ? status.toUpperCase() : null, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrder(TenantId tenantId, UUID id) {
        return toResponse(findActive(tenantId, id));
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> getWorkOrdersForAsset(TenantId tenantId, UUID assetId, Pageable pageable) {
        return workOrderRepository.findByAsset(tenantId, assetId, pageable).map(this::toResponse);
    }

    @Transactional
    public WorkOrderResponse createWorkOrder(TenantId tenantId, CreateWorkOrderRequest req) {
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));
        if (req.assetId() != null) {
            assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", req.assetId().toString()));
        }

        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FacilityWorkOrder wo = FacilityWorkOrder.create(tenantId, number, req.siteId(), req.assetId(), null,
                req.category(), req.priority(), req.description(), req.reportedBy(), req.scheduledDate());
        workOrderRepository.save(wo);
        log.info("Work order created number={} tenant={}", number, tenantId);

        if ("EMERGENCY".equals(wo.getPriority()) || "URGENT".equals(wo.getPriority())) {
            notifyUrgentWorkOrder(tenantId, wo);
        }
        return toResponse(wo);
    }

    /** Used by {@link FacilityNotificationScheduler} to raise a work order from a due PPM schedule. */
    @Transactional
    public FacilityWorkOrder createFromPpmSchedule(TenantId tenantId, UUID assetId, UUID siteId,
                                                    UUID ppmScheduleId, String taskName, LocalDate dueDate) {
        String number = numberGenerator.nextWorkOrderNumber(tenantId);
        FacilityWorkOrder wo = FacilityWorkOrder.create(tenantId, number, siteId, assetId, ppmScheduleId,
                "PPM", "NORMAL", "Planned preventive maintenance: " + taskName, "System (PPM schedule)", dueDate);
        workOrderRepository.save(wo);
        log.info("PPM work order generated number={} schedule={} tenant={}", number, ppmScheduleId, tenantId);
        return wo;
    }

    @Transactional
    public WorkOrderResponse assign(TenantId tenantId, UUID id, AssignWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id);

        String technicianName = req.technicianName();
        if (req.technicianId() != null && technicianName == null) {
            technicianName = technicianRepository.findActiveById(tenantId, req.technicianId())
                    .map(t -> t.getName()).orElse(null);
        }
        String vendorName = req.vendorName();
        if (req.vendorId() != null && vendorName == null) {
            vendorName = vendorRepository.findActiveById(tenantId, req.vendorId())
                    .map(v -> v.getCompanyName()).orElse(null);
        }

        wo.assign(req.technicianId(), technicianName, req.vendorId(), vendorName);
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse start(TenantId tenantId, UUID id) {
        FacilityWorkOrder wo = findActive(tenantId, id);
        wo.start();
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse putOnHold(TenantId tenantId, UUID id, HoldWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id);
        wo.putOnHold(req.reason());
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse complete(TenantId tenantId, UUID id, CompleteWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id);
        wo.complete(req.completionNotes(), req.cost(), req.completedAt());
        workOrderRepository.save(wo);
        log.info("Work order completed number={} tenant={}", wo.getWorkOrderNumber(), tenantId);

        // Cross-entity callback: a PPM-sourced work order completing is what
        // actually advances the schedule's nextDueDate — see
        // FacilityPpmScheduleService.applyCompletion()'s own Javadoc.
        if (wo.getPpmScheduleId() != null) {
            LocalDate completedDate = wo.getCompletedAt() != null
                    ? wo.getCompletedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
            ppmScheduleService.applyCompletion(wo.getPpmScheduleId(), completedDate);
        }
        return toResponse(wo);
    }

    @Transactional
    public WorkOrderResponse cancel(TenantId tenantId, UUID id, CancelWorkOrderRequest req) {
        FacilityWorkOrder wo = findActive(tenantId, id);
        wo.cancel(req.reason());
        workOrderRepository.save(wo);
        return toResponse(wo);
    }

    private void notifyUrgentWorkOrder(TenantId tenantId, FacilityWorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.FACILITY_WORKORDER_URGENT)
                .title((wo.getPriority().equals("EMERGENCY") ? "EMERGENCY" : "Urgent") + " work order: " + wo.getWorkOrderNumber())
                .message(wo.getDescription())
                .actionUrl("/facilities/work-orders/" + wo.getId())
                .sourceModule("facilities")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    private FacilityWorkOrder findActive(TenantId tenantId, UUID id) {
        return workOrderRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityWorkOrder", id.toString()));
    }

    private WorkOrderResponse toResponse(FacilityWorkOrder w) {
        return new WorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getSiteId(), w.getAssetId(),
                w.getPpmScheduleId(), w.getCategory(), w.getPriority(), w.getStatus(), w.getDescription(),
                w.getReportedBy(), w.getTechnicianId(), w.getTechnicianName(), w.getVendorId(), w.getVendorName(),
                w.getScheduledDate(), w.getCompletedAt(), w.getCompletionNotes(), w.getCost(),
                w.getCancellationReason(), w.getCreatedAt());
    }
}
