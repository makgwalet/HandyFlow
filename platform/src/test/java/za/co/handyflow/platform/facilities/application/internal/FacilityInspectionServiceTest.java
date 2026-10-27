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

/** Inspection templates and runs: scope, audit, corrective actions on failure, alerts. Plain JUnit with fakes. */
class FacilityInspectionServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID me = UUID.randomUUID();
    private final FacilitySite site = FacilitySite.create(tenant, "Head Office", "OFFICE", null, null);

    private final Map<UUID, FacilityInspectionTemplate> templates = new LinkedHashMap<>();
    private final Map<UUID, FacilityInspection> inspections = new LinkedHashMap<>();
    private final List<FacilityCorrectiveAction> actions = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertTypes = new ArrayList<>();
    private AccessScope scope = AccessScope.unrestricted();
    private FacilityAsset otherAsset;

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

    private final FacilityInspectionTemplateRepository templateRepo = fake(FacilityInspectionTemplateRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityInspectionTemplate t = (FacilityInspectionTemplate) a[0]; templates.put(t.getId(), t); yield t; }
        case "findByIdForTenant" -> Optional.ofNullable(templates.get(a[1]));
        case "existsByName" -> templates.values().stream().anyMatch(t -> t.getName().equalsIgnoreCase((String) a[1]) && !t.getId().equals(a[2]));
        default -> NOPE;
    });
    private final FacilityInspectionRepository inspectionRepo = fake(FacilityInspectionRepository.class, (n, a) -> switch (n) {
        case "save" -> { FacilityInspection i = (FacilityInspection) a[0]; inspections.put(i.getId(), i); yield i; }
        case "findByIdForTenant" -> Optional.ofNullable(inspections.get(a[1]));
        default -> NOPE;
    });
    private final FacilityCorrectiveActionRepository actionRepo = fake(FacilityCorrectiveActionRepository.class, (n, a) -> switch (n) {
        case "save" -> { actions.add((FacilityCorrectiveAction) a[0]); yield a[0]; }
        case "findBySource" -> actions.stream().filter(x -> x.getSourceId() != null && x.getSourceId().equals(a[2])).toList();
        default -> NOPE;
    });
    private final FacilitySiteRepository siteRepo = fake(FacilitySiteRepository.class, (n, a) ->
            "findActiveById".equals(n) ? Optional.ofNullable(site.getId().equals(a[1]) ? site : null) : NOPE);
    private final FacilityAssetRepository assetRepo = fake(FacilityAssetRepository.class, (n, a) -> "findActiveById".equals(n)
            ? Optional.ofNullable(otherAsset != null && otherAsset.getId().equals(a[1]) ? otherAsset : null) : NOPE);

    private final FacilityAccessPolicy policy = new FacilityAccessPolicy(null, null, null) {
        @Override public AccessScope scope(TenantId t) { return scope; }
    };
    private final FacilityAuditService auditService = new FacilityAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FacilityAlerts alerts = new FacilityAlerts(
            new NotificationService(null, null) {
                @Override public void send(za.co.handyflow.platform.notifications.application.NotificationRequest r) { alertTypes.add(r.type()); }
            },
            t -> List.of(new za.co.handyflow.platform.notifications.application.Recipient(UUID.randomUUID(), "Admin", "a@x.co.za", null)));
    private final FacilityCorrectiveActionService actionService = new FacilityCorrectiveActionService(
            actionRepo, siteRepo, assetRepo, null, null, null, policy, auditService);
    private final FacilityInspectionService service = new FacilityInspectionService(templateRepo, inspectionRepo, actionRepo,
            siteRepo, assetRepo, policy, auditService, actionService, alerts);

    private InspectionTemplateRequest templateReq(String name) {
        return new InspectionTemplateRequest(name, "d", "hvac", List.of(
                new InspectionTemplateRequest.Item("Filter clean", false), new InspectionTemplateRequest.Item("Gas leak check", true)));
    }

    private InspectionResponse startInspection() {
        TenantContext.setUserId(me.toString());
        TenantContext.setUserName("Sipho");
        InspectionTemplateResponse t = service.createTemplate(tenant, templateReq("Aircon monthly"));
        return service.start(tenant, new StartInspectionRequest(t.id(), site.getId(), null));
    }

    private void answerAll(InspectionResponse r, String first, String second) {
        service.answer(tenant, r.id(), new InspectionAnswersRequest(List.of(
                new InspectionAnswersRequest.Answer(r.results().get(0).itemId(), first, "first".equals(first) ? null : "bad filter"),
                new InspectionAnswersRequest.Answer(r.results().get(1).itemId(), second, "FAIL".equals(second) ? "smells of gas" : null))));
    }

    // ── templates ────────────────────────────────────────────────────────────

    @Test
    void creatingATemplateIsAuditedAndNamesMustBeUnique() {
        InspectionTemplateResponse t = service.createTemplate(tenant, templateReq("Aircon monthly"));
        assertEquals("HVAC", t.assetCategory());
        assertEquals(List.of("INSPECTION_TEMPLATE_CREATED"), audit);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.createTemplate(tenant, templateReq("  aircon MONTHLY ")));
        assertEquals("TEMPLATE_NAME_TAKEN", e.getErrorCode());
    }

    @Test
    void updatingATemplateKeepsItsOwnNameAndBumpsTheRevision() {
        InspectionTemplateResponse t = service.createTemplate(tenant, templateReq("Aircon monthly"));
        InspectionTemplateResponse u = service.updateTemplate(tenant, t.id(), templateReq("Aircon monthly"));
        assertEquals(2, u.revision());
        service.createTemplate(tenant, templateReq("Other"));
        assertThrows(HandyFlowException.class, () -> service.updateTemplate(tenant, t.id(), templateReq("Other")));
    }

    @Test
    void aTemplateCanBeSwitchedOffAndThenCannotBeStarted() {
        TenantContext.setUserId(me.toString());
        InspectionTemplateResponse t = service.createTemplate(tenant, templateReq("Aircon monthly"));
        service.setTemplateActive(tenant, t.id(), false);
        assertThrows(IllegalStateException.class, () -> service.start(tenant, new StartInspectionRequest(t.id(), site.getId(), null)));
        service.setTemplateActive(tenant, t.id(), true);
        assertEquals("IN_PROGRESS", service.start(tenant, new StartInspectionRequest(t.id(), site.getId(), null)).status());
    }

    // ── inspections ──────────────────────────────────────────────────────────

    @Test
    void startingRecordsTheInspectorAndPrefillsEveryCheck() {
        InspectionResponse r = startInspection();
        assertEquals(me, r.inspectorUserId());
        assertEquals("Sipho", r.inspectorName());
        assertEquals(2, r.total());
        assertEquals(0, r.answered());
        assertTrue(audit.contains("INSPECTION_STARTED"));
    }

    @Test
    void anInspectionAtASiteOutsideTheCallersScopeLooksLikeItDoesNotExist() {
        scope = AccessScope.restrictedToSites(List.of(UUID.randomUUID()));
        TenantContext.setUserId(me.toString());
        InspectionTemplateResponse t = service.createTemplate(tenant, templateReq("A"));
        assertThrows(ResourceNotFoundException.class, () -> service.start(tenant, new StartInspectionRequest(t.id(), site.getId(), null)));
        assertThrows(ResourceNotFoundException.class, () -> service.list(tenant, site.getId(), null, null, null));
    }

    @Test
    void anotherSitesInspectionCannotBeReadOrChanged() {
        InspectionResponse r = startInspection();
        scope = AccessScope.restrictedToSites(List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.get(tenant, r.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.answer(tenant, r.id(), new InspectionAnswersRequest(List.of(
                new InspectionAnswersRequest.Answer(r.results().get(0).itemId(), "PASS", null)))));
        assertThrows(ResourceNotFoundException.class, () -> service.complete(tenant, r.id(), null));
    }

    @Test
    void anAssetFromAnotherSiteIsRejected() {
        TenantContext.setUserId(me.toString());
        otherAsset = FacilityAsset.create(tenant, UUID.randomUUID(), null, "Chiller", "HVAC", null, null, null, null, null, null, null, null);
        InspectionTemplateResponse t = service.createTemplate(tenant, templateReq("A"));
        assertThrows(IllegalArgumentException.class,
                () -> service.start(tenant, new StartInspectionRequest(t.id(), site.getId(), otherAsset.getId())));
    }

    @Test
    void aPassingInspectionRaisesNoActionsAndNoAlert() {
        InspectionResponse r = startInspection();
        answerAll(r, "PASS", "PASS");
        InspectionResponse done = service.complete(tenant, r.id(), new CompleteInspectionRequest("fine"));
        assertEquals("PASS", done.result());
        assertTrue(done.actions().isEmpty());
        assertTrue(actions.isEmpty());
        assertTrue(alertTypes.isEmpty());
        assertTrue(audit.contains("INSPECTION_COMPLETED"));
    }

    @Test
    void aFailedInspectionRaisesOneActionPerFailureAndAlertsTheAdmins() {
        InspectionResponse r = startInspection();
        answerAll(r, "FAIL", "FAIL");
        InspectionResponse done = service.complete(tenant, r.id(), null);
        assertEquals("FAIL", done.result());
        assertEquals(2, done.actions().size());
        assertEquals(2, actions.size());

        FacilityCorrectiveAction ordinary = actions.get(0);
        FacilityCorrectiveAction critical = actions.get(1);
        assertFalse(ordinary.isCritical());
        assertEquals("NORMAL", ordinary.getPriority());
        assertEquals(LocalDate.now(FacilityTime.ZONE).plusDays(14), ordinary.getDueDate());
        assertTrue(critical.isCritical());
        assertEquals("HIGH", critical.getPriority());
        assertEquals(FacilityTime.today().plusDays(2), critical.getDueDate());
        assertEquals("INSPECTION", critical.getSourceType());
        assertEquals(r.id(), critical.getSourceId());
        assertTrue(critical.getDescription().contains("Gas leak check"));
        assertTrue(critical.getDescription().contains("smells of gas"));

        assertEquals(List.of(NotificationType.FACILITY_INSPECTION_FAILED), alertTypes);
        assertEquals(2, audit.stream().filter("ACTION_CREATED"::equals).count());
    }

    @Test
    void completingTwiceDoesNotRaiseActionsTwice() {
        InspectionResponse r = startInspection();
        answerAll(r, "FAIL", "PASS");
        service.complete(tenant, r.id(), null);
        assertThrows(IllegalStateException.class, () -> service.complete(tenant, r.id(), null));
        assertEquals(1, actions.size());
        assertEquals(1, alertTypes.size());
    }

    @Test
    void anIncompleteInspectionCannotBeCompletedAndRaisesNothing() {
        InspectionResponse r = startInspection();
        service.answer(tenant, r.id(), new InspectionAnswersRequest(List.of(
                new InspectionAnswersRequest.Answer(r.results().get(0).itemId(), "FAIL", "bad"))));
        assertThrows(IllegalStateException.class, () -> service.complete(tenant, r.id(), null));
        assertTrue(actions.isEmpty());
        assertTrue(alertTypes.isEmpty());
    }

    @Test
    void getShowsTheActionsRaisedByAnInspection() {
        InspectionResponse r = startInspection();
        answerAll(r, "FAIL", "PASS");
        service.complete(tenant, r.id(), null);
        assertEquals(1, service.get(tenant, r.id()).actions().size());
    }

    @Test
    void cancellingIsAuditedAndNeedsAReason() {
        InspectionResponse r = startInspection();
        assertThrows(IllegalArgumentException.class, () -> service.cancel(tenant, r.id(), " "));
        assertEquals("CANCELLED", service.cancel(tenant, r.id(), "Site closed").status());
        assertTrue(audit.contains("INSPECTION_CANCELLED"));
    }
}
