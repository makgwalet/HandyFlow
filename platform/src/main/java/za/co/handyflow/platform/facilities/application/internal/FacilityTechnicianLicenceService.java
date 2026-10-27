package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnician;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence;
import za.co.handyflow.platform.facilities.domain.repository.FacilityTechnicianLicenceRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityTechnicianRepository;
import za.co.handyflow.platform.facilities.dto.TechnicianLicenceRequest;
import za.co.handyflow.platform.facilities.dto.TechnicianLicenceResponse;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Technician licences: record, revoke and watch the trade licences technicians hold. Adding and revoking are audited;
 * the daily sweep reminds tenant admins before a licence expires and once after it lapses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityTechnicianLicenceService {

    private static final int ATTENTION_DEFAULT_DAYS = 30;

    private final FacilityTechnicianLicenceRepository licenceRepository;
    private final FacilityTechnicianRepository technicianRepository;
    private final FacilityAuditService auditService;
    private final FacilityAlerts alerts;

    @Transactional(readOnly = true)
    public List<TechnicianLicenceResponse> list(TenantId tenantId, UUID technicianId) {
        FacilityTechnician t = findTechnician(tenantId, technicianId);
        LocalDate today = FacilityTime.today();
        return licenceRepository.findForTechnician(tenantId, technicianId).stream()
                .map(l -> toResponse(l, t.getName(), today)).toList();
    }

    /** Licences that have lapsed or expire within {@code withinDays} (default 30), across all active technicians. */
    @Transactional(readOnly = true)
    public List<TechnicianLicenceResponse> needingAttention(TenantId tenantId, Integer withinDays) {
        int days = withinDays == null ? ATTENTION_DEFAULT_DAYS : withinDays;
        if (days < 0 || days > 365) throw new IllegalArgumentException("withinDays must be between 0 and 365");
        LocalDate today = FacilityTime.today();
        List<FacilityTechnicianLicence> licences = licenceRepository.findLapsedOrExpiringForActiveTechnicians(tenantId, today.plusDays(days));
        Map<UUID, String> names = new HashMap<>();
        return licences.stream().map(l -> toResponse(l, names.computeIfAbsent(l.getTechnicianId(),
                id -> technicianRepository.findActiveById(tenantId, id).map(FacilityTechnician::getName).orElse("Technician")), today)).toList();
    }

    @Transactional
    public TechnicianLicenceResponse add(TenantId tenantId, UUID technicianId, TechnicianLicenceRequest req) {
        FacilityTechnician t = findTechnician(tenantId, technicianId);
        FacilityTechnicianLicence l = FacilityTechnicianLicence.create(tenantId, technicianId, req.licenceType(), req.reference(),
                req.issuedBy(), req.issueDate(), req.expiryDate());
        licenceRepository.save(l);
        auditService.record(tenantId, "TECHNICIAN_LICENCE_ADDED", "TECHNICIAN", technicianId,
                "technician=" + t.getName() + " type=" + l.getLicenceType() + " ref=" + l.getReference() + " expires=" + l.getExpiryDate());
        return toResponse(l, t.getName(), FacilityTime.today());
    }

    @Transactional
    public TechnicianLicenceResponse revoke(TenantId tenantId, UUID technicianId, UUID licenceId, String reason) {
        FacilityTechnician t = findTechnician(tenantId, technicianId);
        FacilityTechnicianLicence l = find(tenantId, technicianId, licenceId);
        l.revoke(reason);
        licenceRepository.save(l);
        auditService.record(tenantId, "TECHNICIAN_LICENCE_REVOKED", "TECHNICIAN", technicianId,
                "type=" + l.getLicenceType() + " ref=" + l.getReference() + " reason=" + l.getRevokedReason());
        return toResponse(l, t.getName(), FacilityTime.today());
    }

    /** Throws NOT FOUND unless the licence belongs to this technician (used by the evidence endpoints). */
    @Transactional(readOnly = true)
    public void requireExists(TenantId tenantId, UUID technicianId, UUID licenceId) {
        findTechnician(tenantId, technicianId);
        find(tenantId, technicianId, licenceId);
    }

    /** Daily sweep, across tenants: remind admins on the milestone days. Returns the number of alerts raised. */
    public int sendReminders(LocalDate today) {
        int sent = 0;
        for (FacilityTechnicianLicence l : licenceRepository.findValidExpiringAcrossTenants(today.plusDays(30))) {
            long daysLeft = ChronoUnit.DAYS.between(today, l.getExpiryDate());
            if (!FacilityLicenceRules.shouldRemind(daysLeft)) continue;
            try {
                String who = technicianRepository.findActiveById(l.getTenantId(), l.getTechnicianId())
                        .map(FacilityTechnician::getName).orElse("a technician");
                String what = l.getLicenceType() + (l.getReference() != null ? " " + l.getReference() : "");
                if (daysLeft < 0) {
                    alerts.toAdmins(l.getTenantId(), NotificationType.FACILITY_TECHNICIAN_LICENCE_EXPIRED,
                            "Technician licence expired: " + who, what + " expired on " + l.getExpiryDate() + ".",
                            "/facilities/technicians", l.getId().toString());
                } else {
                    alerts.toAdmins(l.getTenantId(), NotificationType.FACILITY_TECHNICIAN_LICENCE_EXPIRING,
                            "Technician licence expiring: " + who, what + " expires in " + daysLeft + " day(s) on " + l.getExpiryDate() + ".",
                            "/facilities/technicians", l.getId().toString());
                }
                sent++;
            } catch (Exception e) {
                log.error("[Facilities] Licence reminder failed for licence={}: {}", l.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    private FacilityTechnician findTechnician(TenantId tenantId, UUID id) {
        return technicianRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityTechnician", id.toString()));
    }

    private FacilityTechnicianLicence find(TenantId tenantId, UUID technicianId, UUID licenceId) {
        return licenceRepository.findByIdForTenant(tenantId, licenceId)
                .filter(x -> x.getTechnicianId().equals(technicianId))
                .orElseThrow(() -> new ResourceNotFoundException("FacilityTechnicianLicence", licenceId.toString()));
    }

    static TechnicianLicenceResponse toResponse(FacilityTechnicianLicence l, String technicianName, LocalDate today) {
        Long daysToExpiry = l.getExpiryDate() == null ? null : ChronoUnit.DAYS.between(today, l.getExpiryDate());
        return new TechnicianLicenceResponse(l.getId(), l.getTechnicianId(), technicianName, l.getLicenceType(), l.getReference(),
                l.getIssuedBy(), l.getIssueDate(), l.getExpiryDate(), daysToExpiry, l.getStatus(), l.isInForce(today),
                l.getRevokedReason(), l.getCreatedAt());
    }
}
