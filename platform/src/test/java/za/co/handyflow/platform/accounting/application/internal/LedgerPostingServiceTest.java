package za.co.handyflow.platform.accounting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A posting is attempted once per business event key, a failure is queued (one open row per key, however many attempts),
 * and a later attempt with the same key can succeed and close the queued failure. Whether a key already has a journal is
 * decided by AccountingService.postOnce and the unique index uq_acc_journal_source_key.
 */
class LedgerPostingServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final String key = "invoicing:invoice-issued:" + UUID.randomUUID();

    AccountingService accounting;
    JdbcTemplate jdbc;
    LedgerPostingService service;
    CreateJournalEntryRequest request;

    @BeforeEach
    void setUp() {
        accounting = mock(AccountingService.class);
        jdbc = mock(JdbcTemplate.class);
        service = new LedgerPostingService(accounting, jdbc, mock(PlatformTransactionManager.class));
        request = new CreateJournalEntryRequest(LocalDate.now(), "Invoice issued: INV-1", "INV-1", "MANUAL", List.of(
                new CreateJournalEntryRequest.JournalLineRequest(UUID.randomUUID(), "AR", new BigDecimal("115.00"), null),
                new CreateJournalEntryRequest.JournalLineRequest(UUID.randomUUID(), "Revenue", null, new BigDecimal("115.00"))));
    }

    @Test
    void aSuccessfulPostingUsesTheKeyAndClosesAnyOpenFailureForIt() {
        when(accounting.postOnce(tenant, key, request)).thenReturn(mock(JournalEntryResponse.class));

        service.postOnceOrRecord(tenant, key, request);

        verify(accounting).postOnce(tenant, key, request);
        verify(jdbc).update(contains("SET status = 'RESOLVED'"), any(Object[].class));
        verify(jdbc, never()).update(contains("INSERT INTO acc_posting_failures"), any(Object[].class));
    }

    @Test
    void aFailedPostingIsQueuedOnceAndTheFailureReachesTheCaller() {
        IllegalStateException boom = new IllegalStateException("The books are locked up to 31 October");
        when(accounting.postOnce(tenant, key, request)).thenThrow(boom);

        assertThatThrownBy(() -> service.postOnceOrRecord(tenant, key, request)).isSameAs(boom);

        // one upsert keyed on (tenant, source key) where status is OPEN: a second failure adds an attempt, not a second row
        verify(jdbc).update(contains("ON CONFLICT (tenant_id, source_key) WHERE status = 'OPEN'"), any(Object[].class));
    }

    @Test
    void aFailedPostingRetriedWithTheSameKeySucceedsAndCloses() {
        when(accounting.postOnce(tenant, key, request))
                .thenThrow(new IllegalStateException("period locked"))
                .thenReturn(mock(JournalEntryResponse.class));

        assertThatThrownBy(() -> service.postOnceOrRecord(tenant, key, request)).isInstanceOf(IllegalStateException.class);
        service.postOnceOrRecord(tenant, key, request);

        verify(accounting, times(2)).postOnce(eq(tenant), eq(key), eq(request));
        verify(jdbc).update(contains("SET status = 'RESOLVED'"), any(Object[].class));
    }

    @Test
    void theQueueFailingTooDoesNotHideTheOriginalFailure() {
        IllegalStateException boom = new IllegalStateException("period locked");
        when(accounting.postOnce(tenant, key, request)).thenThrow(boom);
        when(jdbc.update(contains("INSERT INTO acc_posting_failures"), any(Object[].class))).thenThrow(new RuntimeException("database down"));

        assertThatThrownBy(() -> service.postOnceOrRecord(tenant, key, request)).isSameAs(boom);
    }

    @Test
    void anInvoiceKeyIsPerInvoiceSoTwoInvoicesNeverSuppressEachOther() {
        String other = "invoicing:invoice-issued:" + UUID.randomUUID();
        when(accounting.postOnce(any(), any(), any())).thenReturn(mock(JournalEntryResponse.class));

        service.postOnceOrRecord(tenant, key, request);
        service.postOnceOrRecord(tenant, other, request);

        assertThat(key).isNotEqualTo(other);
        verify(accounting).postOnce(tenant, key, request);
        verify(accounting).postOnce(tenant, other, request);
    }
}
