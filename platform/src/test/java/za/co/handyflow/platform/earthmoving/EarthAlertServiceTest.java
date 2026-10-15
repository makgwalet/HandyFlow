package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.earthmoving.application.internal.EarthAlertService;
import za.co.handyflow.platform.earthmoving.application.internal.EarthmovingAccess;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.SaveAlertRuleRequest;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EarthAlertServiceTest {
    EarthAlertRepository alerts = mock(EarthAlertRepository.class);
    EarthAlertRuleRepository rules = mock(EarthAlertRuleRepository.class);
    EarthAlertRecipientRepository recipients = mock(EarthAlertRecipientRepository.class);
    EarthAlertDeliveryRepository deliveries = mock(EarthAlertDeliveryRepository.class);
    NotificationService notifications = mock(NotificationService.class);
    UserRecipientResolver users = mock(UserRecipientResolver.class);
    TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthAlertService service;
    TenantId tenant = TenantId.generate();
    UUID supervisor = UUID.randomUUID(), manager = UUID.randomUUID(), adminUser = UUID.randomUUID(), actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EarthAlertService(alerts, rules, recipients, deliveries, notifications, users, admins, access, trail);
        when(alerts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(alerts.findLiveByKey(any(), any())).thenReturn(Optional.empty());
        when(rules.findByType(any(), any())).thenReturn(Optional.empty());
        when(users.resolveUser(any(), any())).thenAnswer(i -> Optional.of(new Recipient((UUID) i.getArgument(1), "User", "u@x.co.za", null)));
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(new Recipient(adminUser, "Admin", "a@x.co.za", null)));
    }

    private EarthAlertRule rule(String type, Integer escalateAfter, UUID... level1AndLevel2) {
        EarthAlertRule r = EarthAlertRule.create(tenant, type);
        r.configure(true, escalateAfter);
        when(rules.findByType(tenant, type)).thenReturn(Optional.of(r));
        List<EarthAlertRecipient> rs = new ArrayList<>();
        if (level1AndLevel2.length > 0) rs.add(EarthAlertRecipient.of(tenant, r.getId(), level1AndLevel2[0], 1));
        if (level1AndLevel2.length > 1) rs.add(EarthAlertRecipient.of(tenant, r.getId(), level1AndLevel2[1], 2));
        when(recipients.findForRule(tenant, r.getId())).thenReturn(rs);
        return r;
    }

    private EarthAlert raise(String type, String key, Collection<UUID> alsoTell) {
        return service.raise(tenant, type, "WARNING", UUID.randomUUID(), "ASSET", UUID.randomUUID(), "Title", "Message", key, alsoTell);
    }

    @SuppressWarnings("unchecked")
    private List<Recipient> sentTo(int times) {
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(times)).send(c.capture());
        return c.getValue().recipients();
    }

    @Test
    void withNoRuleTheCompanyAdministratorsAreTold_andTheSendIsRecorded() {
        EarthAlert a = raise("SERVICE_DUE", "K1", null);
        assertEquals("OPEN", a.getStatus());
        assertEquals(List.of(adminUser), sentTo(1).stream().map(Recipient::userId).toList());
        var d = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveries).saveAll(d.capture());
        var rec = (EarthAlertDelivery) d.getValue().iterator().next();
        assertEquals("SENT", rec.getStatus());
        assertTrue(rec.getDetail().contains("administrators"));
    }

    @Test
    void aConfiguredRuleTellsOnlyThoseChosen_notEveryone() {
        rule("SERVICE_DUE", 60, supervisor, manager);
        raise("SERVICE_DUE", "K2", null);
        assertEquals(List.of(supervisor), sentTo(1).stream().map(Recipient::userId).toList());
    }

    @Test
    void theAssigneeIsToldAsWellAsTheRuleRecipients() {
        rule("WORK_ORDER_OVERDUE", null, supervisor);
        UUID mechanic = UUID.randomUUID();
        raise("WORK_ORDER_OVERDUE", "K3", List.of(mechanic));
        assertEquals(Set.of(supervisor, mechanic), new HashSet<>(sentTo(1).stream().map(Recipient::userId).toList()));
    }

    @Test
    void theSameCauseIsNotRaisedTwice() {
        EarthAlert live = EarthAlert.create(tenant, "BREAKDOWN", "CRITICAL", null, null, null, "t", "m", "K4");
        when(alerts.findLiveByKey(tenant, "K4")).thenReturn(Optional.of(live));
        assertSame(live, raise("BREAKDOWN", "K4", null));
        verify(notifications, never()).send(any());
        verify(alerts, never()).save(any());
    }

    @Test
    void anEscalationTimeOfZeroTellsTheNextLevelAtOnce() {
        rule("BREAKDOWN", 0, supervisor, manager);
        EarthAlert a = raise("BREAKDOWN", "K5", null);
        assertEquals(2, a.getLevel());
        verify(notifications, times(2)).send(any());
    }

    @Test
    void ifNobodyChosenCanBeReachedTheAdministratorsAreToldAndTheFailureIsRecorded() {
        rule("SERVICE_DUE", null, supervisor);
        when(users.resolveUser(tenant, supervisor)).thenReturn(Optional.empty());
        raise("SERVICE_DUE", "K6", null);
        assertEquals(List.of(adminUser), sentTo(1).stream().map(Recipient::userId).toList());
        var d = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveries).saveAll(d.capture());
        List<String> statuses = new ArrayList<>();
        d.getValue().forEach(x -> statuses.add(((EarthAlertDelivery) x).getStatus()));
        assertTrue(statuses.contains("FAILED"));
        assertTrue(statuses.contains("SENT"));
    }

    @Test
    void aSendFailureIsRecordedAndDoesNotLoseTheAlert() {
        doThrow(new RuntimeException("smtp down")).when(notifications).send(any());
        EarthAlert a = raise("SERVICE_DUE", "K7", null);
        assertNotNull(a);
        var d = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveries).saveAll(d.capture());
        assertEquals("FAILED", ((EarthAlertDelivery) d.getValue().iterator().next()).getStatus());
    }

    @Test
    void anUnacknowledgedAlertEscalatesOnceItsTimeIsUp_andOnlyOnce() {
        rule("SERVICE_DUE", 30, supervisor, manager);
        EarthAlert waiting = EarthAlert.create(tenant, "SERVICE_DUE", "WARNING", null, null, null, "t", "m", "W");
        when(alerts.findEscalationCandidates(tenant)).thenReturn(List.of(waiting));
        Instant later = waiting.getCreatedAt().plusSeconds(31 * 60);
        assertEquals(1, service.escalateDue(tenant, later));
        assertEquals(2, waiting.getLevel());
        assertEquals(List.of(manager), sentTo(1).stream().map(Recipient::userId).toList());
        assertEquals(0, service.escalateDue(tenant, later));
        verify(notifications, times(1)).send(any());
    }

    @Test
    void anAcknowledgedAlertNeverEscalates() {
        rule("SERVICE_DUE", 30, supervisor, manager);
        EarthAlert a = EarthAlert.create(tenant, "SERVICE_DUE", "WARNING", null, null, null, "t", "m", "Ack");
        a.acknowledge(supervisor);
        when(alerts.findEscalationCandidates(tenant)).thenReturn(List.of(a));
        assertEquals(0, service.escalateDue(tenant, a.getCreatedAt().plusSeconds(24 * 3600)));
        verify(notifications, never()).send(any());
    }

    @Test
    void anAlertNotYetDueDoesNotEscalate() {
        rule("SERVICE_DUE", 30, supervisor, manager);
        EarthAlert a = EarthAlert.create(tenant, "SERVICE_DUE", "WARNING", null, null, null, "t", "m", "N");
        when(alerts.findEscalationCandidates(tenant)).thenReturn(List.of(a));
        assertEquals(0, service.escalateDue(tenant, a.getCreatedAt().plusSeconds(10 * 60)));
        assertEquals(1, a.getLevel());
        verify(notifications, never()).send(any());
    }

    @Test
    void acknowledgingNeedsWriteAndResolvingNeedsMaintainAndANote() {
        EarthAlert a = EarthAlert.create(tenant, "SERVICE_DUE", "WARNING", null, null, null, "t", "m", "A");
        when(alerts.findByTenantAndId(tenant, a.getId())).thenReturn(Optional.of(a));
        assertThrows(HandyFlowException.class, () -> service.acknowledge(tenant, a.getId(), actor));
        granted.add("EARTHMOVING_WRITE");
        service.acknowledge(tenant, a.getId(), actor);
        assertEquals("ACKNOWLEDGED", a.getStatus());
        assertThrows(HandyFlowException.class, () -> service.resolve(tenant, a.getId(), "Done and dusted", actor));
        granted.add("EARTHMOVING_MAINTAIN");
        assertThrows(IllegalArgumentException.class, () -> service.resolve(tenant, a.getId(), " ", actor));
        service.resolve(tenant, a.getId(), "Serviced on site", actor);
        assertEquals("RESOLVED", a.getStatus());
    }

    @Test
    void resolveByKeyClosesTheLiveAlertAndIsHarmlessWhenThereIsNone() {
        EarthAlert a = EarthAlert.create(tenant, "BREAKDOWN", "CRITICAL", null, null, null, "t", "m", "B");
        when(alerts.findLiveByKey(tenant, "B")).thenReturn(Optional.of(a));
        service.resolveByKey(tenant, "B", actor, "Released");
        assertEquals("RESOLVED", a.getStatus());
        assertDoesNotThrow(() -> service.resolveByKey(tenant, "none", actor, "x"));
    }

    @Test
    void rulesNeedAdminAndRefuseUnknownPeopleAndUnsafeBreakdownSettings() {
        assertThrows(HandyFlowException.class, () -> service.listRules(tenant));
        granted.add("EARTHMOVING_ADMIN");
        assertThrows(IllegalArgumentException.class, () -> service.saveRule(tenant, "BREAKDOWN",
                new SaveAlertRuleRequest(false, null, List.of(supervisor), List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.saveRule(tenant, "BREAKDOWN",
                new SaveAlertRuleRequest(true, 240, List.of(supervisor), List.of(manager))));
        UUID stranger = UUID.randomUUID();
        when(users.resolveUser(tenant, stranger)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.saveRule(tenant, "SERVICE_DUE",
                new SaveAlertRuleRequest(true, 30, List.of(stranger), List.of())));
        when(rules.save(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.saveRule(tenant, "service_due", new SaveAlertRuleRequest(true, 30, List.of(supervisor), List.of(manager)));
        assertEquals(List.of(supervisor), saved.initialUserIds());
        assertEquals(List.of(manager), saved.escalationUserIds());
        verify(recipients, times(2)).save(any());
        verify(trail).record(eq(tenant), eq("EARTHMOVING_ALERT_RULE_CHANGED"), any(), any(), any(), any());
    }
}
