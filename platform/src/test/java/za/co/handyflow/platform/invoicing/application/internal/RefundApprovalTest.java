package za.co.handyflow.platform.invoicing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.invoicing.RefundIssuedEvent;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.ReceiptDtos.RefundRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Refunds are maker/checker: with a second authorised person available the refund waits (credit held back, nothing
 * posted); that person approves it or declines it; with nobody else able to approve, it is issued at once and the
 * exception is written to the audit trail.
 */
class RefundApprovalTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID receiptId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();
    private final UUID bank = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private SeparationOfDutiesChecker duties;
    private ApplicationEventPublisher events;
    private ReceiptService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        duties = mock(SeparationOfDutiesChecker.class);
        events = mock(ApplicationEventPublisher.class);
        TenantNumberingFacade numbering = mock(TenantNumberingFacade.class);
        lenient().when(numbering.next(any(), anyString(), anyString())).thenReturn("RF000001");
        service = spy(new ReceiptService(jdbc, mock(InvoiceRepository.class), numbering, events, duties));
        doReturn(null).when(service).get(any(), any());
    }

    /** The receipt row the service reads before refunding: R1000 received, R100 already used. */
    private void receiptExists() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1, UUID.class)).thenReturn(receiptId);
        when(rs.getString(2)).thenReturn("RC000001");
        when(rs.getObject(3, UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString(4)).thenReturn("PAYMENT");
        when(rs.getBigDecimal(5)).thenReturn(new BigDecimal("1000.00"));
        when(rs.getBigDecimal(6)).thenReturn(new BigDecimal("100.00"));
        when(rs.getDate(7)).thenReturn(java.sql.Date.valueOf(LocalDate.now()));
        when(rs.getObject(10, UUID.class)).thenReturn(bank);
        when(rs.getTimestamp(12)).thenReturn(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
        doAnswer(inv -> List.of(inv.<RowMapper<Object>>getArgument(1).mapRow(rs, 0)))
                .when(jdbc).query(contains("FROM customer_receipts WHERE id"), ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
    }

    /** A refund of R300 waiting for approval, asked for by {@link #maker}. */
    private void pendingRefundExists(String status) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1, UUID.class)).thenReturn(refundId);
        when(rs.getString(2)).thenReturn("RF000001");
        when(rs.getObject(3, UUID.class)).thenReturn(receiptId);
        when(rs.getString(4)).thenReturn("RC000001");
        when(rs.getObject(5, UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getBigDecimal(6)).thenReturn(new BigDecimal("300.00"));
        when(rs.getDate(7)).thenReturn(java.sql.Date.valueOf(LocalDate.now()));
        when(rs.getObject(8, UUID.class)).thenReturn(bank);
        when(rs.getString(9)).thenReturn("ref");
        when(rs.getObject(10, UUID.class)).thenReturn(maker);
        when(rs.getString(11)).thenReturn(status);
        doAnswer(inv -> List.of(inv.<RowMapper<Object[]>>getArgument(1).mapRow(rs, 0)))
                .when(jdbc).query(contains("FROM customer_refunds f JOIN customer_receipts"), ArgumentMatchers.<RowMapper<Object[]>>any(), any(Object[].class));
    }

    private static RefundRequest request() {
        return new RefundRequest(new BigDecimal("300.00"), LocalDate.now(), UUID.randomUUID(), "ref", "customer cancelled the order");
    }

    private String statusInsertedFor(UUID by) {
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO customer_refunds"), args.capture());
        Object[] all = args.getValue();
        return String.valueOf(all[all.length - 1]);
    }

    // ── Asking for a refund ───────────────────────────────────────────────────

    @Test
    void withAColleagueAvailableTheRefundWaitsAndNothingIsPosted() throws Exception {
        receiptExists();
        when(duties.otherHolders(tenant, "INVOICE_REFUND", maker)).thenReturn(2L);
        when(jdbc.update(contains("UPDATE customer_receipts SET allocated = allocated +"), any(Object[].class))).thenReturn(1);

        service.refund(tenant, receiptId, request(), maker);

        assertEquals("PENDING", statusInsertedFor(maker));
        verify(events, never()).publishEvent(any(RefundIssuedEvent.class));
        verify(duties, never()).recordSoleException(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aSoleUserIsIssuedTheRefundAtOnceAndTheExemptionIsRecorded() throws Exception {
        receiptExists();
        when(duties.otherHolders(tenant, "INVOICE_REFUND", maker)).thenReturn(0L);
        when(jdbc.update(contains("UPDATE customer_receipts SET allocated = allocated +"), any(Object[].class))).thenReturn(1);

        service.refund(tenant, receiptId, request(), maker);

        assertEquals("ISSUED", statusInsertedFor(maker));
        verify(duties).recordSoleException(eq(tenant), eq(maker), eq("INVOICE_REFUND"), eq("REFUND"), any(UUID.class), anyString());
        verify(events).publishEvent(any(RefundIssuedEvent.class));
    }

    // ── Deciding ──────────────────────────────────────────────────────────────

    @Test
    void theSamePersonCannotApproveTheirOwnRefundWhenSomeoneElseCould() throws Exception {
        pendingRefundExists("PENDING");
        doThrow(new HandyFlowException("same person", HttpStatus.CONFLICT, "SAME_PERSON"))
                .when(duties).require(eq(tenant), eq("INVOICE_REFUND"), eq(maker), eq(maker), anyString(), eq("REFUND"), eq(refundId), anyString());

        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approveRefund(tenant, refundId, maker));

        assertEquals("SAME_PERSON", e.getErrorCode());
        verify(events, never()).publishEvent(any(RefundIssuedEvent.class));
        verify(jdbc, never()).update(contains("SET status = 'ISSUED'"), any(Object[].class));
    }

    @Test
    void aColleagueApprovesAndTheRefundIsIssuedAndPosted() throws Exception {
        pendingRefundExists("PENDING");
        when(jdbc.update(contains("SET status = 'ISSUED'"), any(Object[].class))).thenReturn(1);

        service.approveRefund(tenant, refundId, checker);

        ArgumentCaptor<RefundIssuedEvent> event = ArgumentCaptor.forClass(RefundIssuedEvent.class);
        verify(events).publishEvent(event.capture());
        assertNotNull(event.getValue());
    }

    @Test
    void aRefundAlreadyDecidedCannotBeDecidedAgain() throws Exception {
        pendingRefundExists("ISSUED");

        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approveRefund(tenant, refundId, checker));

        assertEquals("REFUND_DECIDED", e.getErrorCode());
        verify(events, never()).publishEvent(any(RefundIssuedEvent.class));
    }

    @Test
    void decliningNeedsAReasonAndReleasesTheHeldCredit() throws Exception {
        pendingRefundExists("PENDING");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.rejectRefund(tenant, refundId, checker, "  "));
        assertEquals("REASON_REQUIRED", e.getErrorCode());
        verify(jdbc, never()).update(contains("SET status = 'REJECTED'"), any(Object[].class));

        when(jdbc.update(contains("SET status = 'REJECTED'"), any(Object[].class))).thenReturn(1);
        service.rejectRefund(tenant, refundId, checker, "wrong bank account");

        verify(jdbc).update(contains("SET allocated = allocated -"), any(Object[].class));
        verify(events, never()).publishEvent(any(RefundIssuedEvent.class));
    }
}
