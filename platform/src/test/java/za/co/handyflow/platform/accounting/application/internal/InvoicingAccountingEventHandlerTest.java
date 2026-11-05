package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.accounting.domain.model.AccAccount;
import za.co.handyflow.platform.accounting.domain.repository.AccAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.invoicing.CreditNoteIssuedEvent;
import za.co.handyflow.platform.invoicing.InvoiceIssuedEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * What an issued invoice and a credit note post, and that each is keyed to its own document so the ledger posts it once
 * (the key is what AccountingService.postOnce and the unique index on acc_journal_entries use). INV-01 sent three more
 * ways of issuing an invoice through this handler; these tests pin what it does with them.
 */
class InvoicingAccountingEventHandlerTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID ar = UUID.randomUUID(), revenue = UUID.randomUUID(), vat = UUID.randomUUID();

    AccAccountRepository accounts;
    LedgerPostingService posting;
    InvoicingAccountingEventHandler handler;

    private static AccAccount account(UUID id, String code) {
        AccAccount a = mock(AccAccount.class);
        when(a.getId()).thenReturn(id);
        when(a.getAccountCode()).thenReturn(code);
        return a;
    }

    @BeforeEach
    void setUp() {
        accounts = mock(AccAccountRepository.class);
        posting = mock(LedgerPostingService.class);
        handler = new InvoicingAccountingEventHandler(accounts, mock(AccBankAccountRepository.class), posting);
        // Built first: creating a mock inside the argument of thenReturn(...) is an UnfinishedStubbingException.
        List<AccAccount> chart = List.of(account(ar, "1100"), account(revenue, "4000"), account(vat, "2100"));
        when(accounts.findAllActive(any())).thenReturn(chart);
    }

    private InvoiceIssuedEvent issued(UUID invoiceId, String subtotal, String vatTotal, String total) {
        return InvoiceIssuedEvent.of(tenant, invoiceId, "INV-1", UUID.randomUUID(), new BigDecimal(subtotal), new BigDecimal(vatTotal), new BigDecimal(total));
    }

    private CreateJournalEntryRequest postedFor(String key) {
        ArgumentCaptor<CreateJournalEntryRequest> req = ArgumentCaptor.forClass(CreateJournalEntryRequest.class);
        verify(posting).postOnceOrRecord(eq(tenant), eq(key), req.capture());
        return req.getValue();
    }

    private static BigDecimal debits(CreateJournalEntryRequest r) {
        return r.lines().stream().map(l -> l.debitAmount() == null ? BigDecimal.ZERO : l.debitAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal credits(CreateJournalEntryRequest r) {
        return r.lines().stream().map(l -> l.creditAmount() == null ? BigDecimal.ZERO : l.creditAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void anIssuedInvoiceDebitsReceivablesAndCreditsRevenueAndVatAndBalances() {
        UUID id = UUID.randomUUID();

        handler.onInvoiceIssued(issued(id, "1000.00", "150.00", "1150.00"));

        CreateJournalEntryRequest r = postedFor("invoicing:invoice-issued:" + id);
        assertThat(r.lines()).hasSize(3);
        assertThat(r.lines()).anySatisfy(l -> { assertThat(l.accountId()).isEqualTo(ar); assertThat(l.debitAmount()).isEqualByComparingTo("1150.00"); });
        assertThat(r.lines()).anySatisfy(l -> { assertThat(l.accountId()).isEqualTo(revenue); assertThat(l.creditAmount()).isEqualByComparingTo("1000.00"); });
        assertThat(r.lines()).anySatisfy(l -> { assertThat(l.accountId()).isEqualTo(vat); assertThat(l.creditAmount()).isEqualByComparingTo("150.00"); });
        assertThat(debits(r)).isEqualByComparingTo(credits(r));
    }

    @Test
    void anInvoiceWithoutVatPostsTwoLines() {
        UUID id = UUID.randomUUID();

        handler.onInvoiceIssued(issued(id, "500.00", "0.00", "500.00"));

        CreateJournalEntryRequest r = postedFor("invoicing:invoice-issued:" + id);
        assertThat(r.lines()).hasSize(2);
        assertThat(debits(r)).isEqualByComparingTo(credits(r));
    }

    @Test
    void theSameInvoiceAnnouncedTwiceCarriesTheSameKeySoTheLedgerPostsItOnce() {
        UUID id = UUID.randomUUID();

        handler.onInvoiceIssued(issued(id, "1000.00", "150.00", "1150.00"));
        handler.onInvoiceIssued(issued(id, "1000.00", "150.00", "1150.00"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(posting, times(2)).postOnceOrRecord(eq(tenant), keys.capture(), any());
        assertThat(keys.getAllValues()).containsOnly("invoicing:invoice-issued:" + id);
    }

    @Test
    void twoDifferentInvoicesNeverShareAKey() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();

        handler.onInvoiceIssued(issued(a, "100.00", "15.00", "115.00"));
        handler.onInvoiceIssued(issued(b, "100.00", "15.00", "115.00"));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(posting, times(2)).postOnceOrRecord(eq(tenant), keys.capture(), any());
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void aMissingReceivablesAccountIsQueuedForAPersonAndNothingIsPosted() {
        List<AccAccount> onlyRevenue = List.of(account(revenue, "4000"));   // built first: a mock created inside thenReturn(...) is an UnfinishedStubbingException
        when(accounts.findAllActive(any())).thenReturn(onlyRevenue);
        UUID id = UUID.randomUUID();

        handler.onInvoiceIssued(issued(id, "100.00", "15.00", "115.00"));

        verify(posting).recordFailure(eq(tenant), eq("invoicing:invoice-issued:" + id), anyString(), anyString());
        verify(posting, never()).postOnceOrRecord(any(), any(), any());
    }

    @Test
    void aPostingFailureNeverReachesTheInvoice() {
        when(posting.postOnceOrRecord(any(), any(), any())).thenThrow(new IllegalStateException("period is locked"));

        // does not throw: the invoice is already saved and the failure is queued by LedgerPostingService
        handler.onInvoiceIssued(issued(UUID.randomUUID(), "100.00", "15.00", "115.00"));

        verify(posting).postOnceOrRecord(any(), any(), any());
    }

    @Test
    void aCreditNoteReversesRevenueAndVatAgainstReceivablesUnderItsOwnKey() {
        UUID creditNoteId = UUID.randomUUID();

        handler.onCreditNoteIssued(CreditNoteIssuedEvent.of(tenant, creditNoteId, "CN-1", UUID.randomUUID(), "INV-1",
                new BigDecimal("200.00"), new BigDecimal("30.00"), new BigDecimal("230.00")));

        CreateJournalEntryRequest r = postedFor("invoicing:credit-note-issued:" + creditNoteId);
        assertThat(r.lines()).anySatisfy(l -> { assertThat(l.accountId()).isEqualTo(revenue); assertThat(l.debitAmount()).isEqualByComparingTo("200.00"); });
        assertThat(r.lines()).anySatisfy(l -> { assertThat(l.accountId()).isEqualTo(vat); assertThat(l.debitAmount()).isEqualByComparingTo("30.00"); });
        assertThat(r.lines()).anySatisfy(l -> { assertThat(l.accountId()).isEqualTo(ar); assertThat(l.creditAmount()).isEqualByComparingTo("230.00"); });
        assertThat(debits(r)).isEqualByComparingTo(credits(r));
    }

    @Test
    void aCreditNoteMirrorsTheInvoiceItCreditsAccountForAccount() {
        UUID invoiceId = UUID.randomUUID(), creditNoteId = UUID.randomUUID();
        handler.onInvoiceIssued(issued(invoiceId, "1000.00", "150.00", "1150.00"));
        handler.onCreditNoteIssued(CreditNoteIssuedEvent.of(tenant, creditNoteId, "CN-1", invoiceId, "INV-1",
                new BigDecimal("1000.00"), new BigDecimal("150.00"), new BigDecimal("1150.00")));

        CreateJournalEntryRequest invoice = postedFor("invoicing:invoice-issued:" + invoiceId);
        CreateJournalEntryRequest note = postedFor("invoicing:credit-note-issued:" + creditNoteId);

        // a full credit note leaves every account at zero: what the invoice debited the note credits, and the reverse
        for (UUID account : List.of(ar, revenue, vat)) {
            BigDecimal net = net(invoice, account).add(net(note, account));
            assertThat(net).as("net movement on " + account).isEqualByComparingTo("0");
        }
    }

    private static BigDecimal net(CreateJournalEntryRequest r, UUID account) {
        return r.lines().stream().filter(l -> account.equals(l.accountId()))
                .map(l -> (l.debitAmount() == null ? BigDecimal.ZERO : l.debitAmount()).subtract(l.creditAmount() == null ? BigDecimal.ZERO : l.creditAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
