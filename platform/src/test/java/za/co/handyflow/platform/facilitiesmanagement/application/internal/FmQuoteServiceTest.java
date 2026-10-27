package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.*;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmQuoteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmQuoteResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Staff side of quotes: validation, client scope, draft editing, sending (freeze), withdrawing, audit. */
class FmQuoteServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = FmTime.today();
    private final FmClient client = FmClient.create(tenant, "C1", "Acme", null, null, null, null, null);
    private final FmSite site = FmSite.create(tenant, client.getId(), "Head office", "OFFICE", Map.of(), null);
    private final FmSite otherSite = FmSite.create(tenant, client.getId(), "Depot", "OFFICE", Map.of(), null);
    private final FmAsset asset = FmAsset.create(tenant, otherSite.getId(), "P-1", "Pump", "PUMP", null, null, null, null, null, null, "LOW", null);

    private final Map<UUID, FmQuote> quotes = new LinkedHashMap<>();
    private final Map<UUID, List<FmQuoteLine>> lines = new HashMap<>();
    private final List<String> audit = new ArrayList<>();
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();

    private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return type.cast(Proxy.newProxyInstance(FmQuoteServiceTest.class.getClassLoader(), new Class<?>[]{type}, h));
    }

    private final FmQuoteRepository quoteRepo = fake(FmQuoteRepository.class, (p, m, a) -> switch (m.getName()) {
        case "save" -> { FmQuote q = (FmQuote) a[0]; quotes.put(q.getId(), q); yield q; }
        case "findByIdForTenant" -> Optional.ofNullable(quotes.get(a[1]));
        default -> throw new UnsupportedOperationException(m.getName());
    });
    @SuppressWarnings("unchecked")
    private final FmQuoteLineRepository lineRepo = fake(FmQuoteLineRepository.class, (p, m, a) -> switch (m.getName()) {
        case "saveAll" -> { for (FmQuoteLine l : (Iterable<FmQuoteLine>) a[0]) lines.computeIfAbsent(l.getQuoteId(), k -> new ArrayList<>()).add(l); yield a[0]; }
        case "deleteForQuote" -> { lines.remove(a[1]); yield null; }
        case "findForQuote" -> lines.getOrDefault(a[1], List.of());
        default -> throw new UnsupportedOperationException(m.getName());
    });
    private final FmClientRepository clientRepo = fake(FmClientRepository.class, (p, m, a) ->
            "findActiveById".equals(m.getName()) ? Optional.ofNullable(client.getId().equals(a[1]) ? client : null) : null);
    private final FmSiteRepository siteRepo = fake(FmSiteRepository.class, (p, m, a) -> "findActiveByIdForClient".equals(m.getName())
            ? Optional.ofNullable(site.getId().equals(a[2]) ? site : otherSite.getId().equals(a[2]) ? otherSite : null) : null);
    private final FmAssetRepository assetRepo = fake(FmAssetRepository.class, (p, m, a) ->
            "findActiveById".equals(m.getName()) ? Optional.ofNullable(asset.getId().equals(a[1]) ? asset : null) : null);
    private final FmWorkOrderRepository woRepo = fake(FmWorkOrderRepository.class, (p, m, a) -> Optional.empty());
    private final FmProfileRepository profileRepo = fake(FmProfileRepository.class, (p, m, a) -> Optional.empty());
    private final FmNumberGenerator numbers = new FmNumberGenerator(null, null) {
        @Override public String nextQuoteNumber(TenantId t) { return "FMQ-" + (quotes.size() + 1); }
    };
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
    };
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FmQuoteService service = new FmQuoteService(quoteRepo, lineRepo, clientRepo, siteRepo, assetRepo, woRepo, profileRepo, numbers, policy, auditService);

    { TenantContext.setUserId(UUID.randomUUID().toString()); }

    private FmQuoteRequest req(UUID siteId, UUID assetId, LocalDate validUntil) {
        return new FmQuoteRequest(client.getId(), siteId, assetId, null, "Replace pump", "desc", validUntil, "note",
                List.of(new FmQuoteRequest.Line("Pump", new BigDecimal("1"), new BigDecimal("250.00")), new FmQuoteRequest.Line("Labour", new BigDecimal("2"), new BigDecimal("100.00"))));
    }

    @Test
    void createsADraftWithTotalsAndAnAuditEntry() {
        FmQuoteResponse r = service.create(tenant, req(site.getId(), null, today.plusDays(14)));
        assertEquals("DRAFT", r.status());
        assertEquals(new BigDecimal("450.00"), r.total());
        assertEquals(2, r.lines().size());
        assertEquals(List.of("QUOTE_CREATED"), audit);
    }

    @Test
    void anAssetFromAnotherSiteAndBadValidityAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req(site.getId(), asset.getId(), today.plusDays(14))));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req(site.getId(), null, today.minusDays(1))));
        assertThrows(IllegalArgumentException.class, () -> service.create(tenant, req(site.getId(), null, today.plusDays(120))));
        assertTrue(quotes.isEmpty());
    }

    @Test
    void aClientOutsideTheCallersScopeIsRefused() {
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of());
        assertThrows(RuntimeException.class, () -> service.create(tenant, req(site.getId(), null, today.plusDays(14))));
        assertTrue(quotes.isEmpty());
    }

    @Test
    void outOfScopeQuotesReadAsNotFoundAndTechniciansSeeNoList() {
        FmQuoteResponse r = service.create(tenant, req(site.getId(), null, today.plusDays(14)));
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of());
        assertThrows(ResourceNotFoundException.class, () -> service.get(tenant, r.id()));
        scope = FmAccessScope.assignedJobs(List.of(site.getId()), List.of(UUID.randomUUID()));
        assertTrue(service.list(tenant, null, null, null).isEmpty());
    }

    @Test
    void updateReplacesTheLinesAndRecomputes() {
        FmQuoteResponse r = service.create(tenant, req(site.getId(), null, today.plusDays(14)));
        FmQuoteRequest changed = new FmQuoteRequest(client.getId(), site.getId(), null, null, "Replace pump (revised)", null, today.plusDays(20), null,
                List.of(new FmQuoteRequest.Line("Pump", BigDecimal.ONE, new BigDecimal("300.00"))));
        FmQuoteResponse u = service.update(tenant, r.id(), changed);
        assertEquals(new BigDecimal("300.00"), u.total());
        assertEquals(1, u.lines().size());
        assertEquals(1, lines.get(r.id()).size());
    }

    @Test
    void sendFreezesTheQuoteAndOnlyOnce() {
        FmQuoteResponse r = service.create(tenant, req(site.getId(), null, today.plusDays(14)));
        assertEquals("SENT", service.send(tenant, r.id()).status());
        assertEquals("QUOTE_NOT_DRAFT", assertThrows(HandyFlowException.class, () -> service.update(tenant, r.id(), req(site.getId(), null, today.plusDays(10)))).getErrorCode());
        assertEquals("QUOTE_NOT_DRAFT", assertThrows(HandyFlowException.class, () -> service.send(tenant, r.id())).getErrorCode());
        assertEquals(List.of("QUOTE_CREATED", "QUOTE_SENT"), audit);
    }

    @Test
    void withdrawNeedsASentQuoteAndAReason() {
        FmQuoteResponse r = service.create(tenant, req(site.getId(), null, today.plusDays(14)));
        assertThrows(HandyFlowException.class, () -> service.withdraw(tenant, r.id(), "oops"));
        service.send(tenant, r.id());
        assertThrows(IllegalArgumentException.class, () -> service.withdraw(tenant, r.id(), " "));
        assertEquals("WITHDRAWN", service.withdraw(tenant, r.id(), "Wrong scope").status());
        assertEquals("QUOTE_WITHDRAWN", audit.get(audit.size() - 1));
    }
}
