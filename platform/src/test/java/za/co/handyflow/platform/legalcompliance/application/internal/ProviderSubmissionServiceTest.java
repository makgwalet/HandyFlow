package za.co.handyflow.platform.legalcompliance.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderAssignmentRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderClientRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderSubmissionRepository;
import za.co.handyflow.platform.legalcompliance.dto.SubmissionDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Two providers, two clients. The point of these tests is who can and cannot see or touch what. */
class ProviderSubmissionServiceTest {

    // provider tenants
    private static final TenantId PA = TenantId.of(UUID.randomUUID()), PB = TenantId.of(UUID.randomUUID());
    // client tenants
    private static final TenantId CA = TenantId.of(UUID.randomUUID()), CB = TenantId.of(UUID.randomUUID());
    private static final UUID MGR_A = UUID.randomUUID(), MGR_B = UUID.randomUUID(), CONS_A = UUID.randomUUID(), OTHER_A = UUID.randomUUID(), CLIENT_ADMIN = UUID.randomUUID();
    private static final String BODY = "The supplier contract has no data-processing clause, which the Act requires.";

    private final List<ProviderClient> rels = new ArrayList<>();
    private final List<ProviderAssignment> team = new ArrayList<>();
    private final List<ProviderSubmission> store = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private long ref;
    private ProviderClient relA, relB;
    private ProviderSubmissionService provider;
    private ClientSubmissionService client;

    @SuppressWarnings("unchecked")
    private static <R> R proxy(Class<R> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (R) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, args) -> m.getName().equals("toString") ? type.getSimpleName() : handler.apply(m.getName(), args == null ? new Object[0] : args));
    }

    private static boolean sameTenant(ProviderClient c, Object t) { return c.getTenantId().getValue().equals(((TenantId) t).getValue()); }

    private ProviderClient relationship(TenantId providerTenant, TenantId clientTenant, String providerName, Set<AccessScope> asked, Set<AccessScope> granted) {
        ProviderClient c = ProviderClient.invite(providerTenant, "CLI-0000" + (rels.size() + 1), providerName, "Client", asked, null, null, ReviewCycle.QUARTERLY, null, "h" + rels.size(), Instant.now());
        c.accept(clientTenant.getValue(), granted, CLIENT_ADMIN, "Client Admin", Instant.now(), LocalDate.now());
        rels.add(c);
        return c;
    }

    @BeforeEach
    void setUp() {
        Set<AccessScope> all = EnumSet.allOf(AccessScope.class);
        relA = relationship(PA, CA, "Alpha Advisory", all, all);
        relB = relationship(PB, CB, "Beta Consulting", all, all);
        team.add(ProviderAssignment.assign(PA, relA.getId(), CONS_A, "Cora Consultant", AssignmentRole.SUPPORT, "Manager", LocalDate.now()));

        ProviderClientRepository clients = proxy(ProviderClientRepository.class, (n, a) -> switch (n) {
            case "findForTenant" -> rels.stream().filter(c -> sameTenant(c, a[0]) && c.getId().equals(a[1])).findFirst();
            case "findForClientTenantAcrossTenants" -> rels.stream().filter(c -> c.getClientTenantId() != null && c.getClientTenantId().equals(a[0]) && c.getId().equals(a[1])).findFirst();
            case "save" -> a[0];
            default -> null;
        });
        ProviderAssignmentRepository assignments = proxy(ProviderAssignmentRepository.class, (n, a) -> switch (n) {
            case "findActive" -> team.stream().filter(x -> sameTenant2(x, a[0]) && x.getProviderClientId().equals(a[1]) && x.getUserId().equals(a[2]) && x.active()).findFirst();
            default -> null;
        });
        ProviderSubmissionRepository subs = proxy(ProviderSubmissionRepository.class, (n, a) -> switch (n) {
            case "findForClient" -> store.stream().filter(s -> s.getTenantId().getValue().equals(((TenantId) a[0]).getValue()) && s.getId().equals(a[1])).findFirst();
            case "searchForClient" -> store.stream().filter(s -> s.getTenantId().getValue().equals(((TenantId) a[0]).getValue()) && (a[1] == null || s.getStatus() == a[1])).collect(Collectors.toList());
            case "countOpenForClient" -> store.stream().filter(s -> s.getTenantId().getValue().equals(((TenantId) a[0]).getValue()) && s.isOpen()).count();
            case "findForProvider" -> store.stream().filter(s -> s.getProviderTenantId().equals(a[0]) && s.getProviderClientId().equals(a[1]) && s.getId().equals(a[2])).findFirst();
            case "listForProvider" -> store.stream().filter(s -> s.getProviderTenantId().equals(a[0]) && s.getProviderClientId().equals(a[1])).collect(Collectors.toList());
            case "countUnansweredForProvider" -> store.stream().filter(s -> s.getProviderTenantId().equals(a[0]) && s.getProviderClientId().equals(a[1]) && s.getStatus() == SubmissionStatus.SUBMITTED).count();
            case "nextRef" -> ++ref;
            case "save" -> { if (!store.contains(a[0])) store.add((ProviderSubmission) a[0]); yield a[0]; }
            default -> null;
        });
        RegulatoryAuditService trail = new RegulatoryAuditService(null) {
            @Override public void record(UUID t, String e, UUID id, String ev, String d, UUID actor, String name) { audit.add(t + "|" + e + "|" + ev + "|" + d); }
        };
        provider = new ProviderSubmissionService(clients, assignments, subs, trail);
        client = new ClientSubmissionService(subs, clients, trail);
    }

    private static boolean sameTenant2(ProviderAssignment a, Object t) { return a.getTenantId().getValue().equals(((TenantId) t).getValue()); }

    private SubmitRequest req(String title) { return new SubmitRequest(SubmissionKind.OBSERVATION, SubmissionArea.CONTROLS, null, title, BODY, null); }

    private SubmissionView send(TenantId p, UUID rel, UUID who, boolean manager) { return provider.submit(p, rel, req("Missing DPA clause"), who, "User", manager); }

    private static HandyFlowException denied(org.junit.jupiter.api.function.Executable e) { return assertThrows(HandyFlowException.class, e); }

    @Test @DisplayName("A manager sends a submission: it lands in the client's tenant, and both trails record it without the body")
    void happyPath() {
        SubmissionView v = send(PA, relA.getId(), MGR_A, true);
        assertEquals(SubmissionStatus.SUBMITTED, v.status());
        assertEquals(SubmissionPriority.NORMAL, v.priority());
        assertEquals("PSB-00001", v.reference());
        assertEquals("Alpha Advisory", v.providerName());
        assertEquals(CA.getValue(), store.get(0).getTenantId().getValue());
        assertEquals(PA.getValue(), store.get(0).getProviderTenantId());
        assertEquals(2, audit.size());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith(CA.getValue() + "|PROVIDER_ACCESS|SUBMISSION_SENT|")));
        assertTrue(audit.stream().anyMatch(a -> a.startsWith(PA.getValue() + "|PROVIDER_CLIENT|SUBMISSION_SENT|")));
        assertTrue(audit.stream().noneMatch(a -> a.contains("data-processing")), "the text of a submission never goes into an audit trail");
    }

    @Test @DisplayName("ISOLATION: another provider cannot use, list or withdraw through someone else's relationship")
    void otherProviderCannotReach() {
        SubmissionView mine = send(PA, relA.getId(), MGR_A, true);
        assertThrows(ResourceNotFoundException.class, () -> provider.submit(PB, relA.getId(), req("Sneaky one here"), MGR_B, "B", true));
        assertThrows(ResourceNotFoundException.class, () -> provider.list(PB, relA.getId(), MGR_B, true));
        assertThrows(ResourceNotFoundException.class, () -> provider.withdraw(PB, relA.getId(), mine.id(), MGR_B, "B", true));
        assertEquals(1, store.size());
    }

    @Test @DisplayName("ISOLATION: through its own relationship a provider cannot withdraw or see another provider's submission")
    void ownRelationshipOtherRow() {
        SubmissionView a = send(PA, relA.getId(), MGR_A, true);
        SubmissionView b = send(PB, relB.getId(), MGR_B, true);
        assertThrows(ResourceNotFoundException.class, () -> provider.withdraw(PB, relB.getId(), a.id(), MGR_B, "B", true));
        assertThrows(ResourceNotFoundException.class, () -> provider.withdraw(PA, relA.getId(), b.id(), MGR_A, "A", true));
        assertEquals(List.of(a.id()), provider.list(PA, relA.getId(), MGR_A, true).stream().map(SubmissionView::id).toList());
        assertEquals(List.of(b.id()), provider.list(PB, relB.getId(), MGR_B, true).stream().map(SubmissionView::id).toList());
    }

    @Test @DisplayName("ISOLATION: a client sees only its own inbox, and cannot read or answer another client's submission")
    void clientIsolation() {
        SubmissionView a = send(PA, relA.getId(), MGR_A, true);
        SubmissionView b = send(PB, relB.getId(), MGR_B, true);
        assertEquals(List.of(a.id()), client.inbox(CA, null).items().stream().map(SubmissionView::id).toList());
        assertEquals(1L, client.inbox(CA, null).open());
        assertEquals(List.of(b.id()), client.inbox(CB, null).items().stream().map(SubmissionView::id).toList());
        assertThrows(ResourceNotFoundException.class, () -> client.get(CB, a.id()));
        assertThrows(ResourceNotFoundException.class, () -> client.accept(CB, a.id(), new RespondRequest(null), CLIENT_ADMIN, "X"));
        assertThrows(ResourceNotFoundException.class, () -> client.decline(CA, b.id(), new RespondRequest("Not our business, thanks"), CLIENT_ADMIN, "X"));
        assertEquals(SubmissionStatus.SUBMITTED, store.get(0).getStatus());
    }

    @Test @DisplayName("A consultant who is not assigned to the client is told it does not exist; an assigned one can submit")
    void assignment() {
        assertThrows(ResourceNotFoundException.class, () -> send(PA, relA.getId(), OTHER_A, false));
        assertThrows(ResourceNotFoundException.class, () -> provider.list(PA, relA.getId(), OTHER_A, false));
        assertEquals(SubmissionStatus.SUBMITTED, send(PA, relA.getId(), CONS_A, false).status());
        assertEquals(1, provider.list(PA, relA.getId(), CONS_A, false).size());
    }

    @Test @DisplayName("Without the SUBMIT grant the provider is refused, and asking for more does not grant it")
    void scopeNotGranted() {
        ProviderClient noSubmit = relationship(PA, CB, "Alpha Advisory", EnumSet.of(AccessScope.SUMMARY), EnumSet.of(AccessScope.SUMMARY));
        HandyFlowException e = denied(() -> send(PA, noSubmit.getId(), MGR_A, true));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertTrue(e.getMessage().contains("submit"));
        provider.requestScopes(PA, noSubmit.getId(), new ScopesRequest(EnumSet.of(AccessScope.SUBMIT)), MGR_A, "M");
        assertTrue(noSubmit.scopesRequested().contains(AccessScope.SUBMIT));
        assertFalse(noSubmit.scopesGranted().contains(AccessScope.SUBMIT));
        assertEquals(HttpStatus.FORBIDDEN, denied(() -> send(PA, noSubmit.getId(), MGR_A, true)).getStatus());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith(CB.getValue() + "|PROVIDER_ACCESS|ACCESS_REQUESTED|")));
    }

    @Test @DisplayName("Suspended or ended access blocks sending and reading")
    void suspendedAndEnded() {
        relA.suspend(AccessSide.CLIENT, "Under review for now");
        assertEquals(HttpStatus.FORBIDDEN, denied(() -> send(PA, relA.getId(), MGR_A, true)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, denied(() -> provider.list(PA, relA.getId(), MGR_A, true)).getStatus());
        relB.end(AccessSide.CLIENT, "Contract has finished", Instant.now());
        assertEquals(HttpStatus.FORBIDDEN, denied(() -> send(PB, relB.getId(), MGR_B, true)).getStatus());
        assertEquals(0, store.size());
    }

    @Test @DisplayName("Twenty unanswered submissions is the limit for one relationship; answering frees room")
    void openLimit() {
        for (int i = 0; i < SubmissionRules.OPEN_LIMIT; i++) send(PA, relA.getId(), MGR_A, true);
        assertEquals(HttpStatus.CONFLICT, denied(() -> send(PA, relA.getId(), MGR_A, true)).getStatus());
        send(PB, relB.getId(), MGR_B, true); // another relationship is unaffected
        client.decline(CA, store.get(0).getId(), new RespondRequest("Already dealt with last month"), CLIENT_ADMIN, "Admin");
        assertEquals(SubmissionStatus.SUBMITTED, send(PA, relA.getId(), MGR_A, true).status());
    }

    @Test @DisplayName("Withdraw works only before the client has answered")
    void withdraw() {
        SubmissionView s = send(PA, relA.getId(), MGR_A, true);
        assertEquals(SubmissionStatus.WITHDRAWN, provider.withdraw(PA, relA.getId(), s.id(), MGR_A, "M", true).status());
        assertEquals(HttpStatus.CONFLICT, denied(() -> provider.withdraw(PA, relA.getId(), s.id(), MGR_A, "M", true)).getStatus());
        SubmissionView t = send(PA, relA.getId(), MGR_A, true);
        client.accept(CA, t.id(), new RespondRequest(null), CLIENT_ADMIN, "Admin");
        assertEquals(HttpStatus.CONFLICT, denied(() -> provider.withdraw(PA, relA.getId(), t.id(), MGR_A, "M", true)).getStatus());
        assertEquals(1, client.inbox(CA, SubmissionStatus.ACCEPTED).items().size());
    }

    @Test @DisplayName("Client answers: accept then complete; decline and complete need a real reply; closed ones stay closed; both trails record it")
    void clientAnswers() {
        SubmissionView s = send(PA, relA.getId(), MGR_A, true);
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> client.decline(CA, s.id(), new RespondRequest("no"), CLIENT_ADMIN, "Admin")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> client.complete(CA, s.id(), new RespondRequest(null), CLIENT_ADMIN, "Admin")).getStatus());
        assertEquals(SubmissionStatus.ACCEPTED, client.accept(CA, s.id(), new RespondRequest(null), CLIENT_ADMIN, "Admin").status());
        assertEquals(HttpStatus.CONFLICT, denied(() -> client.accept(CA, s.id(), new RespondRequest(null), CLIENT_ADMIN, "Admin")).getStatus());
        SubmissionView done = client.complete(CA, s.id(), new RespondRequest("Clause added in the 2026 contract"), CLIENT_ADMIN, "Admin");
        assertEquals(SubmissionStatus.COMPLETED, done.status());
        assertEquals("Clause added in the 2026 contract", done.response());
        assertEquals("Admin", done.respondedBy());
        assertEquals(HttpStatus.CONFLICT, denied(() -> client.decline(CA, s.id(), new RespondRequest("Changed our mind about it"), CLIENT_ADMIN, "Admin")).getStatus());
        assertTrue(audit.stream().anyMatch(a -> a.startsWith(PA.getValue() + "|PROVIDER_CLIENT|SUBMISSION_COMPLETED|")));
        assertTrue(audit.stream().anyMatch(a -> a.startsWith(CA.getValue() + "|PROVIDER_ACCESS|SUBMISSION_ACCEPTED|")));
        assertTrue(audit.stream().noneMatch(a -> a.contains("Clause added")), "replies are not copied into the trail");
        assertEquals(0L, client.inbox(CA, null).open());
        assertEquals("Clause added in the 2026 contract", provider.list(PA, relA.getId(), MGR_A, true).get(0).response());
    }

    @Test @DisplayName("Client changes grants: only within what was asked, never empty, only its own relationship; turning SUBMIT off stops the provider")
    void grants() {
        assertThrows(ResourceNotFoundException.class, () -> client.changeGrants(CB, relA.getId(), new ScopesRequest(EnumSet.of(AccessScope.SUMMARY)), CLIENT_ADMIN, "Admin"));
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> client.changeGrants(CA, relA.getId(), new ScopesRequest(EnumSet.noneOf(AccessScope.class)), CLIENT_ADMIN, "Admin")).getStatus());
        ProviderClient narrow = relationship(PA, CB, "Alpha Advisory", EnumSet.of(AccessScope.SUMMARY, AccessScope.SUBMIT), EnumSet.of(AccessScope.SUMMARY));
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> client.changeGrants(CB, narrow.getId(), new ScopesRequest(EnumSet.of(AccessScope.REPORTS)), CLIENT_ADMIN, "Admin")).getStatus());
        client.changeGrants(CA, relA.getId(), new ScopesRequest(EnumSet.of(AccessScope.SUMMARY, AccessScope.REPORTS)), CLIENT_ADMIN, "Admin");
        assertEquals(HttpStatus.FORBIDDEN, denied(() -> send(PA, relA.getId(), MGR_A, true)).getStatus());
        assertTrue(audit.stream().anyMatch(a -> a.contains("submissions switched off")));
    }

    @Test @DisplayName("Validation: title, description and reply-by date")
    void validation() {
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> provider.submit(PA, relA.getId(), req("abc"), MGR_A, "M", true)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> provider.submit(PA, relA.getId(), new SubmitRequest(SubmissionKind.OBSERVATION, SubmissionArea.RISKS, null, "A fine title", "too short", null), MGR_A, "M", true)).getStatus());
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> provider.submit(PA, relA.getId(), new SubmitRequest(SubmissionKind.OBSERVATION, SubmissionArea.RISKS, null, "A fine title", BODY, today), MGR_A, "M", true)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, denied(() -> provider.submit(PA, relA.getId(), new SubmitRequest(SubmissionKind.OBSERVATION, SubmissionArea.RISKS, null, "A fine title", BODY, today.plusDays(181)), MGR_A, "M", true)).getStatus());
        assertEquals(SubmissionStatus.SUBMITTED, provider.submit(PA, relA.getId(), new SubmitRequest(SubmissionKind.INFORMATION_REQUEST, SubmissionArea.PRIVACY, SubmissionPriority.HIGH, "A fine title", BODY, today.plusDays(180)), MGR_A, "M", true).status());
        assertEquals(0 + 1, store.size());
    }

    @Test @DisplayName("Overdue is derived: past the reply-by date and still open")
    void overdue() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        SubmissionView v = provider.submit(PA, relA.getId(), new SubmitRequest(SubmissionKind.RECOMMENDATION, SubmissionArea.POLICIES, null, "Review the policy", BODY, today.plusDays(5)), MGR_A, "M", true);
        assertFalse(v.overdue());
        ProviderSubmission s = store.get(0);
        assertFalse(s.isOverdue(today.plusDays(5)));
        assertTrue(s.isOverdue(today.plusDays(6)));
        s.accept(null, "Admin", Instant.now());
        assertTrue(s.isOverdue(today.plusDays(6)), "accepted is still open");
        s.complete("All done and filed away", "Admin", Instant.now());
        assertFalse(s.isOverdue(today.plusDays(60)));
    }
}
