package za.co.handyflow.platform.facilities.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnician;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence;
import za.co.handyflow.platform.facilities.domain.repository.FacilityTechnicianLicenceRepository;
import za.co.handyflow.platform.facilities.domain.repository.FacilityTechnicianRepository;
import za.co.handyflow.platform.facilities.dto.TechnicianLicenceRequest;
import za.co.handyflow.platform.facilities.dto.TechnicianLicenceResponse;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Technician licences: validation, ownership, revoking, the attention list, reminders. */
class FacilityTechnicianLicenceServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = FacilityTime.today();
    private final FacilityTechnician thabo = FacilityTechnician.create(tenant, "Thabo", null, null, "GAS", null);
    private final FacilityTechnician other = FacilityTechnician.create(tenant, "Lerato", null, null, "GENERAL", null);
    private final Map<UUID, FacilityTechnicianLicence> licences = new LinkedHashMap<>();
    private final List<String> audit = new ArrayList<>();
    private final List<String> alertTitles = new ArrayList<>();
    private final List<NotificationType> alertTypes = new ArrayList<>();

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

    private final FacilityTechnicianLicenceRepository repo = fake(FacilityTechnicianLicenceRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityTechnicianLicence l = (FacilityTechnicianLicence) a[0]; licences.put(l.getId(), l); yield l; }
        case "findByIdForTenant" -> Optional.ofNullable(licences.get(a[1]));
        case "findForTechnician" -> licences.values().stream().filter(l -> l.getTechnicianId().equals(a[1])).toList();
        case "findLapsedOrExpiringForActiveTechnicians", "findValidExpiringAcrossTenants" -> {
            LocalDate cutoff = (LocalDate) a[a.length - 1];
            yield licences.values().stream().filter(l -> "VALID".equals(l.getStatus()) && l.getExpiryDate() != null
                    && !l.getExpiryDate().isAfter(cutoff)).toList();
        }
        default -> NOPE;
    });
    private final FacilityTechnicianRepository techRepo = fake(FacilityTechnicianRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(thabo.getId().equals(a[1]) ? thabo : other.getId().equals(a[1]) ? other : null) : NOPE);
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityAlerts alerts = new FacilityAlerts(
            new NotificationService(null, null) {
                @Override public void send(za.co.handyflow.platform.notifications.application.NotificationRequest r) {
                    alertTypes.add(r.type());
                    alertTitles.add(r.title());
                }
            },
            t -> List.of(new za.co.handyflow.platform.notifications.application.Recipient(UUID.randomUUID(), "Admin", "a@x.co.za", null)));
    private final FacilityTechnicianLicenceService service = new FacilityTechnicianLicenceService(repo, techRepo, auditService, alerts);

    private TechnicianLicenceRequest req(String type, int expiresInDays) {
        return new TechnicianLicenceRequest(type, "REF-1", "SAQCC", today.minusYears(2), today.plusDays(expiresInDays));
    }

    @Test
    void addingALicenceIsAuditedAndReportsDaysToExpiry() {
        TechnicianLicenceResponse r = service.add(tenant, thabo.getId(), req("gas_installer", 40));
        assertEquals("GAS_INSTALLER", r.licenceType());
        assertEquals("Thabo", r.technicianName());
        assertEquals(Long.valueOf(40), r.daysToExpiry());
        assertTrue(r.inForce());
        assertEquals(List.of("TECHNICIAN_LICENCE_ADDED"), audit);
    }

    @Test
    void unknownTypesAndMissingExpiryAreRejectedExceptForOther() {
        assertThrows(IllegalArgumentException.class, () -> service.add(tenant, thabo.getId(), req("PILOT", 40)));
        assertThrows(IllegalArgumentException.class, () -> service.add(tenant, thabo.getId(),
                new TechnicianLicenceRequest("PLUMBING", null, null, null, null)));
        TechnicianLicenceResponse other = service.add(tenant, thabo.getId(), new TechnicianLicenceRequest("OTHER", null, null, null, null));
        assertNull(other.expiryDate());
        assertNull(other.daysToExpiry());
        assertTrue(other.inForce());
    }

    @Test
    void anExpiryBeforeTheIssueDateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.add(tenant, thabo.getId(),
                new TechnicianLicenceRequest("PLUMBING", null, null, today, today.minusDays(1))));
    }

    @Test
    void anUnknownTechnicianIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.add(tenant, UUID.randomUUID(), req("PLUMBING", 40)));
        assertThrows(ResourceNotFoundException.class, () -> service.list(tenant, UUID.randomUUID()));
    }

    @Test
    void aLapsedLicenceIsNotInForce() {
        TechnicianLicenceResponse r = service.add(tenant, thabo.getId(), req("PLUMBING", -5));
        assertFalse(r.inForce());
        assertEquals(Long.valueOf(-5), r.daysToExpiry());
    }

    @Test
    void revokingNeedsAReasonAndAnOwnLicenceAndIsAudited() {
        UUID id = service.add(tenant, thabo.getId(), req("PLUMBING", 40)).id();
        assertThrows(ResourceNotFoundException.class, () -> service.revoke(tenant, other.getId(), id, "x")); // someone else's
        assertThrows(IllegalArgumentException.class, () -> service.revoke(tenant, thabo.getId(), id, " "));
        TechnicianLicenceResponse r = service.revoke(tenant, thabo.getId(), id, "Struck off");
        assertEquals("REVOKED", r.status());
        assertFalse(r.inForce());
        assertEquals("Struck off", r.revokedReason());
        assertEquals(List.of("TECHNICIAN_LICENCE_ADDED", "TECHNICIAN_LICENCE_REVOKED"), audit);
        assertThrows(IllegalStateException.class, () -> service.revoke(tenant, thabo.getId(), id, "again"));
    }

    @Test
    void listShowsOnlyThatTechniciansLicences() {
        service.add(tenant, thabo.getId(), req("PLUMBING", 40));
        service.add(tenant, other.getId(), req("FIRST_AID", 40));
        assertEquals(1, service.list(tenant, thabo.getId()).size());
    }

    @Test
    void theAttentionListHoldsLapsedAndSoonToExpireLicencesWithNames() {
        service.add(tenant, thabo.getId(), req("PLUMBING", -3));
        service.add(tenant, other.getId(), req("FIRST_AID", 20));
        service.add(tenant, other.getId(), req("GAS_INSTALLER", 200));
        List<TechnicianLicenceResponse> r = service.needingAttention(tenant, null);
        assertEquals(2, r.size());
        assertTrue(r.stream().anyMatch(x -> "Thabo".equals(x.technicianName())));
        assertEquals(1, service.needingAttention(tenant, 0).size()); // only the lapsed one
        assertThrows(IllegalArgumentException.class, () -> service.needingAttention(tenant, 400));
    }

    @Test
    void remindersGoOutOnMilestoneDaysAndOnceAfterLapsing() {
        service.add(tenant, thabo.getId(), req("PLUMBING", 7));     // milestone
        service.add(tenant, thabo.getId(), req("FIRST_AID", 10));   // not a milestone
        service.add(tenant, thabo.getId(), req("GAS_INSTALLER", -1)); // lapsed yesterday
        service.add(tenant, thabo.getId(), req("HVAC_REFRIGERATION", -9)); // lapsed long ago: already told
        assertEquals(2, service.sendReminders(today));
        assertTrue(alertTypes.contains(NotificationType.FACILITY_TECHNICIAN_LICENCE_EXPIRING));
        assertTrue(alertTypes.contains(NotificationType.FACILITY_TECHNICIAN_LICENCE_EXPIRED));
        assertTrue(alertTitles.stream().allMatch(t -> t.contains("Thabo")));
    }

    @Test
    void revokedLicencesAreNotRemindedAbout() {
        UUID id = service.add(tenant, thabo.getId(), req("PLUMBING", 7)).id();
        service.revoke(tenant, thabo.getId(), id, "Struck off");
        assertEquals(0, service.sendReminders(today));
    }
}
