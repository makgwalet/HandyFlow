package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.fleet.domain.model.AccidentStatus;
import za.co.handyflow.platform.fleet.domain.model.FleetIssue;
import za.co.handyflow.platform.fleet.domain.model.IssueSeverity;
import za.co.handyflow.platform.fleet.domain.model.IssueStatus;
import za.co.handyflow.platform.fleet.domain.model.Technician;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.model.VehicleLifecycleStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleService;
import za.co.handyflow.platform.fleet.domain.model.VehicleStatus;
import za.co.handyflow.platform.fleet.domain.model.WorkOrder;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderAttachment;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderAttachmentType;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderLine;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderLineType;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderPriority;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderStatus;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderType;
import za.co.handyflow.platform.fleet.domain.repository.AccidentRepository;
import za.co.handyflow.platform.fleet.domain.repository.AssetProfileRepository;
import za.co.handyflow.platform.fleet.domain.repository.FleetIssueRepository;
import za.co.handyflow.platform.fleet.domain.repository.TechnicianRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleServiceRepository;
import za.co.handyflow.platform.fleet.domain.repository.WorkOrderAttachmentRepository;
import za.co.handyflow.platform.fleet.domain.repository.WorkOrderRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <p>
 * The workshop: raise a work order (optionally from a fleet issue or accident), quote, approve,
 * do the work, log actual costs, complete. Beyond the work order's own rules, this service keeps
 * the rest of Fleet consistent:
 * <ul>
 *   <li><b>Vehicle status</b> — starting work moves an AVAILABLE or BREAKDOWN vehicle to MAINTENANCE;
 *       when its last working order completes or is cancelled it returns to AVAILABLE. A vehicle
 *       on a trip cannot be taken into the workshop, and only ACQUIRED / ACTIVE vehicles can be
 *       worked on.</li>
 *   <li><b>Fleet issue</b> — a linked issue goes IN_PROGRESS when work starts, RESOLVED when it
 *       completes, and back to OPEN if the order is cancelled and nothing else is pending on it.</li>
 *   <li><b>Service history &amp; cost-per-km</b> — completing an order writes a VehicleService row
 *       carrying the actual total, so workshop spend flows into FleetCostService unchanged. A
 *       SERVICE order also resets the vehicle's service interval (and, for a fleet asset, its
 *       hour-based interval).</li>
 *   <li><b>Accident</b> — completing an ACCIDENT_REPAIR order fills the accident's blank repairer
 *       and repair cost and stamps its return-to-service date (skipped if the case is CLOSED).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final long MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_PER_ORDER = 50;
    private static final Set<String> ATTACHMENT_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderAttachmentRepository attachmentRepository;
    private final TechnicianRepository technicianRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleServiceRepository serviceRepository;
    private final FleetIssueRepository issueRepository;
    private final AccidentRepository accidentRepository;
    private final AssetProfileRepository assetProfileRepository;
    private final WorkOrderReferenceGenerator referenceGenerator;
    private final FleetUploadSupport uploads;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public record AttachmentDownload(String fileName, String contentType, long sizeBytes, InputStream stream) {}

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> list(TenantId tenantId, UUID vehicleId, WorkOrderStatus status, Pageable pageable) {
        Page<WorkOrder> page;
        if (vehicleId != null && status != null) {
            page = workOrderRepository.findByVehicleAndStatus(tenantId, vehicleId, status, pageable);
        } else if (vehicleId != null) {
            page = workOrderRepository.findByVehicle(tenantId, vehicleId, pageable);
        } else if (status != null) {
            page = workOrderRepository.findByStatus(tenantId, status, pageable);
        } else {
            page = workOrderRepository.findAllForTenant(tenantId, pageable);
        }
        Map<UUID, String> regCache = new HashMap<>();
        Map<UUID, String> techCache = new HashMap<>();
        return page.map(w -> toSummary(w,
                regCache.computeIfAbsent(w.getVehicleId(), id -> registrationOf(tenantId, id)),
                technicianName(tenantId, w.getAssignedTechnicianId(), techCache)));
    }

    @Transactional(readOnly = true)
    public WorkOrderDetailResponse get(TenantId tenantId, UUID id) {
        return toDetail(find(tenantId, id), tenantId);
    }

    // ── Create / edit ────────────────────────────────────────────────────

    @Transactional
    public WorkOrderDetailResponse create(TenantId tenantId, UUID userId, CreateWorkOrderRequest req) {
        Vehicle vehicle = findVehicle(tenantId, req.vehicleId());
        if (vehicle.getLifecycleStatus().isOutOfFleet()) {
            throw new IllegalArgumentException("Vehicle " + vehicle.getRegistration() + " is "
                    + vehicle.getLifecycleStatus() + " and is no longer in the fleet");
        }

        FleetIssue issue = null;
        if (req.issueId() != null) {
            issue = issueRepository.findActiveById(tenantId, req.issueId())
                    .orElseThrow(() -> new ResourceNotFoundException("FleetIssue", req.issueId().toString()));
            if (!issue.getVehicleId().equals(vehicle.getId())) {
                throw new IllegalArgumentException("That issue belongs to a different vehicle");
            }
            List<WorkOrder> open = workOrderRepository.findByIssueAndStatuses(tenantId, issue.getId(), WorkOrderStatus.ACTIVE);
            if (!open.isEmpty()) {
                throw new IllegalArgumentException("This issue already has an open work order: "
                        + open.get(0).getReferenceNumber());
            }
        }
        if (req.accidentId() != null) {
            var accident = accidentRepository.findByTenantAndId(tenantId, req.accidentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Accident", req.accidentId().toString()));
            if (!accident.getVehicleId().equals(vehicle.getId())) {
                throw new IllegalArgumentException("That accident belongs to a different vehicle");
            }
        }
        requireActiveTechnician(tenantId, req.technicianId());

        String title = blankToNull(req.title());
        if (title == null) {
            if (issue == null) throw new IllegalArgumentException("A title is required");
            title = issue.getTitle();
        }
        WorkOrderPriority priority = req.priority() != null ? req.priority()
                : (issue != null ? priorityFor(issue.getSeverity()) : WorkOrderPriority.NORMAL);

        WorkOrder wo = WorkOrder.create(tenantId, referenceGenerator.next(tenantId), vehicle.getId(),
                req.issueId(), req.accidentId(), req.type(), title.trim(), blankToNull(req.description()),
                priority, req.technicianId(), blankToNull(req.supplier()), req.scheduledFor(),
                req.odometerIn(), req.vehicleOffRoadFrom(), userId);
        workOrderRepository.save(wo);
        log.info("Work order created ref={} vehicle={} tenant={}", wo.getReferenceNumber(), vehicle.getId(), tenantId);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse update(TenantId tenantId, UUID id, UpdateWorkOrderRequest req) {
        WorkOrder wo = find(tenantId, id);
        requireActiveTechnician(tenantId, req.technicianId());
        wo.updateDetails(req.type(), req.title().trim(), blankToNull(req.description()), req.priority(),
                req.technicianId(), blankToNull(req.supplier()), req.scheduledFor(), req.odometerIn(),
                req.vehicleOffRoadFrom());
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    // ── Quotation & approval ─────────────────────────────────────────────

    @Transactional
    public WorkOrderDetailResponse recordQuotation(TenantId tenantId, UUID id, RecordQuotationRequest req) {
        WorkOrder wo = find(tenantId, id);
        wo.recordQuotation(req.amount(), blankToNull(req.supplier()), blankToNull(req.quotationRef()),
                req.quotationDate(), req.validUntil(), blankToNull(req.notes()));
        workOrderRepository.save(wo);
        notifyApprovalRequested(tenantId, wo);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse approve(TenantId tenantId, UUID id, UUID approver, String note) {
        WorkOrder wo = find(tenantId, id);
        wo.approve(approver, blankToNull(note));
        workOrderRepository.save(wo);
        log.info("Work order approved ref={} by={}", wo.getReferenceNumber(), approver);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse reject(TenantId tenantId, UUID id, String reason) {
        WorkOrder wo = find(tenantId, id);
        wo.reject(blankToNull(reason));
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    // ── Work ─────────────────────────────────────────────────────────────

    @Transactional
    public WorkOrderDetailResponse start(TenantId tenantId, UUID id, UUID userId) {
        WorkOrder wo = find(tenantId, id);
        Vehicle vehicle = findVehicle(tenantId, wo.getVehicleId());

        VehicleLifecycleStatus lifecycle = vehicle.getLifecycleStatus();
        if (lifecycle != VehicleLifecycleStatus.ACTIVE && lifecycle != VehicleLifecycleStatus.ACQUIRED) {
            throw new IllegalStateException("Vehicle " + vehicle.getRegistration() + " is " + lifecycle
                    + " — work can only start on an ACQUIRED or ACTIVE vehicle");
        }
        if (vehicle.getStatus() == VehicleStatus.ON_TRIP) {
            throw new IllegalStateException("Vehicle " + vehicle.getRegistration()
                    + " is on a trip — end the trip before taking it into the workshop");
        }

        wo.start();   // validates the quotation / approval rule and the transition

        if (vehicle.getStatus() == VehicleStatus.AVAILABLE || vehicle.getStatus() == VehicleStatus.BREAKDOWN) {
            vehicle.sendToMaintenance();
            vehicleRepository.save(vehicle);
        }
        workOrderRepository.save(wo);
        markIssue(tenantId, wo.getIssueId(), IssueStatus.IN_PROGRESS, null, userId);
        log.info("Work order started ref={} vehicle={}", wo.getReferenceNumber(), vehicle.getId());
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse hold(TenantId tenantId, UUID id, String reason) {
        WorkOrder wo = find(tenantId, id);
        wo.hold(blankToNull(reason));
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse resume(TenantId tenantId, UUID id) {
        WorkOrder wo = find(tenantId, id);
        wo.resume();
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse complete(TenantId tenantId, UUID id, UUID userId, CompleteWorkOrderRequest req) {
        WorkOrder wo = find(tenantId, id);
        Vehicle vehicle = findVehicle(tenantId, wo.getVehicleId());

        Instant completedAt = req.completedAt() != null ? req.completedAt() : Instant.now();
        if (completedAt.isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("The completion time cannot be in the future");
        }

        wo.complete(req.odometer(), blankToNull(req.invoiceRef()), blankToNull(req.completionNotes()),
                req.nextServiceKm(), completedAt);

        if (req.odometer() != null) {
            vehicle.updateOdometer(req.odometer());   // floor-guarded: cannot go backwards
        }

        // Service history row: this is what carries workshop spend into FleetCostService.
        BigDecimal total = wo.getActualTotal();
        LocalDate completedOn = completedAt.atZone(SAST).toLocalDate();
        String supplierLabel = supplierLabel(tenantId, wo);
        VehicleService svc = VehicleService.create(tenantId, vehicle.getId(), wo.getType().name(), wo.getTitle(),
                completedOn, req.odometer(), req.nextServiceKm(), total.signum() > 0 ? total : null,
                supplierLabel, blankToNull(req.invoiceRef()));
        serviceRepository.save(svc);
        wo.linkServiceRecord(svc.getId());

        if (wo.getType() == WorkOrderType.SERVICE) {
            vehicle.recordService(req.odometer());   // resets the km / days service interval
            // Fleet assets (chunk 8): also restart the hour-based interval from the machine's current engine hours.
            assetProfileRepository.findByTenantIdAndVehicleId(tenantId, vehicle.getId())
                    .ifPresent(profile -> {
                        profile.recordServiceAt(vehicle.getEngineHours());
                        assetProfileRepository.save(profile);
                    });
        }
        releaseVehicleIfIdle(vehicle, wo);
        vehicleRepository.save(vehicle);
        workOrderRepository.save(wo);

        markIssue(tenantId, wo.getIssueId(), IssueStatus.RESOLVED,
                "Resolved by work order " + wo.getReferenceNumber(), userId);
        syncAccidentRepair(tenantId, wo, supplierLabel, total, completedOn);

        log.info("Work order completed ref={} total={} tenant={}", wo.getReferenceNumber(), total, tenantId);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse cancel(TenantId tenantId, UUID id, UUID userId, String reason) {
        WorkOrder wo = find(tenantId, id);
        boolean wasWorking = WorkOrderStatus.WORKING.contains(wo.getStatus());
        wo.cancel(blankToNull(reason));
        workOrderRepository.save(wo);

        if (wasWorking) {
            Vehicle vehicle = findVehicle(tenantId, wo.getVehicleId());
            releaseVehicleIfIdle(vehicle, wo);
            vehicleRepository.save(vehicle);
        }
        // Put the issue back to OPEN if this was the only thing being done about it.
        if (wo.getIssueId() != null && workOrderRepository.countByIssueAndStatusesExcluding(
                tenantId, wo.getIssueId(), wo.getId(), WorkOrderStatus.ACTIVE) == 0) {
            markIssue(tenantId, wo.getIssueId(), IssueStatus.OPEN, null, userId);
        }
        return toDetail(wo, tenantId);
    }

    // ── Cost lines ───────────────────────────────────────────────────────

    @Transactional
    public WorkOrderDetailResponse addLine(TenantId tenantId, UUID id, SaveWorkOrderLineRequest req) {
        WorkOrder wo = find(tenantId, id);
        wo.addLine(req.lineType(), req.description().trim(), blankToNull(req.partNumber()), req.quantity(),
                resolveUnitCost(tenantId, req), req.technicianId());
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse updateLine(TenantId tenantId, UUID id, UUID lineId, SaveWorkOrderLineRequest req) {
        WorkOrder wo = find(tenantId, id);
        wo.updateLine(lineId, req.lineType(), req.description().trim(), blankToNull(req.partNumber()),
                req.quantity(), resolveUnitCost(tenantId, req), req.technicianId());
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    @Transactional
    public WorkOrderDetailResponse removeLine(TenantId tenantId, UUID id, UUID lineId) {
        WorkOrder wo = find(tenantId, id);
        wo.removeLine(lineId);
        workOrderRepository.save(wo);
        return toDetail(wo, tenantId);
    }

    /** LABOUR lines may omit the unit cost to use the technician's hourly rate. */
    private BigDecimal resolveUnitCost(TenantId tenantId, SaveWorkOrderLineRequest req) {
        Technician tech = null;
        if (req.technicianId() != null) {
            tech = technicianRepository.findByTenantAndId(tenantId, req.technicianId())
                    .orElseThrow(() -> new ResourceNotFoundException("Technician", req.technicianId().toString()));
        }
        BigDecimal unit = req.unitCost();
        if (unit == null && req.lineType() == WorkOrderLineType.LABOUR && tech != null && tech.getHourlyRate() != null) {
            unit = tech.getHourlyRate();
        }
        if (unit == null) {
            throw new IllegalArgumentException("A unit cost is required"
                    + (req.lineType() == WorkOrderLineType.LABOUR ? " (or pick a technician who has an hourly rate)" : ""));
        }
        return unit;
    }

    // ── Attachments ──────────────────────────────────────────────────────

    /** Attachments (quotes, invoices, photos) are accepted in any status, including after completion. */
    @Transactional
    public WorkOrderAttachmentResponse uploadAttachment(TenantId tenantId, UUID id, UUID userId, MultipartFile file,
                                                        WorkOrderAttachmentType type, String description) {
        WorkOrder wo = find(tenantId, id);
        if (attachmentRepository.countActive(tenantId, id) >= MAX_ATTACHMENTS_PER_ORDER) {
            throw new IllegalArgumentException("A work order can have at most " + MAX_ATTACHMENTS_PER_ORDER + " attachments");
        }
        String contentType = uploads.validate(file, ATTACHMENT_TYPES, MAX_ATTACHMENT_BYTES);
        String key = "fleet/" + tenantId.getValue() + "/" + wo.getVehicleId() + "/workshop/" + id + "/" + UUID.randomUUID();
        uploads.store(key, file, contentType);

        WorkOrderAttachment a = WorkOrderAttachment.create(tenantId, id, type,
                truncate(blankToNull(description), 500), uploads.sanitiseFileName(file.getOriginalFilename()),
                contentType, file.getSize(), key, userId);
        attachmentRepository.save(a);
        return toAttachmentResponse(a);
    }

    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(TenantId tenantId, UUID id, UUID attachmentId) {
        find(tenantId, id);
        WorkOrderAttachment a = attachmentRepository.findActiveById(tenantId, id, attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrderAttachment", attachmentId.toString()));
        return new AttachmentDownload(a.getFileName(), a.getContentType(), a.getSizeBytes(),
                uploads.open(a.getStorageKey()));
    }

    /** Soft delete — hidden from the order, stored file retained. */
    @Transactional
    public void deleteAttachment(TenantId tenantId, UUID id, UUID attachmentId, UUID deletedBy) {
        find(tenantId, id);
        WorkOrderAttachment a = attachmentRepository.findActiveById(tenantId, id, attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrderAttachment", attachmentId.toString()));
        a.softDelete(deletedBy);
        attachmentRepository.save(a);
    }

    // ── Cross-aggregate sync ─────────────────────────────────────────────

    /** Returns the vehicle to AVAILABLE if it is in MAINTENANCE and no other order is still working on it. */
    private void releaseVehicleIfIdle(Vehicle vehicle, WorkOrder finished) {
        long others = workOrderRepository.countByVehicleAndStatusesExcluding(
                finished.getTenantId(), vehicle.getId(), finished.getId(), WorkOrderStatus.WORKING);
        if (others == 0 && vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            vehicle.changeStatusTo(VehicleStatus.AVAILABLE);
        }
    }

    /** Moves the linked issue to {@code target} only if that is a legal move from where it is now. */
    private void markIssue(TenantId tenantId, UUID issueId, IssueStatus target, String note, UUID userId) {
        if (issueId == null) return;
        issueRepository.findActiveById(tenantId, issueId).ifPresent(issue -> {
            if (issue.getStatus() != target && issue.getStatus().canTransitionTo(target)) {
                issue.changeStatus(target, note, userId);
                issueRepository.save(issue);
            }
        });
    }

    /**
     * Fills the accident's blank repairer and repair cost from this order and stamps the
     * return-to-service date. Existing values are never overwritten (a manual entry wins, and a
     * second repair order does not double-count). Skipped when the case is CLOSED.
     */
    private void syncAccidentRepair(TenantId tenantId, WorkOrder wo, String supplierLabel,
                                    BigDecimal total, LocalDate completedOn) {
        if (wo.getAccidentId() == null || wo.getType() != WorkOrderType.ACCIDENT_REPAIR) return;
        accidentRepository.findByTenantAndId(tenantId, wo.getAccidentId()).ifPresent(a -> {
            LocalDate accidentDate = a.getOccurredAt().atZone(SAST).toLocalDate();
            if (a.getStatus() == AccidentStatus.CLOSED || completedOn.isBefore(accidentDate)) return;
            String repairer = a.getRepairer() != null ? a.getRepairer() : supplierLabel;
            BigDecimal cost = a.getRepairCost() != null ? a.getRepairCost() : (total.signum() > 0 ? total : null);
            a.updateRepair(repairer, cost, completedOn);
            accidentRepository.save(a);
        });
    }

    private void notifyApprovalRequested(TenantId tenantId, WorkOrder wo) {
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        String reg = registrationOf(tenantId, wo.getVehicleId());
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.WORK_ORDER_APPROVAL_REQUESTED)
                .title("Approval needed: " + wo.getReferenceNumber() + (reg != null ? " (" + reg + ")" : ""))
                .message("Quotation of R" + wo.getQuotedAmount() + " for \"" + wo.getTitle() + "\""
                        + (wo.getSupplier() != null ? " from " + wo.getSupplier() : "") + " is awaiting approval.")
                .actionUrl("/fleet/work-orders/" + wo.getId())
                .sourceModule("fleet")
                .sourceEntityId(wo.getId().toString())
                .recipients(recipients)
                .build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private WorkOrder find(TenantId tenantId, UUID id) {
        return workOrderRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkOrder", id.toString()));
    }

    private Vehicle findVehicle(TenantId tenantId, UUID id) {
        return vehicleRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", id.toString()));
    }

    private String registrationOf(TenantId tenantId, UUID vehicleId) {
        return vehicleRepository.findActiveById(tenantId, vehicleId).map(Vehicle::getRegistration).orElse(null);
    }

    private void requireActiveTechnician(TenantId tenantId, UUID technicianId) {
        if (technicianId == null) return;
        Technician t = technicianRepository.findByTenantAndId(tenantId, technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician", technicianId.toString()));
        if (!t.isActive()) {
            throw new IllegalArgumentException("Technician " + t.getName() + " is inactive");
        }
    }

    private String technicianName(TenantId tenantId, UUID technicianId, Map<UUID, String> cache) {
        if (technicianId == null) return null;
        return cache.computeIfAbsent(technicianId, id -> technicianRepository.findByTenantAndId(tenantId, id)
                .map(Technician::getName).orElse(null));
    }

    private String supplierLabel(TenantId tenantId, WorkOrder wo) {
        if (wo.getSupplier() != null) return wo.getSupplier();
        String tech = technicianName(tenantId, wo.getAssignedTechnicianId(), new HashMap<>());
        return tech != null ? "In-house — " + tech : "In-house workshop";
    }

    private WorkOrderPriority priorityFor(IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> WorkOrderPriority.URGENT;
            case HIGH -> WorkOrderPriority.HIGH;
            case MEDIUM -> WorkOrderPriority.NORMAL;
            case LOW -> WorkOrderPriority.LOW;
        };
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private WorkOrderResponse toSummary(WorkOrder w, String registration, String technicianName) {
        return new WorkOrderResponse(w.getId(), w.getReferenceNumber(), w.getVehicleId(), registration,
                w.getType().name(), w.getTitle(), w.getPriority().name(), w.getStatus().name(),
                w.getAssignedTechnicianId(), technicianName, w.getSupplier(), w.getScheduledFor(),
                w.getQuotedAmount(), w.getActualTotal(), w.getStartedAt(), w.getCompletedAt(), w.getCreatedAt());
    }

    private WorkOrderAttachmentResponse toAttachmentResponse(WorkOrderAttachment a) {
        return new WorkOrderAttachmentResponse(a.getId(), a.getType().name(), a.getDescription(), a.getFileName(),
                a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), a.getUploadedAt());
    }

    private WorkOrderDetailResponse toDetail(WorkOrder w, TenantId tenantId) {
        Map<UUID, String> techCache = new HashMap<>();
        WorkOrderResponse summary = toSummary(w, registrationOf(tenantId, w.getVehicleId()),
                technicianName(tenantId, w.getAssignedTechnicianId(), techCache));

        List<WorkOrderLineResponse> lines = w.getLines().stream()
                .map(l -> new WorkOrderLineResponse(l.getId(), l.getLineType().name(), l.getDescription(),
                        l.getPartNumber(), l.getQuantity(), l.getUnitCost(), l.getLineTotal(),
                        l.getTechnicianId(), technicianName(tenantId, l.getTechnicianId(), techCache)))
                .toList();

        BigDecimal actual = w.getActualTotal();
        BigDecimal variance = w.getQuotedAmount() != null ? actual.subtract(w.getQuotedAmount()) : null;

        Instant offRoad = w.getVehicleOffRoadFrom() != null ? w.getVehicleOffRoadFrom() : w.getStartedAt();
        boolean ongoing = offRoad != null && WorkOrderStatus.WORKING.contains(w.getStatus());
        Double downtime = null;
        if (offRoad != null) {
            Instant end = w.getCompletedAt() != null ? w.getCompletedAt()
                    : (w.getStatus() == WorkOrderStatus.CANCELLED ? w.getUpdatedAt() : Instant.now());
            downtime = Math.max(0, Math.round(Duration.between(offRoad, end).toMinutes() / 6.0) / 10.0);
        }

        List<WorkOrderAttachmentResponse> attachments = attachmentRepository
                .findActiveByWorkOrder(tenantId, w.getId()).stream().map(this::toAttachmentResponse).toList();

        return new WorkOrderDetailResponse(summary, w.getDescription(), w.getIssueId(), w.getAccidentId(),
                w.getOdometerIn(), w.getVehicleOffRoadFrom(),
                w.getQuotationRef(), w.getQuotationDate(), w.getQuotationValidUntil(), w.getQuotationNotes(),
                w.getApprovedBy(), w.getApprovedAt(), w.getApprovalNote(), w.getRejectionReason(),
                w.getHoldReason(), w.getCancelReason(),
                lines, w.getTotalFor(WorkOrderLineType.LABOUR), w.getTotalFor(WorkOrderLineType.PART),
                w.getTotalFor(WorkOrderLineType.SUBLET), w.getTotalFor(WorkOrderLineType.OTHER),
                variance, variance != null && variance.signum() > 0,
                downtime, ongoing,
                w.getCompletionOdometer(), w.getInvoiceRef(), w.getCompletionNotes(), w.getNextServiceKm(),
                w.getServiceRecordId(), attachments);
    }
}