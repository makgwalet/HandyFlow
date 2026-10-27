package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmQuoteLine;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalQuoteResponse;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** The client's view of quotes: isolation, accepting (creates the work order), declining, expiry, audit and alerts. */
class FmPortalQuoteServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final UUID otherClient = UUID.randomUUID();
    private final UUID site = UUID.randomUUID();
    private final UUID portalUser = UUID.randomUUID();
    private final LocalDate today = FmTime.today();

    private final Map<UUID, FmQuote> quotes = new LinkedHashMap<>();
    private final List<FmWorkOrder> workOrders = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertsSent = new ArrayList<>();

    private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return type.cast(Proxy.newProxyInstance(FmPortalQuoteServiceTest.class.getClassLoader(), new Class<?>[]{type}, h));
    }

    private final FmPortalDataService data = new FmPortalDataService(null, null, null, null, null, null) {
        @Override public Scope resolveScope(UUID user, UUID selected) { return new Scope(tenant, client); }
    };
    private final FmQuoteRepository quoteRepo = fake(FmQuoteRepository.class, (p, m, a) -> switch (m.getName()) {
        case "findByIdForTenant" -> Optional.ofNullable(quotes.get(a[1]));
        case "findVisibleForClient" -> quotes.values().stream().filter(q -> q.getClientId().equals(a[1]) && !"DRAFT".equals(q.getStatus())).toList();
        case "save" -> { FmQuote q = (FmQuote) a[0]; quotes.put(q.getId(), q); yield q; }
        default -> throw new UnsupportedOperationException(m.getName());
    });
    private final FmQuoteLineRepository lineRepo = fake(FmQuoteLineRepository.class, (p, m, a) ->
            "findForQuote".equals(m.getName()) ? List.of(FmQuoteLine.create(tenant, (UUID) a[1], 1, "Pump", BigDecimal.ONE, new BigDecimal("100.00"))) : null);
    private final FmWorkOrderRepository woRepo = fake(FmWorkOrderRepository.class, (p, m, a) -> {
        if ("save".equals(m.getName())) { workOrders.add((FmWorkOrder) a[0]); return a[0]; }
        throw new UnsupportedOperationException(m.getName());
    });
    private final FmNumberGenerator numbers = new FmNumberGenerator(null, null) {
        @Override public String nextWorkOrderNumber(TenantId t) { return "WO-" + (workOrders.size() + 1); }
    };
    private final FmContractTermsService terms = new FmContractTermsService(
            fake(FmServiceAgreementRepository.class, (p, m, a) -> "findActiveAsOfDate".equals(m.getName()) ? List.of() : Optional.empty()),
            fake(FmBusinessCalendarRepository.class, (p, m, a) -> Optional.empty()));
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void recordPortal(TenantId t, UUID user, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) { alertsSent.add(type); }
    };
    private final FmPortalQuoteService service = new FmPortalQuoteService(data, quoteRepo, lineRepo, woRepo, numbers, terms, auditService, alerts);

    private FmQuote sent(UUID forClient, LocalDate validUntil, UUID workOrderId) {
        FmQuote q = FmQuote.create(tenant, "FMQ-" + (quotes.size() + 1), forClient, site, null, workOrderId, "Replace pump", null, validUntil, "internal", UUID.randomUUID());
        q.setAmounts(new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("0.00"), new BigDecimal("100.00"));
        q.send(UUID.randomUUID(), today);
        quotes.put(q.getId(), q);
        return q;
    }

    @Test
    void listShowsOnlySentQuotesForThisClient() {
        FmQuote mine = sent(client, today.plusDays(5), null);
        sent(otherClient, today.plusDays(5), null);
        FmQuote draft = FmQuote.create(tenant, "FMQ-D", client, site, null, null, "Draft", null, today.plusDays(5), null, UUID.randomUUID());
        quotes.put(draft.getId(), draft);
        List<FmPortalQuoteResponse> list = service.list(portalUser, null);
        assertEquals(1, list.size());
        assertEquals(mine.getId(), list.get(0).id());
        assertTrue(list.get(0).canRespond());
    }

    @Test
    void anotherClientsOrDraftQuoteReadsAsNotFound() {
        FmQuote theirs = sent(otherClient, today.plusDays(5), null);
        FmQuote draft = FmQuote.create(tenant, "FMQ-D", client, site, null, null, "Draft", null, today.plusDays(5), null, UUID.randomUUID());
        quotes.put(draft.getId(), draft);
        for (UUID id : List.of(theirs.getId(), draft.getId(), UUID.randomUUID())) {
            assertThrows(ResourceNotFoundException.class, () -> service.get(portalUser, null, id));
            assertThrows(ResourceNotFoundException.class, () -> service.accept(portalUser, null, id, null));
            assertThrows(ResourceNotFoundException.class, () -> service.decline(portalUser, null, id, "no"));
        }
        assertTrue(audit.isEmpty());
        assertEquals("SENT", theirs.getStatus());
    }

    @Test
    void acceptingCreatesAnOpenWorkOrderAndLinksIt() {
        FmQuote q = sent(client, today.plusDays(5), null);
        FmPortalQuoteResponse r = service.accept(portalUser, null, q.getId(), "Please start Monday");
        assertEquals("ACCEPTED", r.status());
        assertFalse(r.canRespond());
        assertEquals(1, workOrders.size());
        FmWorkOrder wo = workOrders.get(0);
        assertEquals("OPEN", wo.getStatus());
        assertEquals(client, wo.getClientId());
        assertEquals(site, wo.getSiteId());
        assertTrue(wo.getDescription().contains("FMQ-1"));
        assertEquals(wo.getId(), q.getWorkOrderId());
        assertEquals(List.of("QUOTE_ACCEPTED"), audit);
        assertEquals(List.of(NotificationType.FM_QUOTE_ACCEPTED), alertsSent);
    }

    @Test
    void acceptingAQuoteTiedToARequestLeavesThatWorkOrderAlone() {
        UUID existing = UUID.randomUUID();
        FmQuote q = sent(client, today.plusDays(5), existing);
        service.accept(portalUser, null, q.getId(), null);
        assertTrue(workOrders.isEmpty());
        assertEquals(existing, q.getWorkOrderId());
        assertEquals("ACCEPTED", q.getStatus());
    }

    @Test
    void anAnsweredQuoteCannotBeAnsweredAgainAndNothingIsDuplicated() {
        FmQuote q = sent(client, today.plusDays(5), null);
        service.accept(portalUser, null, q.getId(), null);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.accept(portalUser, null, q.getId(), null));
        assertEquals("QUOTE_NOT_OPEN", e.getErrorCode());
        assertThrows(HandyFlowException.class, () -> service.decline(portalUser, null, q.getId(), "changed my mind"));
        assertEquals(1, workOrders.size());
        assertEquals(1, audit.size());
    }

    @Test
    void anExpiredQuoteCannotBeAcceptedEvenBeforeTheSweep() {
        FmQuote q = sent(client, today, null);
        // Make it past due without waiting: the validity ended yesterday.
        FmQuote old = sent(client, today.plusDays(1), null);
        try {
            var f = FmQuote.class.getDeclaredField("validUntil"); f.setAccessible(true); f.set(old, today.minusDays(1));
        } catch (ReflectiveOperationException ex) { fail(ex.toString()); }
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.accept(portalUser, null, old.getId(), null));
        assertEquals("QUOTE_EXPIRED", e.getErrorCode());
        assertEquals("EXPIRED", service.get(portalUser, null, old.getId()).status());
        assertFalse(service.get(portalUser, null, old.getId()).canRespond());
        assertTrue(workOrders.isEmpty());
        assertEquals("SENT", service.get(portalUser, null, q.getId()).status());
    }

    @Test
    void decliningNeedsAReasonAndAlertsTheAdmins() {
        FmQuote q = sent(client, today.plusDays(5), null);
        assertThrows(IllegalArgumentException.class, () -> service.decline(portalUser, null, q.getId(), " "));
        FmPortalQuoteResponse r = service.decline(portalUser, null, q.getId(), "Found a cheaper supplier");
        assertEquals("DECLINED", r.status());
        assertEquals("Found a cheaper supplier", r.decisionNote());
        assertTrue(workOrders.isEmpty());
        assertEquals(List.of("QUOTE_DECLINED"), audit);
        assertEquals(List.of(NotificationType.FM_QUOTE_DECLINED), alertsSent);
    }
}
