package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalClientResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The portal resolves tenant and client from the caller's ACTIVE grants — never from TenantContext (a portal token has
 * no tenant) and never from a client-supplied id it has no grant for. Plain JUnit with hand-written fakes.
 */
class FmPortalDataServiceTest {

    private final UUID portalUser = UUID.randomUUID();
    private final TenantId tenantA = TenantId.generate();
    private final TenantId tenantB = TenantId.generate();
    private final FmClient clientA = FmClient.create(tenantA, "A", "Client A", null, null, null, null, "1 Main Rd");
    private final FmClient clientB = FmClient.create(tenantB, "B", "Client B", null, null, null, null, "2 Side St");

    private final List<FmPortalAccessGrant> grants = new ArrayList<>();
    private final List<FmInvoice> invoices = new ArrayList<>();
    private final List<FmCreditNote> notes = new ArrayList<>();
    private Collection<String> lastStatuses;

    private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return type.cast(Proxy.newProxyInstance(FmPortalDataServiceTest.class.getClassLoader(), new Class<?>[]{type}, h));
    }

    private final FmPortalAccessGrantRepository grantRepo = fake(FmPortalAccessGrantRepository.class, (p, m, a) -> {
        if ("findByPortalUserId".equals(m.getName()))
            return grants.stream().filter(g -> g.isActive() && a[0].equals(g.getPortalUserId())).toList();
        throw new UnsupportedOperationException(m.getName());
    });

    private final FmClientRepository clientRepo = fake(FmClientRepository.class, (p, m, a) -> {
        if (!"findActiveById".equals(m.getName())) throw new UnsupportedOperationException(m.getName());
        for (FmClient c : List.of(clientA, clientB)) if (c.getId().equals(a[1]) && c.getTenantId().equals(a[0])) return Optional.of(c);
        return Optional.empty();
    });

    private final FmInvoiceRepository invoiceRepo = fake(FmInvoiceRepository.class, (p, m, a) -> {
        switch (m.getName()) {
            case "findAllForClientList":
                return invoices.stream().filter(i -> i.getTenantId().equals(a[0]) && i.getClientId().equals(a[1])).toList();
            case "findActiveById":
                return invoices.stream().filter(i -> i.getTenantId().equals(a[0]) && i.getId().equals(a[1])).findFirst();
            default: throw new UnsupportedOperationException(m.getName());
        }
    });

    private final FmCreditNoteRepository creditNoteRepo = fake(FmCreditNoteRepository.class, (p, m, a) -> {
        switch (m.getName()) {
            case "findApprovedForClient":
                return notes.stream().filter(n -> n.getTenantId().equals(a[0]) && n.getClientId().equals(a[1])
                        && "APPROVED".equals(n.getStatus())).toList();
            case "findActiveById":
                return notes.stream().filter(n -> n.getTenantId().equals(a[0]) && n.getId().equals(a[1])).findFirst();
            default: throw new UnsupportedOperationException(m.getName());
        }
    });

    private final FmSiteRepository siteRepo = fake(FmSiteRepository.class, (p, m, a) -> { throw new UnsupportedOperationException(m.getName()); });

    private final FmWorkOrderRepository workOrderRepo = fake(FmWorkOrderRepository.class, (p, m, a) -> {
        if ("findAllForClientPortal".equals(m.getName())) { lastStatuses = (Collection<String>) a[2]; return null; }
        throw new UnsupportedOperationException(m.getName());
    });

    private final FmPortalDataService service =
            new FmPortalDataService(grantRepo, clientRepo, siteRepo, workOrderRepo, invoiceRepo, creditNoteRepo);

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmPortalAccessGrant activeGrant(TenantId tenant, UUID clientId) {
        FmPortalAccessGrant g = FmPortalAccessGrant.createInvite(tenant.getValue(), clientId, "p@example.com", UUID.randomUUID());
        g.acceptInvite(portalUser);
        grants.add(g);
        return g;
    }

    private FmInvoice invoice(FmClient c, String targetStatus) {
        LocalDate d = LocalDate.now();
        FmInvoice i = FmInvoice.create(c.getTenantId(), c.getId(), "INV-" + (invoices.size() + 1), d.minusMonths(1), d, d,
                d.plusDays(30), bd("1000.00"), bd("0.00"), bd("0.00"), UUID.randomUUID(), null);
        switch (targetStatus) {
            case "DRAFT": break;
            case "CANCELLED": i.cancelDraft("mistake"); break;
            case "APPROVED": i.approve(UUID.randomUUID()); break;
            case "SENT": i.approve(UUID.randomUUID()); i.markSent(); break;
            case "PARTIAL": i.approve(UUID.randomUUID()); i.markSent(); i.recordPayment(bd("100.00")); break;
            case "PAID": i.approve(UUID.randomUUID()); i.markSent(); i.recordPayment(bd("1000.00")); break;
            case "CREDITED": i.approve(UUID.randomUUID()); i.markSent(); i.applyCredit(bd("1000.00")); break;
            default: throw new IllegalArgumentException(targetStatus);
        }
        invoices.add(i);
        return i;
    }

    private FmCreditNote approvedNote(FmInvoice invoice) {
        FmCreditNote n = FmCreditNote.forSubtotal(invoice, () -> "CN-" + (notes.size() + 1), LocalDate.now(), bd("100.00"), "Adjustment", UUID.randomUUID());
        n.approve(UUID.randomUUID());
        notes.add(n);
        return n;
    }

    // ── scope ────────────────────────────────────────────────────────────────

    @Test
    void aPortalUserWithNoActiveGrantHasNoAccess() {
        TenantContext.clear();
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.resolveScope(portalUser, null));
        assertEquals("NO_CLIENT_ACCESS", e.getErrorCode());
    }

    @Test
    void aRevokedGrantGivesNoAccess() {
        FmPortalAccessGrant g = activeGrant(tenantA, clientA.getId());
        g.revoke(UUID.randomUUID());
        assertThrows(HandyFlowException.class, () -> service.resolveScope(portalUser, null));
    }

    @Test
    void theTenantAndClientComeFromTheGrantWithoutAnyTenantContext() {
        TenantContext.clear(); // a portal token carries no tenant
        activeGrant(tenantA, clientA.getId());
        FmPortalDataService.Scope scope = service.resolveScope(portalUser, null);
        assertEquals(tenantA, scope.tenantId());
        assertEquals(clientA.getId(), scope.clientId());
    }

    @Test
    void aReInvitedClientStillCountsAsOneClient() {
        activeGrant(tenantA, clientA.getId());
        activeGrant(tenantA, clientA.getId());
        assertEquals(clientA.getId(), service.resolveScope(portalUser, null).clientId());
        assertEquals(1, service.myClients(portalUser).size());
    }

    @Test
    void severalClientsRequireAnExplicitChoice() {
        activeGrant(tenantA, clientA.getId());
        activeGrant(tenantB, clientB.getId());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.resolveScope(portalUser, null));
        assertEquals("CLIENT_SELECTION_REQUIRED", e.getErrorCode());
        FmPortalDataService.Scope chosen = service.resolveScope(portalUser, clientB.getId());
        assertEquals(tenantB, chosen.tenantId());
        assertEquals(clientB.getId(), chosen.clientId());
    }

    @Test
    void aClientIdWithoutAGrantIsRefusedEvenWhenTheUserHasOneGrant() {
        activeGrant(tenantA, clientA.getId());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.resolveScope(portalUser, clientB.getId()));
        assertEquals("NO_CLIENT_ACCESS", e.getErrorCode());
    }

    @Test
    void myClientsListsTheNamesOfEveryClientTheUserCanActFor() {
        activeGrant(tenantA, clientA.getId());
        activeGrant(tenantB, clientB.getId());
        List<String> names = service.myClients(portalUser).stream().map(FmPortalClientResponse::name).sorted().toList();
        assertEquals(List.of("Client A", "Client B"), names);
    }

    // ── invoices & credit notes ──────────────────────────────────────────────

    @Test
    void onlyInvoicesThatWereActuallySentAreVisible() {
        activeGrant(tenantA, clientA.getId());
        for (String s : List.of("DRAFT", "CANCELLED", "APPROVED")) invoice(clientA, s);
        FmInvoice sent = invoice(clientA, "SENT");
        FmInvoice partial = invoice(clientA, "PARTIAL");
        FmInvoice paid = invoice(clientA, "PAID");
        FmInvoice credited = invoice(clientA, "CREDITED");
        List<UUID> visible = service.getMyInvoices(portalUser, null).stream().map(FmInvoice::getId).toList();
        assertEquals(Set.of(sent.getId(), partial.getId(), paid.getId(), credited.getId()), new HashSet<>(visible));
    }

    @Test
    void aFullyCreditedInvoiceStaysVisibleSoTheCreditNoteMakesSense() {
        activeGrant(tenantA, clientA.getId());
        FmInvoice credited = invoice(clientA, "CREDITED");
        assertEquals(1, service.getMyInvoices(portalUser, null).size());
        assertEquals(0, credited.balance().signum());
    }

    @Test
    void anotherClientsInvoicesAreNeverReturned() {
        activeGrant(tenantA, clientA.getId());
        invoice(clientB, "SENT");
        assertTrue(service.getMyInvoices(portalUser, null).isEmpty());
    }

    @Test
    void anInvoiceMustBeMineAndSentToDownload() {
        activeGrant(tenantA, clientA.getId());
        FmInvoice mine = invoice(clientA, "SENT");
        FmInvoice draft = invoice(clientA, "DRAFT");
        FmInvoice theirs = invoice(clientB, "SENT");
        assertEquals(tenantA, service.requireMyInvoice(portalUser, null, mine.getId()).tenantId());
        assertThrows(ResourceNotFoundException.class, () -> service.requireMyInvoice(portalUser, null, draft.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.requireMyInvoice(portalUser, null, theirs.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.requireMyInvoice(portalUser, null, UUID.randomUUID()));
    }

    @Test
    void onlyApprovedCreditNotesOnVisibleInvoicesAreListed() {
        activeGrant(tenantA, clientA.getId());
        FmInvoice sent = invoice(clientA, "SENT");
        FmInvoice unsentInvoice = invoice(clientA, "APPROVED"); // credits are allowed once approved, but the client has not been sent it
        FmCreditNote shown = approvedNote(sent);
        approvedNote(unsentInvoice);
        FmCreditNote draftNote = FmCreditNote.forSubtotal(sent, () -> "CN-X", LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        notes.add(draftNote);
        Map<FmCreditNote, String> listed = service.getMyCreditNotes(portalUser, null);
        assertEquals(1, listed.size());
        assertEquals(sent.getInvoiceNumber(), listed.get(shown));
    }

    @Test
    void aCreditNoteMustBeMineAndApprovedToDownload() {
        activeGrant(tenantA, clientA.getId());
        FmInvoice sent = invoice(clientA, "SENT");
        FmCreditNote mine = approvedNote(sent);
        FmCreditNote draft = FmCreditNote.forSubtotal(sent, () -> "CN-D", LocalDate.now(), bd("10.00"), "x", UUID.randomUUID());
        notes.add(draft);
        FmCreditNote theirs = approvedNote(invoice(clientB, "SENT"));
        assertEquals(tenantA, service.requireMyCreditNote(portalUser, null, mine.getId()).tenantId());
        assertThrows(ResourceNotFoundException.class, () -> service.requireMyCreditNote(portalUser, null, draft.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.requireMyCreditNote(portalUser, null, theirs.getId()));
    }

    // ── work orders ──────────────────────────────────────────────────────────

    @Test
    void theWorkOrderStatusFilterIsTranslatedToInternalStatuses() {
        activeGrant(tenantA, clientA.getId());
        service.getMyWorkOrders(portalUser, null, "IN_PROGRESS", null);
        assertEquals(List.of("IN_PROGRESS", "PENDING_VERIFICATION"), List.copyOf(lastStatuses));
        service.getMyWorkOrders(portalUser, null, "SUBMITTED", null);
        assertEquals(List.of("REQUESTED"), List.copyOf(lastStatuses));
        service.getMyWorkOrders(portalUser, null, "PENDING_VERIFICATION", null);
        assertEquals(List.of("NONE"), List.copyOf(lastStatuses));
    }
}
