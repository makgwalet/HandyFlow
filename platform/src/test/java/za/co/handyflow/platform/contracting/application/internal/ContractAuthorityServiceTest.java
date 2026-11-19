package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.contracting.application.internal.AuthorityRules.Context;
import za.co.handyflow.platform.contracting.domain.model.*;
import za.co.handyflow.platform.contracting.domain.repository.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractAuthorityServiceTest {
    @Mock ContractAuthorityGrantRepository grantRepo;
    @Mock ContractAuthorityEventRepository eventRepo;
    @Mock ContractAuthoritySettingsRepository settingsRepo;
    @Mock ContractRepository contractRepo;
    ContractAuthorityService service;

    TenantId tenant = TenantId.generate();
    UUID admin = UUID.randomUUID(), alice = UUID.randomUUID(), bob = UUID.randomUUID();
    final List<ContractAuthorityGrant> store = new ArrayList<>();
    LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

    @BeforeEach
    void setUp() {
        service = new ContractAuthorityService(grantRepo, eventRepo, settingsRepo, contractRepo);
        lenient().when(grantRepo.save(any())).thenAnswer(i -> { ContractAuthorityGrant g = i.getArgument(0); if (!store.contains(g)) store.add(g); return g; });
        lenient().when(grantRepo.findByTenant(tenant.getValue())).thenAnswer(i -> new ArrayList<>(store));
        lenient().when(grantRepo.findOne(eq(tenant.getValue()), any())).thenAnswer(i -> store.stream().filter(g -> g.getId().equals(i.getArgument(1))).findFirst());
        lenient().when(settingsRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void enforce(boolean on) {
        ContractAuthoritySettings s = ContractAuthoritySettings.create(tenant.getValue());
        s.set(on, admin);
        lenient().when(settingsRepo.findById(tenant.getValue())).thenReturn(Optional.of(s));
    }

    private ContractAuthorityService.GrantRequest request(UUID user, String role, Set<AuthorityAction> actions, boolean unlimited, String max, LocalDate to) {
        return new ContractAuthorityService.GrantRequest(user, role, null, Set.of(), actions, unlimited, max == null ? null : new BigDecimal(max), null, to, null);
    }

    private List<String> auditTypes() {
        ArgumentCaptor<ContractAuthorityEvent> c = ArgumentCaptor.forClass(ContractAuthorityEvent.class);
        verify(eventRepo, atLeast(0)).save(c.capture());
        return c.getAllValues().stream().map(ContractAuthorityEvent::getEventType).toList();
    }

    @Test
    void enforcementIsOffUntilAnAdministratorTurnsItOn() {
        when(settingsRepo.findById(tenant.getValue())).thenReturn(Optional.empty());
        assertFalse(service.enforced(tenant));
        assertDoesNotThrow(() -> service.assertAuthorised(tenant, alice, Set.of(), AuthorityAction.SIGN, new Context("NDA", BigDecimal.TEN, "ZAR")));
    }

    @Test
    void whenEnforcedAnActionWithoutAMatchingGrantIsRefusedWithTheReason() {
        enforce(true);
        var e = assertThrows(AccessDeniedException.class, () -> service.assertAuthorised(tenant, alice, Set.of(), AuthorityAction.SIGN, new Context("NDA", BigDecimal.TEN, "ZAR")));
        assertTrue(e.getMessage().contains("no delegated contract authority"));
    }

    @Test
    void whenEnforcedAGrantLetsTheHolderActWithinItsLimit() {
        enforce(true);
        service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE), false, "100000", null), admin);
        assertDoesNotThrow(() -> service.assertAuthorised(tenant, alice, Set.of(), AuthorityAction.APPROVE, new Context("NDA", new BigDecimal("99999"), "ZAR")));
        assertThrows(AccessDeniedException.class, () -> service.assertAuthorised(tenant, alice, Set.of(), AuthorityAction.APPROVE, new Context("NDA", new BigDecimal("100001"), "ZAR")));
    }

    @Test
    void changingEnforcementIsAudited() {
        enforce(false);
        service.setEnforced(tenant, true, admin);
        assertTrue(auditTypes().contains("ENFORCEMENT_ON"));
    }

    @Test
    void grantingNeedsExactlyOneGranteeAnActionAndALimitOrNoLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.grant(tenant, request(null, null, Set.of(AuthorityAction.SIGN), true, null, null), admin));
        assertThrows(IllegalArgumentException.class, () -> service.grant(tenant, request(alice, "CONTRACTS_MANAGE", Set.of(AuthorityAction.SIGN), true, null, null), admin));
        assertThrows(IllegalArgumentException.class, () -> service.grant(tenant, request(alice, null, Set.of(), true, null, null), admin));
        assertThrows(IllegalArgumentException.class, () -> service.grant(tenant, request(alice, null, Set.of(AuthorityAction.SIGN), false, null, null), admin), "a limit or an explicit no-limit is required");
        assertThrows(IllegalArgumentException.class, () -> service.grant(tenant, request(alice, null, Set.of(AuthorityAction.SIGN), true, null, today.minusDays(1)), admin));
        assertTrue(store.isEmpty());
    }

    @Test
    void everyGrantDelegationAndRevocationIsAudited() {
        var g = service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE, AuthorityAction.SIGN), false, "500000", null), admin);
        service.delegate(tenant, g.getId(), new ContractAuthorityService.DelegationRequest(bob, Set.of(AuthorityAction.APPROVE), Set.of(), false,
                new BigDecimal("100000"), today, today.plusDays(14), "while on leave"), alice, Set.of());
        service.revoke(tenant, g.getId(), "role changed", admin);
        assertEquals(List.of("GRANTED", "DELEGATED", "REVOKED"), auditTypes());
    }

    @Test
    void aDelegationGivesTheRecipientAuthorityOnlyForItsPeriodAndOnlyUpToTheSource() {
        enforce(true);
        var g = service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE), false, "500000", null), admin);
        service.delegate(tenant, g.getId(), new ContractAuthorityService.DelegationRequest(bob, Set.of(AuthorityAction.APPROVE), Set.of(), false,
                new BigDecimal("100000"), today, today.plusDays(14), null), alice, Set.of());
        assertDoesNotThrow(() -> service.assertAuthorised(tenant, bob, Set.of(), AuthorityAction.APPROVE, new Context("NDA", new BigDecimal("100000"), "ZAR")));
        assertThrows(AccessDeniedException.class, () -> service.assertAuthorised(tenant, bob, Set.of(), AuthorityAction.APPROVE, new Context("NDA", new BigDecimal("100001"), "ZAR")));
    }

    @Test
    void delegatingMoreThanYouHoldIsRefusedAndNothingIsSaved() {
        var g = service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE), false, "100000", null), admin);
        int before = store.size();
        var e = assertThrows(IllegalArgumentException.class, () -> service.delegate(tenant, g.getId(), new ContractAuthorityService.DelegationRequest(bob,
                Set.of(AuthorityAction.APPROVE), Set.of(), false, new BigDecimal("200000"), today, today.plusDays(7), null), alice, Set.of()));
        assertTrue(e.getMessage().contains("own limit"));
        assertEquals(before, store.size());
    }

    @Test
    void onlyTheHolderCanDelegateAndANonHolderIsRefused() {
        var g = service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE), true, null, null), admin);
        assertThrows(IllegalArgumentException.class, () -> service.delegate(tenant, g.getId(), new ContractAuthorityService.DelegationRequest(bob,
                Set.of(AuthorityAction.APPROVE), Set.of(), true, null, today, today.plusDays(7), null), bob, Set.of()));
    }

    @Test
    void revokingNeedsAReasonAndWorksOnce() {
        var g = service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE), true, null, null), admin);
        assertThrows(IllegalArgumentException.class, () -> service.revoke(tenant, g.getId(), " ", admin));
        service.revoke(tenant, g.getId(), "no longer needed", admin);
        assertThrows(IllegalStateException.class, () -> service.revoke(tenant, g.getId(), "again please", admin));
    }

    @Test
    void whatSomeoneHoldsTodayLeavesOutExpiredAndRevoked() {
        service.grant(tenant, request(alice, null, Set.of(AuthorityAction.APPROVE), true, null, null), admin);
        var old = service.grant(tenant, request(alice, null, Set.of(AuthorityAction.SIGN), true, null, null), admin);
        service.revoke(tenant, old.getId(), "replaced by another", admin);
        assertEquals(1, service.heldBy(tenant, alice, Set.of()).size());
    }
}
