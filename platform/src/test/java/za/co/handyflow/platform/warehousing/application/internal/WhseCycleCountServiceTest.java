package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseCycleCountServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID counter = UUID.randomUUID();
    private final UUID approver = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private final UUID line = UUID.randomUUID();
    private final UUID inv = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseInventoryService inventory;
    private SeparationOfDutiesChecker duties;
    private WhseCycleCountService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventory = mock(WhseInventoryService.class);
        duties = mock(SeparationOfDutiesChecker.class);
        service = new WhseCycleCountService(jdbc, inventory, duties);
    }

    private TenantId t() { return TenantId.of(tenant); }

    /** Stubs the single locked line row the service reads before every decision. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void line(String state, String sessionStatus, UUID countedBy, String counted, String expected) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("inventory_id", UUID.class)).thenReturn(inv);
        when(rs.getString("state")).thenReturn(state);
        when(rs.getObject("counted_by", UUID.class)).thenReturn(countedBy);
        when(rs.getBigDecimal("counted_qty")).thenReturn(counted == null ? null : new BigDecimal(counted));
        when(rs.getBigDecimal("expected_qty")).thenReturn(new BigDecimal(expected));
        when(rs.getTimestamp("expected_at")).thenReturn(new Timestamp(1_000));
        when(rs.getString("session_status")).thenReturn(sessionStatus);
        when(rs.getString("count_number")).thenReturn("CC-00001");
        when(jdbc.query(contains("FOR UPDATE OF l"), any(RowMapper.class), any(Object[].class))).thenAnswer(inv_ -> {
            RowMapper m = inv_.getArgument(1);
            return List.of(m.mapRow(rs, 0));
        });
    }

    private void moved(long n) {
        when(jdbc.queryForObject(contains("m.created_at > ?"), eq(Long.class), any(Object[].class))).thenReturn(n);
    }

    @Test @DisplayName("approving a difference posts a COUNT_VARIANCE keyed on the line and marks it applied")
    void approvePosts() throws Exception {
        line("PENDING_APPROVAL", "IN_REVIEW", counter, "8", "10");
        moved(0);
        when(jdbc.queryForObject(contains("state IN ('OPEN','COUNTED'"), eq(Long.class), any(Object[].class))).thenReturn(1L);

        assertNull(service.approve(t(), session, line, "ok", approver));

        verify(duties).require(eq(t()), eq("WAREHOUSING_COUNT_APPROVE"), eq(counter), eq(approver), anyString(), eq("WHSE_COUNT_LINE"), eq(line), anyString());
        verify(inventory).adjustForCount(eq(t()), eq(inv), eq(new BigDecimal("-2")), eq(session), eq("CC-00001"), contains("ok"), eq(approver), eq("count:" + line));
        verify(jdbc).update(contains("state = 'APPLIED'"), any(Object[].class));
    }

    @Test @DisplayName("the person who counted cannot approve: the checker's refusal stops the posting")
    void counterCannotApprove() throws Exception {
        line("PENDING_APPROVAL", "IN_REVIEW", counter, "8", "10");
        doThrow(new HandyFlowException("no", HttpStatus.FORBIDDEN, "SEPARATION_OF_DUTIES"))
                .when(duties).require(any(), anyString(), any(), any(), anyString(), anyString(), any(), anyString());

        assertThrows(HandyFlowException.class, () -> service.approve(t(), session, line, null, counter));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a position that moved after the count started sends the line back instead of posting a stale difference")
    void staleOnApprove() throws Exception {
        line("PENDING_APPROVAL", "IN_REVIEW", counter, "8", "10");
        moved(2);

        String msg = service.approve(t(), session, line, null, approver);

        assertNotNull(msg);
        verifyNoInteractions(inventory);
        verify(jdbc).update(contains("state = 'STALE'"), any(Object[].class));
    }

    @Test @DisplayName("only lines waiting for approval can be approved")
    void notPending() throws Exception {
        line("MATCHED", "IN_REVIEW", counter, "10", "10");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.approve(t(), session, line, null, approver));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a finished count cannot be decided")
    void finished() throws Exception {
        line("PENDING_APPROVAL", "CLOSED", counter, "8", "10");
        assertThrows(HandyFlowException.class, () -> service.approve(t(), session, line, null, approver));
    }

    @Test @DisplayName("rejecting needs a reason and sends the line back to be recounted")
    void reject() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.reject(t(), session, line, " no", approver));
        line("PENDING_APPROVAL", "IN_REVIEW", counter, "8", "10");
        service.reject(t(), session, line, "count looks wrong", approver);
        verify(jdbc).update(contains("state = 'RECOUNT'"), any(Object[].class));
        verify(jdbc).update(contains("status = 'COUNTING'"), any(Object[].class));
        verifyNoInteractions(inventory);
    }

    @Test @DisplayName("a negative count is refused; a count on a closed session is refused")
    void countGuards() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.recordCount(t(), session, line, new BigDecimal("-1"), counter));
        line("OPEN", "IN_REVIEW", null, null, "10");
        assertThrows(HandyFlowException.class, () -> service.recordCount(t(), session, line, BigDecimal.ONE, counter));
    }

    @Test @DisplayName("counting a stale line takes a fresh system quantity and resets the first count")
    void staleRecount() throws Exception {
        line("STALE", "COUNTING", null, null, "10");
        service.recordCount(t(), session, line, new BigDecimal("12"), counter);
        verify(jdbc).update(contains("expected_qty = (SELECT qty_on_hand"), any(Object[].class));
        verify(jdbc).update(contains("state = 'COUNTED'"), any(Object[].class));
    }

    @Test @DisplayName("a count cannot be closed while lines are unfinished")
    void closeBlocked() {
        when(jdbc.query(contains("FROM whse_count_sessions WHERE id"), any(RowMapper.class), any(Object[].class))).thenReturn(List.of("IN_REVIEW"));
        when(jdbc.queryForObject(contains("state IN ('OPEN'"), eq(Long.class), any(Object[].class))).thenReturn(3L);
        assertThrows(IllegalArgumentException.class, () -> service.close(t(), session, approver));
    }

    @Test @DisplayName("no user means no count step is recorded")
    void noActor() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submit(t(), session, null));
    }
}
