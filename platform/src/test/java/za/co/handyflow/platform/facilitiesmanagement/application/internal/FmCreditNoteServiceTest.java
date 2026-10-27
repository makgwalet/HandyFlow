package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmCreditNoteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmCreditNoteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmCreditNoteResponse;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
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
 * Credit-note workflow: prepare → approve (different person) → ledger. Plain JUnit with hand-written fakes
 * (no Mockito) so the money rules can be exercised anywhere.
 */
class FmCreditNoteServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID client = UUID.randomUUID();
    private final UUID preparer = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    private final List<FmInvoice> invoices = new ArrayList<>();
    private final List<FmCreditNote> notes = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<NotificationType> alertsSent = new ArrayList<>();
    private final List<UUID> glPosted = new ArrayList<>();
    private int numbersIssued = 0;
    private boolean admin = false;
    private FmAccessScope scope = FmAccessScope.unrestrictedScope();

    private final FmCreditNoteRepository noteRepo = (FmCreditNoteRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmCreditNoteRepository.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "save": if (!notes.contains(a[0])) notes.add((FmCreditNote) a[0]); return a[0];
                    case "findActiveById": return notes.stream().filter(n -> n.getId().equals(a[1])).findFirst();
                    case "findForInvoice": return notes.stream().filter(n -> n.getInvoiceId().equals(a[1])).toList();
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });

    private final FmInvoiceRepository invoiceRepo = (FmInvoiceRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{FmInvoiceRepository.class}, (p, m, a) -> {
                switch (m.getName()) {
                    case "save": return a[0];
                    case "findActiveById": return invoices.stream().filter(i -> i.getId().equals(a[1])).findFirst();
                    default: throw new UnsupportedOperationException(m.getName());
                }
            });

    private final FmNumberGenerator numbers = new FmNumberGenerator(null, null) {
        @Override public String nextCreditNoteNumber(TenantId t) { return "FMCN-" + (++numbersIssued); }
    };
    private final FmGlPostingService gl = new FmGlPostingService(null, null, null, null, null, null, null) {
        @Override public void postCreditNoteAfterCommit(TenantId t, UUID id) { glPosted.add(id); }
    };
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return scope; }
        @Override public boolean hasAuthority(String a) { return admin && "FACILITIESMANAGEMENT_ADMIN".equals(a); }
    };
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FmAlerts alerts = new FmAlerts(null, null) {
        @Override public void toAdmins(TenantId t, NotificationType type, String title, String message, String url, String id) {
            alertsSent.add(type);
        }
    };

    private final FmCreditNoteService service = new FmCreditNoteService(noteRepo, invoiceRepo, numbers, gl, policy, auditService, alerts);

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmInvoice sentInvoice() {
        LocalDate issue = LocalDate.now();
        FmInvoice invoice = FmInvoice.create(tenant, client, "FMINV-0001", issue.minusMonths(1), issue, issue,
                issue.plusDays(30), bd("1000.00"), bd("150.00"), bd("15.00"), UUID.randomUUID(), null);
        invoice.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client Co", "4987654321", "1 Main Rd");
        invoice.approve(UUID.randomUUID());
        invoice.markSent();
        invoices.add(invoice);
        return invoice;
    }

    private FmCreditNoteResponse prepare(FmInvoice invoice, String subtotal) {
        TenantContext.setUserId(preparer.toString());
        return service.create(tenant, invoice.getId(), new CreateFmCreditNoteRequest(bd(subtotal), null, "Overbilled"));
    }

    private void actAs(UUID user) { TenantContext.setUserId(user.toString()); }

    @Test
    void preparingADraftAuditsAlertsAndLeavesTheInvoiceAlone() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse r = prepare(invoice, "200.00");
        assertEquals("DRAFT", r.status());
        assertEquals(0, bd("230.00").compareTo(r.total()));
        assertEquals(0, bd("1150.00").compareTo(invoice.balance()));
        assertEquals(List.of("CREDIT_NOTE_PREPARED"), audit);
        assertEquals(List.of(NotificationType.FM_CREDIT_NOTE_AWAITING_APPROVAL), alertsSent);
        assertTrue(glPosted.isEmpty());
    }

    @Test
    void eitherASubtotalOrFullBalanceIsRequiredButNotBoth() {
        FmInvoice invoice = sentInvoice();
        actAs(preparer);
        assertThrows(IllegalArgumentException.class,
                () -> service.create(tenant, invoice.getId(), new CreateFmCreditNoteRequest(null, null, "x")));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(tenant, invoice.getId(), new CreateFmCreditNoteRequest(bd("10.00"), true, "x")));
        assertTrue(notes.isEmpty());
        assertEquals(0, numbersIssued);
    }

    @Test
    void aRejectedCreditNoteNeverBurnsANumber() {
        FmInvoice invoice = sentInvoice();
        actAs(preparer);
        assertThrows(RuntimeException.class,
                () -> service.create(tenant, invoice.getId(), new CreateFmCreditNoteRequest(bd("5000.00"), null, "too much")));
        assertEquals(0, numbersIssued);
        assertTrue(notes.isEmpty());
    }

    @Test
    void approvalByAnotherPersonReducesTheBalanceAndQueuesTheLedgerPosting() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse prepared = prepare(invoice, "200.00");
        actAs(approver);
        FmCreditNoteResponse r = service.approve(tenant, prepared.id());
        assertEquals("APPROVED", r.status());
        assertEquals("PENDING", r.glStatus());
        assertEquals(0, bd("920.00").compareTo(invoice.balance()));
        assertEquals(0, bd("230.00").compareTo(invoice.getCreditedAmount()));
        assertEquals(List.of(prepared.id()), glPosted);
        assertEquals(List.of("CREDIT_NOTE_PREPARED", "CREDIT_NOTE_APPROVED"), audit);
    }

    @Test
    void creditingTheFullBalanceOfAnUnpaidInvoiceClosesIt() {
        FmInvoice invoice = sentInvoice();
        actAs(preparer);
        FmCreditNoteResponse prepared = service.create(tenant, invoice.getId(), new CreateFmCreditNoteRequest(null, true, "Cancelled contract"));
        assertEquals(0, bd("1150.00").compareTo(prepared.total()));
        actAs(approver);
        service.approve(tenant, prepared.id());
        assertEquals("CREDITED", invoice.getStatus());
        assertEquals(0, invoice.balance().signum());
    }

    @Test
    void thePreparerCannotApproveTheirOwnCreditNote() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse prepared = prepare(invoice, "200.00");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approve(tenant, prepared.id()));
        assertEquals("SEGREGATION_OF_DUTIES", e.getErrorCode());
        assertEquals(0, invoice.getCreditedAmount().signum());
        assertTrue(glPosted.isEmpty());
        assertEquals("DRAFT", notes.get(0).getStatus());
    }

    @Test
    void anAdministratorMayApproveTheirOwnButItIsAudited() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse prepared = prepare(invoice, "200.00");
        admin = true;
        service.approve(tenant, prepared.id());
        assertEquals("APPROVED", notes.get(0).getStatus());
        assertTrue(audit.contains("CREDIT_NOTE_SELF_APPROVED"));
        assertEquals(1, glPosted.size());
    }

    @Test
    void aNoteCannotBeApprovedTwiceNorPostedTwice() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse prepared = prepare(invoice, "200.00");
        actAs(approver);
        service.approve(tenant, prepared.id());
        assertThrows(RuntimeException.class, () -> service.approve(tenant, prepared.id()));
        assertEquals(1, glPosted.size());
        assertEquals(0, bd("230.00").compareTo(invoice.getCreditedAmount()));
    }

    @Test
    void aCreditCannotExceedWhatIsStillOwedAtApproval() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse first = prepare(invoice, "800.00"); // 920.00 incl VAT
        FmCreditNoteResponse second = prepare(invoice, "800.00"); // both fit at draft time
        actAs(approver);
        service.approve(tenant, first.id());
        assertThrows(RuntimeException.class, () -> service.approve(tenant, second.id()));
        assertEquals(0, bd("920.00").compareTo(invoice.getCreditedAmount()));
        assertEquals(1, glPosted.size());
    }

    @Test
    void cancellingADraftNeedsAReasonAndStopsItBeingApproved() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse prepared = prepare(invoice, "200.00");
        assertThrows(RuntimeException.class, () -> service.cancel(tenant, prepared.id(), " "));
        service.cancel(tenant, prepared.id(), "Raised in error");
        assertEquals("CANCELLED", notes.get(0).getStatus());
        actAs(approver);
        assertThrows(RuntimeException.class, () -> service.approve(tenant, prepared.id()));
        assertTrue(audit.contains("CREDIT_NOTE_CANCELLED"));
        assertEquals(0, invoice.getCreditedAmount().signum());
    }

    @Test
    void recordsOfAnotherClientAreHiddenFromARestrictedCaller() {
        FmInvoice invoice = sentInvoice();
        FmCreditNoteResponse prepared = prepare(invoice, "200.00");
        scope = FmAccessScope.restrictedToClients(List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        actAs(approver);
        assertThrows(ResourceNotFoundException.class, () -> service.get(tenant, prepared.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.approve(tenant, prepared.id()));
        assertThrows(ResourceNotFoundException.class, () -> service.forInvoice(tenant, invoice.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(tenant, invoice.getId(), new CreateFmCreditNoteRequest(bd("10.00"), null, "x")));
        assertThrows(ResourceNotFoundException.class, () -> service.requireAccessible(tenant, prepared.id()));
        assertEquals(0, invoice.getCreditedAmount().signum());
    }
}
