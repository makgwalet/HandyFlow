package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAsset;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionSchedule;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionTemplate;
import za.co.handyflow.platform.facilities.domain.model.FacilitySite;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAssetRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityInspectionScheduleRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityInspectionTemplateRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilitySiteRepository;
import za.co.handyflow.platform.facilities.dto.InspectionScheduleRequest;
import za.co.handyflow.platform.facilities.dto.InspectionScheduleResponse;
import za.co.handyflow.platform.facilities.dto.UpdateInspectionScheduleRequest;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Recurring inspections. A schedule says "run this checklist at this site (or asset) every N days". It does not create
 * inspections: it reminds, and when an inspection for the same checklist, site and asset is completed the due date
 * moves on. A schedule is tied to a site, so every user-facing call checks the caller can see that site.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityInspectionScheduleService {

    /** Look ahead this far for upcoming inspections when sweeping (the largest reminder lead time). */
    static final int REMINDER_HORIZON_DAYS = 7;

    private final FacilityInspectionScheduleRepository scheduleRepository;
    private final FacilityInspectionTemplateRepository templateRepository;
    private final FacilitySiteRepository siteRepository;
    private final FacilityAssetRepository assetRepository;
    private final FacilityAccessPolicy accessPolicy;
    private final FacilityAuditService auditService;
    private final FacilityAlerts alerts;

    // ── user-facing ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InspectionScheduleResponse> list(TenantId tenantId, UUID siteId, Pageable pageable) {
        AccessScope scope = accessPolicy.scope(tenantId);
        if (siteId != null) scope.requireSite(siteId);
        Names names = new Names(tenantId);
        LocalDate today = FacilityTime.today();
        return scheduleRepository.findAllInScope(tenantId, siteId, scope.allSites(), scope.siteIds(), pageable)
                .map(s -> toResponse(s, names, today));
    }

    @Transactional
    public InspectionScheduleResponse create(TenantId tenantId, InspectionScheduleRequest req) {
        accessPolicy.scope(tenantId).requireSite(req.siteId());
        FacilityInspectionTemplate template = templateRepository.findByIdForTenant(tenantId, req.templateId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilityInspectionTemplate", req.templateId().toString()));
        if (!template.isActive()) throw new IllegalStateException("This checklist is switched off — activate it first");
        siteRepository.findActiveById(tenantId, req.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("FacilitySite", req.siteId().toString()));
        if (req.assetId() != null) {
            FacilityAsset asset = assetRepository.findActiveById(tenantId, req.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("FacilityAsset", req.assetId().toString()));
            if (!asset.getSiteId().equals(req.siteId()))
                throw new IllegalArgumentException("The selected asset does not belong to the selected site");
        }
        Optional<FacilityInspectionSchedule> existing = req.assetId() != null
                ? scheduleRepository.findForAsset(tenantId, req.templateId(), req.siteId(), req.assetId())
                : scheduleRepository.findForSite(tenantId, req.templateId(), req.siteId());
        if (existing.isPresent())
            throw new HandyFlowException("This checklist is already scheduled for that site/asset", HttpStatus.CONFLICT, "SCHEDULE_EXISTS");

        FacilityInspectionSchedule s = FacilityInspectionSchedule.create(tenantId, req.templateId(), req.siteId(), req.assetId(),
                req.frequencyDays(), req.firstDueDate());
        scheduleRepository.save(s);
        auditService.record(tenantId, "INSPECTION_SCHEDULE_CREATED", "INSPECTION_SCHEDULE", s.getId(),
                template.getName() + " every " + s.getFrequencyDays() + "d, next " + s.getNextDueDate());
        return toResponse(s, new Names(tenantId), FacilityTime.today());
    }

    @Transactional
    public InspectionScheduleResponse update(TenantId tenantId, UUID id, UpdateInspectionScheduleRequest req) {
        FacilityInspectionSchedule s = findScoped(tenantId, id);
        s.update(req.frequencyDays(), req.nextDueDate());
        scheduleRepository.save(s);
        auditService.record(tenantId, "INSPECTION_SCHEDULE_UPDATED", "INSPECTION_SCHEDULE", id,
                "every " + s.getFrequencyDays() + "d, next " + s.getNextDueDate());
        return toResponse(s, new Names(tenantId), FacilityTime.today());
    }

    @Transactional
    public InspectionScheduleResponse setActive(TenantId tenantId, UUID id, boolean active) {
        FacilityInspectionSchedule s = findScoped(tenantId, id);
        if (active) s.reactivate(); else s.deactivate();
        scheduleRepository.save(s);
        auditService.record(tenantId, active ? "INSPECTION_SCHEDULE_REACTIVATED" : "INSPECTION_SCHEDULE_DEACTIVATED",
                "INSPECTION_SCHEDULE", id, null);
        return toResponse(s, new Names(tenantId), FacilityTime.today());
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        FacilityInspectionSchedule s = findScoped(tenantId, id);
        s.softDelete();
        scheduleRepository.save(s);
        auditService.record(tenantId, "INSPECTION_SCHEDULE_DELETED", "INSPECTION_SCHEDULE", id, null);
    }

    // ── callbacks from the inspection itself ─────────────────────────────────

    /** The schedule (if any) that an inspection of this checklist, site and asset belongs to. Not scoped: a system lookup. */
    @Transactional(readOnly = true)
    public Optional<UUID> findScheduleId(TenantId tenantId, UUID templateId, UUID siteId, UUID assetId) {
        Optional<FacilityInspectionSchedule> s = assetId != null
                ? scheduleRepository.findForAsset(tenantId, templateId, siteId, assetId)
                : scheduleRepository.findForSite(tenantId, templateId, siteId);
        return s.filter(FacilityInspectionSchedule::isActive).map(FacilityInspectionSchedule::getId);
    }

    /** An inspection belonging to {@code scheduleId} was completed: move the due date on. */
    @Transactional
    public void applyCompletion(TenantId tenantId, UUID scheduleId, LocalDate completedOn) {
        scheduleRepository.findByIdForTenant(tenantId, scheduleId).ifPresentOrElse(s -> {
            s.recordCompleted(completedOn);
            scheduleRepository.save(s);
        }, () -> log.warn("Inspection schedule {} not found for tenant {} on completion — nothing to advance", scheduleId, tenantId));
    }

    // ── daily reminders ──────────────────────────────────────────────────────

    /**
     * Remind tenant administrators about inspections coming due (7, 3, 1 and 0 days) and overdue (the same milestones as
     * overdue work orders). Cross-tenant; one bad schedule never stops the rest. Returns how many alerts were raised.
     */
    public int sendReminders(LocalDate today) {
        int sent = 0;
        for (FacilityInspectionSchedule s : scheduleRepository.findDueAcrossTenants(today.plusDays(REMINDER_HORIZON_DAYS))) {
            try {
                long daysUntilDue = ChronoUnit.DAYS.between(today, s.getNextDueDate());
                if (!FacilityInspectionScheduleRules.shouldRemind(daysUntilDue)) continue;
                Names names = new Names(s.getTenantId());
                String what = names.template(s.getTemplateId()) + " at " + names.site(s.getSiteId())
                        + (s.getAssetId() != null ? " (" + names.asset(s.getAssetId()) + ")" : "");
                String when = daysUntilDue > 0 ? "due in " + daysUntilDue + " day(s), on " + s.getNextDueDate()
                        : daysUntilDue == 0 ? "due today"
                        : "overdue by " + (-daysUntilDue) + " day(s), was due " + s.getNextDueDate();
                alerts.toAdmins(s.getTenantId(), NotificationType.FACILITY_INSPECTION_DUE,
                        (daysUntilDue < 0 ? "Inspection overdue: " : "Inspection due: ") + what,
                        "The recurring inspection " + what + " is " + when + ".",
                        "/facilities/inspections", s.getId().toString());
                sent++;
            } catch (Exception e) {
                log.error("[Facilities] Could not send inspection reminder for schedule={}: {}", s.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FacilityInspectionSchedule findScoped(TenantId tenantId, UUID id) {
        AccessScope scope = accessPolicy.scope(tenantId);
        FacilityInspectionSchedule s = scheduleRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityInspectionSchedule", id.toString()));
        if (!scope.canSeeSite(s.getSiteId())) throw new ResourceNotFoundException("FacilityInspectionSchedule", id.toString());
        return s;
    }

    private InspectionScheduleResponse toResponse(FacilityInspectionSchedule s, Names names, LocalDate today) {
        return new InspectionScheduleResponse(s.getId(), s.getTemplateId(), names.template(s.getTemplateId()), s.getSiteId(),
                names.site(s.getSiteId()), s.getAssetId(), s.getAssetId() == null ? null : names.asset(s.getAssetId()),
                s.getFrequencyDays(), s.getNextDueDate(), s.getLastCompletedDate(), s.isActive(), s.isOverdue(today),
                ChronoUnit.DAYS.between(today, s.getNextDueDate()), s.getCreatedAt());
    }

    /** Names for display, looked up once per id for the lifetime of one call. */
    private final class Names {
        private final TenantId tenantId;
        private final Map<UUID, String> templates = new HashMap<>(), sites = new HashMap<>(), assets = new HashMap<>();

        Names(TenantId tenantId) { this.tenantId = tenantId; }

        String template(UUID id) {
            return templates.computeIfAbsent(id, k -> templateRepository.findByIdForTenant(tenantId, k)
                    .map(FacilityInspectionTemplate::getName).orElse("Checklist"));
        }

        String site(UUID id) {
            return sites.computeIfAbsent(id, k -> siteRepository.findActiveById(tenantId, k).map(FacilitySite::getName).orElse("Site"));
        }

        String asset(UUID id) {
            return assets.computeIfAbsent(id, k -> assetRepository.findActiveById(tenantId, k).map(FacilityAsset::getName).orElse("Asset"));
        }
    }
}
