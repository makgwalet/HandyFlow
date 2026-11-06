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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseAdjustmentApprovalServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();
    private final UUID inventory = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private WhseInventoryService inventory_;
    private SeparationOfDutiesChecker duties;
    private WhseAdjustmentApprovalService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        inventory_ = mock(WhseInventoryService.class);
        duties = mock(SeparationOfDutiesChecker.class);
        service = new WhseAdjustmentApprovalService(jdbc, inventory_, duties);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void threshold(String value) {
        List rows = value == null ? List.of() : List.of(new BigDecimal(value));
        when(jdbc.query(contains("adjustment_approval_threshold_qty"), any(RowMapper.class), any(Object[].class))).thenReturn(rows);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pendingRequest(String delta, UUID by, String status) {
        List rows = List.of(new WhseAdjustmentApprovalService.Row(inventory, new BigDecimal(delta), "count correction", status, by));
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class))).thenReturn(rows);
    }

    @Test
    @DisplayName("only an adjustment larger than the threshold, in either direction, needs a second person")
    void thresholdRule() {
        BigDecimal ten = BigDecimal.TEN;
        assertFalse(WhseAdjustmentApprovalService.needsApproval(ten, new BigDecimal("10")));
        assertTrue(WhseAdjustmentApprovalService.needsApproval(ten, new BigDecimal("10.001")));
        assertTrue(WhseAdjustmentApprovalService.needsApproval(ten, new BigDecimal("-11")));
        assertFalse(WhseAdjustmentApprovalService.needsApproval(null, new BigDecimal("1000000")));
    }

    @Test
    @DisplayName("a direct adjustment over the threshold is refused with APPROVAL_REQUIRED")
    void directOverThresholdRefused() {
        threshold("10");
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.requireDirectAllowed(t(), new BigDecimal("-50")));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertDoesNotThrow(() -> service.requireDirectAllowed(t(), new BigDecimal("5")));
    }

    @Test
    @DisplayName("with no threshold set, any direct adjustment is allowed as before")
    void noThresholdNoRule() {
        threshold(null);
        assertDoesNotThrow(() -> service.requireDirectAllowed(t(), new BigDecimal("100000")));
    }

    @Test
    @DisplayName("a second person's approval posts the adjustment once, keyed by the request id")
    void approvalPostsAdjustment() {
        pendingRequest("-8", maker, "PENDING");

        service.approve(t(), requestId, "ok", checker);

        verify(inventory_).adjust(eq(t()), eq(inventory), eq(new BigDecimal("-8")), contains(requestId.toString()),
                eq(checker), eq("adjreq:" + requestId));
        verify(jdbc).update(contains("status = 'APPLIED'"), any(Object[].class));
    }

    @Test
    @DisplayName("the person who asked cannot approve while someone else can, and nothing is posted")
    void makerCannotApprove() {
        pendingRequest("-8", maker, "PENDING");
        doThrow(new HandyFlowException("same person", HttpStatus.CONFLICT, "SAME_PERSON"))
                .when(duties).require(any(), eq("WAREHOUSING_ADJUST_APPROVE"), eq(maker), eq(maker), anyString(), anyString(), any(), anyString());

        assertThrows(HandyFlowException.class, () -> service.approve(t(), requestId, null, maker));
        verifyNoInteractions(inventory_);
    }

    @Test
    @DisplayName("an already-decided request cannot be approved again")
    void decidedOnce() {
        pendingRequest("-8", maker, "APPLIED");
        assertThrows(HandyFlowException.class, () -> service.approve(t(), requestId, null, checker));
        verifyNoInteractions(inventory_);
    }

    @Test
    @DisplayName("rejecting needs a reason and only the requester can cancel")
    void rejectAndCancelRules() {
        pendingRequest("-8", maker, "PENDING");
        assertThrows(IllegalArgumentException.class, () -> service.reject(t(), requestId, " ", checker));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.cancel(t(), requestId, checker));
        verifyNoInteractions(inventory_);
    }

    @Test
    @DisplayName("requesting needs a non-zero amount and a reason")
    void requestValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.request(t(), inventory, BigDecimal.ZERO, "a good reason", maker));
        assertThrows(IllegalArgumentException.class, () -> service.request(t(), inventory, BigDecimal.ONE, "no", maker));
    }

    @Test
    @DisplayName("changing the threshold needs a reason")
    void thresholdChangeNeedsReason() {
        assertThrows(IllegalArgumentException.class, () -> service.setThreshold(t(), BigDecimal.TEN, "", maker));
    }
}
