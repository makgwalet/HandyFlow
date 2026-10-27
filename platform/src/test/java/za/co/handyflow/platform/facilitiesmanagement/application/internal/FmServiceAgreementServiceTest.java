package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmBusinessCalendarRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmServiceAgreementRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSiteRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmServiceAgreementRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmServiceAgreementRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Agreement overlap rule, scope and audit trail — plain JUnit with hand-written fakes (no Mockito). */
class FmServiceAgreementServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final UUID siteA = UUID.randomUUID();
    private final UUID siteB = UUID.randomUUID();
    private final List<FmServiceAgreement> store = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();

    private final FmServiceAgreementRepository agreementRepo = (FmServiceAgreementRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmServiceAgreementRepository.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "save": if (!store.contains(a[0])) store.add((FmServiceAgreement) a[0]); return a[0];
                    case "findActiveById": return store.stream().filter(x -> x.getId().equals(a[1])).findFirst();
                    case "findActiveOverlapping": {
                        LocalDate from = (LocalDate) a[2], to = (LocalDate) a[3];
                        return store.stream().filter(x -> x.isActive() && x.getClientId().equals(a[1]) && !x.getId().equals(a[4])
                                && !x.getStartDate().isAfter(to)
                                && (x.getEndDate() == null || !x.getEndDate().isBefore(from))).toList();
                    }
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });

    private final FmClientRepository clientRepo = (FmClientRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmClientRepository.class}, (p, m, a) ->
                    "findActiveById".equals(m.getName())
                            ? Optional.of(FmClient.create(tenant, "C1", "Client", null, null, null, null, null))
                            : null);

    private final FmSiteRepository siteRepo = (FmSiteRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmSiteRepository.class}, (p, m, a) -> {
                if (!"findActiveByIdForClient".equals(m.getName())) throw new UnsupportedOperationException(m.getName());
                boolean known = a[2].equals(siteA) || a[2].equals(siteB);
                return known ? Optional.of(FmSite.create(tenant, client, "S", "OFFICE", Map.of(), null)) : Optional.empty();
            });

    private final FmBusinessCalendarRepository calendarRepo = (FmBusinessCalendarRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmBusinessCalendarRepository.class}, (p, m, a) -> Optional.empty());

    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
    };

    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };

    private final FmServiceAgreementService service = new FmServiceAgreementService(
            agreementRepo, clientRepo, siteRepo, policy, auditService,
            new FmContractTermsService(agreementRepo, calendarRepo));

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private CreateFmServiceAgreementRequest create(Set<UUID> sites, Set<String> cats, LocalDate start, LocalDate end) {
        return new CreateFmServiceAgreementRequest(client, "A", "TIME_AND_MATERIALS", null, new BigDecimal("500"),
                null, start, end, sites, cats, List.of());
    }

    @Test
    void createsAndAuditsAnAgreement() {
        service.createAgreement(tenant, create(Set.of(), Set.of(), START, null));
        assertEquals(1, store.size());
        assertEquals(List.of("AGREEMENT_CREATED"), audit);
    }

    @Test
    void twoAllSitesAllCategoriesAgreementsInOverlappingDatesAreRejected() {
        service.createAgreement(tenant, create(Set.of(), Set.of(), START, null));
        assertThrows(IllegalStateException.class,
                () -> service.createAgreement(tenant, create(Set.of(), Set.of(), LocalDate.of(2026, 6, 1), null)));
        assertEquals(1, store.size());
    }

    @Test
    void allSitesOverlapsASiteSpecificAgreementOfTheSameCategories() {
        service.createAgreement(tenant, create(Set.of(siteA), Set.of(), START, null));
        assertThrows(IllegalStateException.class,
                () -> service.createAgreement(tenant, create(Set.of(), Set.of(), START, null)));
    }

    @Test
    void differentSitesDoNotConflict() {
        service.createAgreement(tenant, create(Set.of(siteA), Set.of(), START, null));
        service.createAgreement(tenant, create(Set.of(siteB), Set.of(), START, null));
        assertEquals(2, store.size());
    }

    @Test
    void differentCategoriesDoNotConflict() {
        service.createAgreement(tenant, create(Set.of(), Set.of("PPM"), START, null));
        service.createAgreement(tenant, create(Set.of(), Set.of("CORRECTIVE"), START, null));
        assertEquals(2, store.size());
    }

    @Test
    void sharedCategoryOnTheSameSitesConflicts() {
        service.createAgreement(tenant, create(Set.of(siteA), Set.of("PPM", "CORRECTIVE"), START, null));
        assertThrows(IllegalStateException.class,
                () -> service.createAgreement(tenant, create(Set.of(siteA), Set.of("CORRECTIVE"), START, null)));
    }

    @Test
    void backToBackDatesDoNotConflict() {
        service.createAgreement(tenant, create(Set.of(), Set.of(), START, LocalDate.of(2026, 6, 30)));
        service.createAgreement(tenant, create(Set.of(), Set.of(), LocalDate.of(2026, 7, 1), null));
        assertEquals(2, store.size());
    }

    @Test
    void anEndedAgreementNoLongerBlocksANewOne() {
        service.createAgreement(tenant, create(Set.of(), Set.of(), START, null));
        service.end(tenant, store.get(0).getId());
        service.createAgreement(tenant, create(Set.of(), Set.of(), LocalDate.now().plusDays(1), null));
        assertEquals(2, store.size());
        assertTrue(audit.contains("AGREEMENT_ENDED"));
    }

    @Test
    void wideningCoverageOnUpdateIsRechecked() {
        service.createAgreement(tenant, create(Set.of(siteA), Set.of(), START, null));
        service.createAgreement(tenant, create(Set.of(siteB), Set.of(), START, null));
        UUID second = store.get(1).getId();
        assertThrows(IllegalStateException.class, () -> service.updateAgreement(tenant, second,
                new UpdateFmServiceAgreementRequest(null, null, null, null, null, Set.of(), null, null)));
    }

    @Test
    void anUnrelatedEditIsNotBlockedByAnOlderOverlap() {
        // two overlapping agreements that pre-date the rule
        store.add(FmServiceAgreement.create(tenant, client, "old1", "TIME_AND_MATERIALS", null, null, null, START, null, Set.of(), Set.of(), List.of()));
        store.add(FmServiceAgreement.create(tenant, client, "old2", "TIME_AND_MATERIALS", null, null, null, START, null, Set.of(), Set.of(), List.of()));
        service.updateAgreement(tenant, store.get(0).getId(),
                new UpdateFmServiceAgreementRequest(null, new BigDecimal("700"), null, null, null, null, null, null));
        assertEquals(0, new BigDecimal("700").compareTo(store.get(0).getHourlyRate()));
        assertTrue(audit.contains("AGREEMENT_UPDATED"));
    }

    @Test
    void aSiteFromAnotherClientIsRejected() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.createAgreement(tenant, create(Set.of(UUID.randomUUID()), Set.of(), START, null)));
        assertTrue(store.isEmpty());
    }

    @Test
    void aClientOutsideTheCallersScopeLooksLikeItDoesNotExist() {
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.createAgreement(tenant, create(Set.of(), Set.of(), START, null)));
    }

    @Test
    void anAgreementOfAnotherClientIsHiddenFromARestrictedCaller() {
        service.createAgreement(tenant, create(Set.of(), Set.of(), START, null));
        UUID id = store.get(0).getId();
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> service.getAgreement(tenant, id));
        assertThrows(ResourceNotFoundException.class, () -> service.end(tenant, id));
        assertTrue(store.get(0).isActive());
    }
}
