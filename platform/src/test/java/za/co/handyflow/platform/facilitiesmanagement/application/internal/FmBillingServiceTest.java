package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoicePayment;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmProfile;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoiceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.RecordFmPaymentRequest;
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
 * Payment double-submit protection and invoice approval (party snapshot, South African tax-invoice rules,
 * preparer/approver separation). Plain JUnit with hand-written fakes — no Mockito.
 */
class FmBillingServiceTest {

    private final TenantId tenant = TenantId.generate();
    private final UUID preparer = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();

    private final List<FmInvoice> invoices = new ArrayList<>();
    private final List<FmInvoicePayment> payments = new ArrayList<>();
    private final List<String> audit = new ArrayList<>();
    private final List<UUID> invoicePosts = new ArrayList<>();
    private final List<UUID> paymentPosts = new ArrayList<>();
    private FmClient client = FmClient.create(tenant, "C1", "Client Co", null, null, null, null, "1 Main Rd, Pretoria");
    private FmProfile profile = null;
    private boolean admin = false;

    private static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return type.cast(Proxy.newProxyInstance(FmBillingServiceTest.class.getClassLoader(), new Class<?>[]{type}, h));
    }

    private final FmInvoiceRepository invoiceRepo = fake(FmInvoiceRepository.class, (p, m, a) -> {
        switch (m.getName()) {
            case "save": return a[0];
            case "findActiveById": return invoices.stream().filter(i -> i.getId().equals(a[1])).findFirst();
            default: throw new UnsupportedOperationException(m.getName());
        }
    });

    private final FmInvoicePaymentRepository paymentRepo = fake(FmInvoicePaymentRepository.class, (p, m, a) -> {
        switch (m.getName()) {
            case "save": payments.add((FmInvoicePayment) a[0]); return a[0];
            case "findByIdempotencyKey":
                return payments.stream().filter(x -> a[2].equals(x.getIdempotencyKey())).findFirst();
            case "existsByReference":
                return payments.stream().anyMatch(x -> x.getReference() != null && x.getReference().equalsIgnoreCase((String) a[2]));
            default: throw new UnsupportedOperationException(m.getName());
        }
    });

    private final FmClientRepository clientRepo = fake(FmClientRepository.class, (p, m, a) ->
            "findActiveById".equals(m.getName()) ? Optional.of(client) : null);

    private final FmProfileRepository profileRepo = fake(FmProfileRepository.class, (p, m, a) ->
            "findByTenant".equals(m.getName()) ? Optional.ofNullable(profile) : null);

    private final FmServiceAgreementRepository agreementRepo = fake(FmServiceAgreementRepository.class, (p, m, a) -> List.of());
    private final FmWorkOrderRepository workOrderRepo = fake(FmWorkOrderRepository.class, (p, m, a) -> List.of());

    private final FmNumberGenerator numbers = new FmNumberGenerator(null, null);
    private final FmGlPostingService gl = new FmGlPostingService(null, null, null, null, null, null, null) {
        @Override public void postInvoiceAfterCommit(TenantId t, UUID id) { invoicePosts.add(id); }
        @Override public void postPaymentAfterCommit(TenantId t, UUID id) { paymentPosts.add(id); }
    };
    private final FmAccessPolicy policy = new FmAccessPolicy(null, null, null, null) {
        @Override public FmAccessScope scope(TenantId t) { return FmAccessScope.unrestrictedScope(); }
        @Override public boolean hasAuthority(String a) { return admin && "FACILITIESMANAGEMENT_ADMIN".equals(a); }
    };
    private final FmAuditService auditService = new FmAuditService(null) {
        @Override public void record(TenantId t, String action, String type, UUID id, String details) { audit.add(action); }
    };
    private final FmAlerts alerts = new FmAlerts(null, null);

    private final FmBillingService service = new FmBillingService(invoiceRepo, paymentRepo, clientRepo, agreementRepo,
            workOrderRepo, profileRepo, numbers, gl, policy, auditService, alerts);

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    private FmInvoice invoice(String subtotal, String vat, String rate) {
        LocalDate issue = LocalDate.now();
        FmInvoice i = FmInvoice.create(tenant, client.getId(), "FMINV-" + (invoices.size() + 1), issue.minusMonths(1), issue,
                issue, issue.plusDays(30), bd(subtotal), bd(vat), bd(rate), preparer, null);
        invoices.add(i);
        return i;
    }

    private FmInvoice sentInvoice() {
        FmInvoice i = invoice("1000.00", "150.00", "15.00");
        i.snapshotParties("Acme FM", "4123456789", "9 Supplier St", "Client Co", null, "1 Main Rd");
        i.approve(approver);
        i.markSent();
        return i;
    }

    private FmProfile vatRegisteredProfile(String address) {
        FmProfile p = FmProfile.create(tenant, "Acme FM", null, null, null, null);
        p.updateBillingSettings(true, "4123456789", bd("15.00"), null, null, null, "VAT-OUT", null);
        if (address != null) p.changeAddress(address);
        return p;
    }

    private RecordFmPaymentRequest pay(String amount, String reference, String key) {
        return new RecordFmPaymentRequest(bd(amount), LocalDate.now(), "EFT", reference, key);
    }

    private void actAs(UUID user) { TenantContext.setUserId(user.toString()); }

    // ── payments ─────────────────────────────────────────────────────────────

    @Test
    void aPaymentReducesTheBalanceAuditsAndQueuesTheLedgerPosting() {
        FmInvoice invoice = sentInvoice();
        actAs(approver);
        FmInvoiceResponse r = service.recordPayment(tenant, invoice.getId(), pay("400.00", "BANK-1", null));
        assertEquals("PARTIAL", r.status());
        assertEquals(0, bd("750.00").compareTo(invoice.balance()));
        assertEquals(1, payments.size());
        assertEquals(1, paymentPosts.size());
        assertEquals(List.of("PAYMENT_RECORDED"), audit);
    }

    @Test
    void retryingWithTheSameIdempotencyKeyRecordsNothingTheSecondTime() {
        FmInvoice invoice = sentInvoice();
        actAs(approver);
        service.recordPayment(tenant, invoice.getId(), pay("400.00", "BANK-1", "key-1"));
        // The client times out and the user clicks again — reference may even have been retyped.
        FmInvoiceResponse again = service.recordPayment(tenant, invoice.getId(), pay("400.00", "BANK-1", "key-1"));
        assertEquals(1, payments.size());
        assertEquals(1, paymentPosts.size());
        assertEquals(0, bd("750.00").compareTo(invoice.balance()));
        assertEquals(0, bd("750.00").compareTo(again.balance()));
        assertEquals(1, audit.size());
    }

    @Test
    void theKeyIsTrimmedSoWhitespaceCannotDefeatIt() {
        FmInvoice invoice = sentInvoice();
        actAs(approver);
        service.recordPayment(tenant, invoice.getId(), pay("100.00", null, "k"));
        service.recordPayment(tenant, invoice.getId(), pay("100.00", null, "  k  "));
        assertEquals(1, payments.size());
    }

    @Test
    void theSameBankReferenceTwiceIsRejectedAsADuplicate() {
        FmInvoice invoice = sentInvoice();
        actAs(approver);
        service.recordPayment(tenant, invoice.getId(), pay("400.00", "BANK-1", null));
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service.recordPayment(tenant, invoice.getId(), pay("400.00", "bank-1", null)));
        assertEquals("DUPLICATE_PAYMENT", e.getErrorCode());
        assertEquals(1, payments.size());
        assertEquals(0, bd("750.00").compareTo(invoice.balance()));
    }

    @Test
    void twoGenuinelySeparatePaymentsBothApply() {
        FmInvoice invoice = sentInvoice();
        actAs(approver);
        service.recordPayment(tenant, invoice.getId(), pay("400.00", "BANK-1", "a"));
        service.recordPayment(tenant, invoice.getId(), pay("350.00", "BANK-2", "b"));
        assertEquals(2, payments.size());
        assertEquals(0, bd("400.00").compareTo(invoice.balance()));
    }

    @Test
    void aPaymentAgainstAnUnknownInvoiceIsNotFound() {
        actAs(approver);
        assertThrows(ResourceNotFoundException.class,
                () -> service.recordPayment(tenant, UUID.randomUUID(), pay("1.00", null, null)));
    }

    // ── approval ─────────────────────────────────────────────────────────────

    @Test
    void approvalSnapshotsTheSupplierAndClientDetailsAndQueuesTheLedgerPosting() {
        profile = vatRegisteredProfile("9 Supplier St, Cape Town");
        client.changeVatNumber("4987654321");
        FmInvoice draft = invoice("1000.00", "150.00", "15.00");
        actAs(approver);
        FmInvoiceResponse r = service.approveInvoice(tenant, draft.getId());
        assertEquals("APPROVED", r.status());
        assertTrue(r.taxInvoice());
        assertEquals("Acme FM", r.supplierName());
        assertEquals("4123456789", r.supplierVatNumber());
        assertEquals("9 Supplier St, Cape Town", r.supplierAddress());
        assertEquals("Client Co", r.clientName());
        assertEquals("4987654321", r.clientVatNumber());
        assertEquals(List.of(draft.getId()), invoicePosts);
    }

    @Test
    void aTaxInvoiceAboveR5000cannotBeIssuedToAClientWithoutAnAddress() {
        profile = vatRegisteredProfile("9 Supplier St");
        client = FmClient.create(tenant, "C2", "No Address Ltd", null, null, null, null, null);
        FmInvoice draft = invoice("10000.00", "1500.00", "15.00");
        actAs(approver);
        assertThrows(IllegalStateException.class, () -> service.approveInvoice(tenant, draft.getId()));
        assertEquals("DRAFT", draft.getStatus());
        assertTrue(invoicePosts.isEmpty());
    }

    @Test
    void aVatRegisteredSupplierWithoutAnAddressCannotIssueATaxInvoice() {
        profile = vatRegisteredProfile(null);
        FmInvoice draft = invoice("100.00", "15.00", "15.00");
        actAs(approver);
        assertThrows(IllegalStateException.class, () -> service.approveInvoice(tenant, draft.getId()));
        assertEquals("DRAFT", draft.getStatus());
    }

    @Test
    void aSupplierNotRegisteredForVatIssuesAPlainInvoiceWithoutAnAddressCheck() {
        profile = null;
        client = FmClient.create(tenant, "C3", "Small Client", null, null, null, null, null);
        FmInvoice draft = invoice("10000.00", "0.00", "0.00");
        actAs(approver);
        FmInvoiceResponse r = service.approveInvoice(tenant, draft.getId());
        assertFalse(r.taxInvoice());
        assertEquals("APPROVED", r.status());
    }

    @Test
    void thePreparerCannotApproveTheirOwnInvoice() {
        profile = vatRegisteredProfile("9 Supplier St");
        FmInvoice draft = invoice("1000.00", "150.00", "15.00");
        actAs(preparer);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approveInvoice(tenant, draft.getId()));
        assertEquals("SEGREGATION_OF_DUTIES", e.getErrorCode());
        assertEquals("DRAFT", draft.getStatus());
        assertTrue(invoicePosts.isEmpty());
    }

    @Test
    void anAdministratorMayApproveTheirOwnInvoiceButItIsAudited() {
        profile = vatRegisteredProfile("9 Supplier St");
        FmInvoice draft = invoice("1000.00", "150.00", "15.00");
        admin = true;
        actAs(preparer);
        service.approveInvoice(tenant, draft.getId());
        assertEquals("APPROVED", draft.getStatus());
        assertTrue(audit.contains("INVOICE_SELF_APPROVED"));
    }
}
