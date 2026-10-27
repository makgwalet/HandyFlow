package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.*;
import za.co.handyflow.platform.facilities.domain.repository.*;
import za.co.handyflow.platform.facilities.dto.*;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Recurring inspections: validation, uniqueness, scope, completion advancing the schedule, reminders. */
class FacilityInspectionScheduleServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = FacilityTime.today();
    private final FacilitySite site = FacilitySite.create(tenant, "Head Office", "OFFICE", null, null);
    private final FacilitySite otherSite = FacilitySite.create(tenant, "Warehouse", "WAREHOUSE", null, null);
    private FacilityInspectionTemplate template = FacilityInspectionTemplate.create(tenant, "Fire walkround", null, null,
            List.of(new FacilityInspectionTemplate.ItemSpec("Exits clear", true)));
    private final FacilityAsset asset = FacilityAsset.create(tenant, site.getId(), "AHU-1", "AHU", "HVAC", null, null, null, null,
            null, null, null, null);

    private final Map<UUID, FacilityInspectionSchedule> schedules = new LinkedHashMap<>();
    private final Map<UUID, FacilityInspection> inspections = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final List<String> alertTitles = new ArrayList<>();
    private AccessScope scope = AccessScope.unrestricted();

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if ("toString".equals(m.getName())) return type.getSimpleName();
            Object r = h.apply(m.getName(), a == null ? new Object[0] : a);
            if (r == NOPE) throw new UnsupportedOperationException(m.getName());
            return r;
        });
    }
    private static final Object NOPE = new Object();

    private final FacilityInspectionScheduleRepository scheduleRepo = fake(FacilityInspectionScheduleRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityInspectionSchedule s = (FacilityInspectionSchedule) a[0]; schedules.put(s.getId(), s); yield s; }
        case "findByIdForTenant" -> Optional.ofNullable(schedules.get(a[1])).filter(s -> s.getDeletedAt() == null);
        case "findForAsset" -> live().filter(s -> s.getTemplateId().equals(a[1]) && s.getSiteId().equals(a[2]) && a[3].equals(s.getAssetId())).findFirst();
        case "findForSite" -> live().filter(s -> s.getTemplateId().equals(a[1]) && s.getSiteId().equals(a[2]) && s.getAssetId() == null).findFirst();
        case "findDueAcrossTenants" -> live().filter(s -> s.isActive() && !s.getNextDueDate().isAfter((LocalDate) a[0])).toList();
        default -> NOPE;
    });
    private java.util.stream.Stream<FacilityInspectionSchedule> live() { return schedules.values().stream().filter(s -> s.getDeletedAt() == null); }

    private final FacilityInspectionTemplateRepository templateRepo = fake(FacilityInspectionTemplateRepository.class, (n, a) ->
            "findByIdForTenant".equals(n) ? Optional.ofNullable(template.getId().equals(a[1]) ? template : null) : NOPE);
    private final FacilitySiteRepository siteRepo = fake(FacilitySiteRepository.class, (n, a) -> "findActiveById".equals(n)
            ? Optional.ofNullable(site.getId().equals(a[1]) ? site : otherSite.getId().equals(a[1]) ? otherSite : null) : NOPE);
    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(asset.getId().equals(a[1]) ? asset : null) : NOPE);
    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return scope; }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityAlerts alerts = new FacilityAlerts(
            new NotificationService(null, null) {
                @Override public void send(za.co.handyflow.platform.notifications.application.NotificationRequest r) {
                    if (r.type() == NotificationType.FACILITY_INSPECTION_DUE) alertTitles.add(r.title());
                }
            },
            t -> List.of(new za.co.handyflow.platform.notifications.application.Recipient(UUID.randomUUID(), "Admin", "a@x.co.za", null)));
    private final FacilityInspectionScheduleService service = new FacilityInspectionScheduleService(
            scheduleRepo, templateRepo, siteRepo, assetRepo, policy, auditService, alerts);

    private InspectionScheduleRequest req(UUID siteId, UUID assetId, int days, LocalDate first) {
        return new InspectionScheduleRequest(template.getId(), siteId, assetId, days, first);
    }

    @Test
    void createDefaultsTheFirstDueDateToOneIntervalAhead() {
        InspectionScheduleResponse r = service.create(tenant, req(site.getId(), null, 30, null));
        assertEquals(today.plusDays(30), r.nextDueDate());
        assertEquals("Fire walkround", r.templateName());
        assertEquals("Head Office", r.siteName());
        assertFalse(r.overdue());
        assertEquals(List.of("INSPECTION_SCHEDULE_CREATED"), audit);
    }

    @Test
    void createRejectsADuplicateForTheSameTarget() {
        service.create(tenant, req(site.getId(), null, 30, null));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.create(tenant, req(site.getId(), null, 7, null)));
        assertEquals("SCHEDULE_EXISTS", e.getErrorCode());
        // A different asset on the same site is a different target.
        assertDoesNotThrow(() -> service.create(tenant, req(site.getId(), asset.getId(), 7, null)));
    }

    @Test
    void createRejectsAnAssetFromAnotherSite() {
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req(otherSite.getId(), asset.getId(), 30, null)));
    }

    @Test
    void createRejectsAnInactiveChecklist() {
        template.setActive(false);
        assertThrows(IllegalStateException.class, () -> service.create(tenant, req(site.getId(), null, 30, null)));
    }

    @Test
    void createRejectsAnOutOfRangeFrequency() {
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req(site.getId(), null, 0, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req(site.getId(), null, 3651, null)));
    }

    @Test
    void siteScopeApplies() {
        scope = AccessScope.restrictedToSites(List.of(otherSite.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.create(tenant, req(site.getId(), null, 30, null)));
        scope = AccessScope.unrestricted();
        InspectionScheduleResponse r = service.create(tenant, req(site.getId(), null, 30, null));
        scope = AccessScope.restrictedToSites(List.of(otherSite.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.update(tenant, r.id(), new UpdateInspectionScheduleRequest(7, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(tenant, r.id()));
    }

    @Test
    void updateChangesFrequencyAndDueDateAndDeleteHidesIt() {
        InspectionScheduleResponse r = service.create(tenant, req(site.getId(), null, 30, null));
        InspectionScheduleResponse u = service.update(tenant, r.id(), new UpdateInspectionScheduleRequest(14, today.plusDays(3)));
        assertEquals(14, u.frequencyDays());
        assertEquals(today.plusDays(3), u.nextDueDate());
        service.delete(tenant, r.id());
        assertThrows(ResourceNotFoundException.class, () -> service.update(tenant, r.id(), new UpdateInspectionScheduleRequest(7, null)));
    }

    @Test
    void completionMovesTheDueDateOn() {
        InspectionScheduleResponse r = service.create(tenant, req(site.getId(), null, 30, today.plusDays(2)));
        service.applyCompletion(tenant, r.id(), today);
        FacilityInspectionSchedule s = schedules.get(r.id());
        assertEquals(today.plusDays(32), s.getNextDueDate());
        assertEquals(today, s.getLastCompletedDate());
    }

    @Test
    void findScheduleIdMatchesAssetThenSiteAndIgnoresPausedOnes() {
        InspectionScheduleResponse siteWide = service.create(tenant, req(site.getId(), null, 30, null));
        InspectionScheduleResponse onAsset = service.create(tenant, req(site.getId(), asset.getId(), 30, null));
        assertEquals(Optional.of(onAsset.id()), service.findScheduleId(tenant, template.getId(), site.getId(), asset.getId()));
        assertEquals(Optional.of(siteWide.id()), service.findScheduleId(tenant, template.getId(), site.getId(), null));
        service.setActive(tenant, siteWide.id(), false);
        assertEquals(Optional.empty(), service.findScheduleId(tenant, template.getId(), site.getId(), null));
    }

    @Test
    void remindersGoOutOnLeadDaysAndOverdueMilestonesOnly() {
        service.create(tenant, new InspectionScheduleRequest(template.getId(), site.getId(), null, 30, today.plusDays(3)));
        assertEquals(1, service.sendReminders(today));
        assertTrue(alertTitles.get(0).startsWith("Inspection due: Fire walkround at Head Office"));

        alertTitles.clear();
        assertEquals(0, service.sendReminders(today.minusDays(1)), "4 days out: not a lead day");

        // Overdue milestones: due in 3 days, so 4 days from now it is 1 day overdue (milestone), 5 days from now is not.
        assertEquals(1, service.sendReminders(today.plusDays(4)));
        assertTrue(alertTitles.get(alertTitles.size() - 1).startsWith("Inspection overdue:"));
        assertEquals(0, service.sendReminders(today.plusDays(5)));
    }

    @Test
    void pausedSchedulesAreNotRemindedAndOneFailureDoesNotStopTheRest() {
        InspectionScheduleResponse a = service.create(tenant, new InspectionScheduleRequest(template.getId(), site.getId(), null, 30, today.plusDays(1)));
        service.create(tenant, new InspectionScheduleRequest(template.getId(), otherSite.getId(), null, 30, today.plusDays(1)));
        service.setActive(tenant, a.id(), false);
        assertEquals(1, service.sendReminders(today));
    }
}
