package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fleet.domain.model.AssetAttachment;
import za.co.handyflow.platform.fleet.domain.model.AssetClass;
import za.co.handyflow.platform.fleet.domain.model.AssetProfile;
import za.co.handyflow.platform.fleet.domain.model.AttachmentAssignment;
import za.co.handyflow.platform.fleet.domain.model.AttachmentStatus;
import za.co.handyflow.platform.fleet.domain.model.Vehicle;
import za.co.handyflow.platform.fleet.domain.repository.AssetAttachmentRepository;
import za.co.handyflow.platform.fleet.domain.repository.AssetProfileRepository;
import za.co.handyflow.platform.fleet.domain.repository.AttachmentAssignmentRepository;
import za.co.handyflow.platform.fleet.domain.repository.VehicleRepository;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements and attachments hitched to tractors, plant and forklifts. One record per attachment for
 * life; hours in use come from the host machine's engine hours between attaching and detaching, so a
 * machine whose hour meter is not kept up to date gives its attachments the wrong hours. A trailer
 * cannot carry an attachment (trailers are coupled, not hitched — see AssetService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetAttachmentService {

    private final AssetAttachmentRepository attachmentRepository;
    private final AttachmentAssignmentRepository assignmentRepository;
    private final AssetProfileRepository profileRepository;
    private final VehicleRepository vehicleRepository;

    // ── Read ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AttachmentResponse> list(TenantId tenantId, AttachmentStatus status, Pageable pageable) {
        Page<AssetAttachment> page = status != null
                ? attachmentRepository.findByStatus(tenantId, status, pageable)
                : attachmentRepository.findAllForTenant(tenantId, pageable);
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        return page.map(a -> toSummary(a, vehicleOf(tenantId, a.getCurrentVehicleId(), vehicles)));
    }

    @Transactional(readOnly = true)
    public AttachmentDetailResponse get(TenantId tenantId, UUID id) {
        return toDetail(find(tenantId, id), tenantId);
    }

    // ── Register / edit ──────────────────────────────────────────────────

    @Transactional
    public AttachmentDetailResponse create(TenantId tenantId, SaveAttachmentRequest req) {
        String serial = blankToNull(req.serialNumber());
        assertSerialFree(tenantId, serial, UUID.randomUUID());
        AssetAttachment a = AssetAttachment.create(tenantId, req.name().trim(), blankToNull(req.category()), serial,
                blankToNull(req.description()), req.purchaseDate(), req.purchasePrice(), blankToNull(req.notes()));
        attachmentRepository.save(a);
        return toDetail(a, tenantId);
    }

    @Transactional
    public AttachmentDetailResponse update(TenantId tenantId, UUID id, SaveAttachmentRequest req) {
        AssetAttachment a = find(tenantId, id);
        String serial = blankToNull(req.serialNumber());
        assertSerialFree(tenantId, serial, id);
        a.updateDetails(req.name().trim(), blankToNull(req.category()), serial, blankToNull(req.description()),
                req.purchaseDate(), req.purchasePrice(), blankToNull(req.notes()));
        attachmentRepository.save(a);
        return toDetail(a, tenantId);
    }

    // ── Attach / detach / retire ─────────────────────────────────────────

    @Transactional
    public AttachmentDetailResponse attach(TenantId tenantId, UUID id, AttachRequest req) {
        AssetAttachment a = find(tenantId, id);
        Vehicle host = vehicleRepository.findActiveById(tenantId, req.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle", req.vehicleId().toString()));
        if (host.getLifecycleStatus().isOutOfFleet()) {
            throw new IllegalArgumentException("Vehicle " + host.getRegistration() + " is " + host.getLifecycleStatus()
                    + " and is no longer in the fleet");
        }
        AssetProfile hp = profileRepository.findByTenantIdAndVehicleId(tenantId, host.getId()).orElse(null);
        if (hp != null && hp.getAssetClass() == AssetClass.TRAILER) {
            throw new IllegalArgumentException("A trailer cannot carry an attachment");
        }
        BigDecimal hours = resolveHours(host, req.engineHours());
        Instant at = resolveTime(req.attachedAt());

        a.attach(host.getId(), hours, at);     // throws unless the attachment is AVAILABLE
        attachmentRepository.save(a);
        assignmentRepository.save(AttachmentAssignment.open(tenantId, id, host.getId(), at, hours));
        log.info("Attachment {} attached to {} tenant={}", a.getName(), host.getRegistration(), tenantId);
        return toDetail(a, tenantId);
    }

    @Transactional
    public AttachmentDetailResponse detach(TenantId tenantId, UUID id, DetachRequest req) {
        AssetAttachment a = find(tenantId, id);
        if (a.getStatus() != AttachmentStatus.ATTACHED) {
            throw new IllegalStateException("Attachment " + a.getName() + " is " + a.getStatus() + ", not attached");
        }
        Vehicle host = vehicleRepository.findActiveById(tenantId, a.getCurrentVehicleId()).orElse(null);
        BigDecimal hours;
        if (host != null) {
            hours = resolveHours(host, req.engineHours());
        } else if (req.engineHours() != null) {
            hours = req.engineHours();
        } else {
            throw new IllegalArgumentException("Engine hours are required (the machine is no longer on file)");
        }
        Instant at = resolveTime(req.detachedAt());

        AttachmentAssignment open = assignmentRepository.findOpenByAttachment(tenantId, id)
                .orElseThrow(() -> new IllegalStateException("No open assignment found for attachment " + a.getName()));
        open.close(at, hours);
        a.detach(hours);
        assignmentRepository.save(open);
        attachmentRepository.save(a);
        return toDetail(a, tenantId);
    }

    @Transactional
    public AttachmentDetailResponse retire(TenantId tenantId, UUID id) {
        AssetAttachment a = find(tenantId, id);
        a.retire();
        attachmentRepository.save(a);
        return toDetail(a, tenantId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private AssetAttachment find(TenantId tenantId, UUID id) {
        return attachmentRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("AssetAttachment", id.toString()));
    }

    private Vehicle vehicleOf(TenantId tenantId, UUID vehicleId, Map<UUID, Vehicle> cache) {
        if (vehicleId == null) return null;
        return cache.computeIfAbsent(vehicleId, vid -> vehicleRepository.findActiveById(tenantId, vid).orElse(null));
    }

    private void assertSerialFree(TenantId tenantId, String serial, UUID excludeId) {
        if (serial != null && attachmentRepository.existsOtherWithSerial(tenantId, serial, excludeId)) {
            throw new IllegalArgumentException("An attachment with serial number " + serial + " already exists");
        }
    }

    /** Defaults to the machine's current engine hours; may be lower (back-dating) but never higher. */
    private BigDecimal resolveHours(Vehicle host, BigDecimal requested) {
        BigDecimal current = host.getEngineHours();
        if (requested == null) return current;
        if (requested.compareTo(current) > 0) {
            throw new IllegalArgumentException("Engine hours (" + requested + ") are above the machine's current reading ("
                    + current + ") — record the machine's hours first");
        }
        return requested;
    }

    private Instant resolveTime(Instant requested) {
        Instant now = Instant.now();
        if (requested == null) return now;
        if (requested.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("The date/time cannot be in the future");
        }
        return requested;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ── Mapping ──────────────────────────────────────────────────────────

    private AttachmentResponse toSummary(AssetAttachment a, Vehicle host) {
        return new AttachmentResponse(a.getId(), a.getName(), a.getCategory(), a.getSerialNumber(), a.getStatus().name(),
                a.getCurrentVehicleId(), host != null ? host.getRegistration() : null, a.getAttachedAt(),
                a.totalHoursInUse(host != null ? host.getEngineHours() : null));
    }

    private AttachmentDetailResponse toDetail(AssetAttachment a, TenantId tenantId) {
        Map<UUID, Vehicle> vehicles = new HashMap<>();
        Vehicle host = vehicleOf(tenantId, a.getCurrentVehicleId(), vehicles);
        AttachmentResponse summary = toSummary(a, host);

        List<AttachmentAssignmentResponse> history = assignmentRepository.findByAttachment(tenantId, a.getId()).stream()
                .map(s -> {
                    Vehicle v = vehicleOf(tenantId, s.getVehicleId(), vehicles);
                    return new AttachmentAssignmentResponse(s.getVehicleId(), v != null ? v.getRegistration() : null,
                            s.getAttachedAt(), s.getAttachedEngineHours(), s.getDetachedAt(),
                            s.getDetachedEngineHours(), s.getHours());
                }).toList();

        BigDecimal hours = summary.totalHoursInUse();
        BigDecimal costPerHour = a.getPurchasePrice() != null && a.getPurchasePrice().signum() > 0 && hours.signum() > 0
                ? a.getPurchasePrice().divide(hours, 2, RoundingMode.HALF_UP) : null;
        return new AttachmentDetailResponse(summary, a.getDescription(), a.getPurchaseDate(), a.getPurchasePrice(),
                costPerHour, a.getNotes(), history);
    }
}