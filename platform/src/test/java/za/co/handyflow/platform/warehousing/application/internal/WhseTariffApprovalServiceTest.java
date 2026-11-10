package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WhseTariffApprovalServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();
    private final UUID client = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final LocalDate start = LocalDate.now().plusDays(10);

    private JdbcTemplate jdbc;
    private WhseTariffService tariffs;
    private SeparationOfDutiesChecker duties;
    private WhseTariffApprovalService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        tariffs = mock(WhseTariffService.class);
        duties = mock(SeparationOfDutiesChecker.class);
        service = new WhseTariffApprovalService(jdbc, tariffs, duties);
    }

    private TenantId t() { return TenantId.of(tenant); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rule(Boolean on) {
        List rows = on == null ? List.of() : List.of(on);
        when(jdbc.query(contains("tariff_approval_required"), any(RowMapper.class), any(Object[].class))).thenReturn(rows);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void locked(String kind, UUID by, String status) {
        boolean tariff = "TARIFF".equals(kind);
        List rows = List.of(new WhseTariffApprovalService.Row(kind, client, null, tariff ? new BigDecimal("12.5") : null,
                tariff ? null : new BigDecimal("900"), null, null, start, "note", status, by));
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), any(Object[].class))).thenReturn(rows);
    }

    @Test
    @DisplayName("the rule is off by default and when there is no profile")
    void offByDefault() {
        rule(false);
        assertFalse(service.required(t()));
        assertDoesNotThrow(() -> service.requireDirectAllowed(t()));
        rule(null);
        assertFalse(service.required(t()));
    }

    @Test
    @DisplayName("with the rule on, a direct change is refused with APPROVAL_REQUIRED")
    void directRefusedWhenOn() {
        rule(true);
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> service.requireDirectAllowed(t()));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    @DisplayName("a request is checked as the change itself would be, then stored as pending")
    void requestValidatesAndStores() {
        UUID id = service.requestTariff(t(), client, null, new BigDecimal("12.5"), start, " new rate ", maker);
        assertNotNull(id);
        verify(tariffs).precheckTariff(t(), client, null, new BigDecimal("12.5"), start);
        verify(jdbc).update(contains("INSERT INTO whse_tariff_requests"), any(Object[].class));
        service.requestTerms(t(), client, new BigDecimal("900"), 14, new BigDecimal("25"), start, null, maker);
        verify(tariffs).precheckTerms(t(), client, new BigDecimal("900"), 14, new BigDecimal("25"), start);
    }

    @Test
    @DisplayName("a request that would be refused (e.g. back-dated) is never stored")
    void badRequestNotStored() {
        doThrow(new IllegalArgumentException("A tariff or term cannot start in the past.")).when(tariffs).precheckTariff(any(), any(), any(), any(), any());
        assertThrows(IllegalArgumentException.class, () -> service.requestTariff(t(), client, null, BigDecimal.ONE, LocalDate.now().minusDays(1), null, maker));
        verify(jdbc, never()).update(contains("INSERT INTO whse_tariff_requests"), any(Object[].class));
    }

    @Test
    @DisplayName("approving a tariff request applies it credited to the requester, with the approver recorded")
    void approveAppliesTariff() {
        locked("TARIFF", maker, "PENDING");
        UUID created = UUID.randomUUID();
        when(tariffs.addTariff(t(), client, null, new BigDecimal("12.5"), start, "note", maker, checker)).thenReturn(created);
        service.approve(t(), requestId, "ok", checker);
        verify(duties).require(eq(t()), eq(WhseTariffApprovalService.PERMISSION), eq(maker), eq(checker), anyString(), eq("WHSE_TARIFF_REQUEST"), eq(requestId), anyString());
        verify(tariffs).addTariff(t(), client, null, new BigDecimal("12.5"), start, "note", maker, checker);
        verify(jdbc).update(contains("status = 'APPLIED'"), eq(checker), eq("ok"), eq(false), eq(created), eq(requestId));
    }

    @Test
    @DisplayName("approving a minimum-charge request applies the terms")
    void approveAppliesTerms() {
        locked("TERMS", maker, "PENDING");
        service.approve(t(), requestId, null, checker);
        verify(tariffs).addTerms(t(), client, new BigDecimal("900"), null, null, start, "note", maker, checker);
    }

    @Test
    @DisplayName("the person who asked cannot approve when someone else can; the separation check refuses")
    void selfApprovalRefused() {
        locked("TARIFF", maker, "PENDING");
        doThrow(new AccessDeniedException("The person who asked for this change cannot approve it."))
                .when(duties).require(any(), any(), any(), any(), anyString(), any(), any(), anyString());
        assertThrows(AccessDeniedException.class, () -> service.approve(t(), requestId, null, maker));
        verify(tariffs, never()).addTariff(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a sole approver may apply their own request and it is recorded as an exception")
    void soleApproverException() {
        locked("TARIFF", maker, "PENDING");
        when(duties.otherHolders(t(), WhseTariffApprovalService.PERMISSION, maker)).thenReturn(0L);
        service.approve(t(), requestId, null, maker);
        verify(jdbc).update(contains("status = 'APPLIED'"), eq(maker), isNull(), eq(true), any(), eq(requestId));
    }

    @Test
    @DisplayName("a decided request cannot be approved, rejected or cancelled again")
    void decidedIsFinal() {
        locked("TARIFF", maker, "APPLIED");
        assertThrows(HandyFlowException.class, () -> service.approve(t(), requestId, null, checker));
        assertThrows(HandyFlowException.class, () -> service.reject(t(), requestId, "no longer wanted", checker));
        assertThrows(HandyFlowException.class, () -> service.cancel(t(), requestId, maker));
    }

    @Test
    @DisplayName("rejecting needs a reason; only the requester can cancel")
    void rejectAndCancelRules() {
        locked("TARIFF", maker, "PENDING");
        assertThrows(IllegalArgumentException.class, () -> service.reject(t(), requestId, " no", checker));
        service.reject(t(), requestId, "rate not agreed with client", checker);
        verify(jdbc).update(contains("status = 'REJECTED'"), eq(checker), eq("rate not agreed with client"), eq(requestId));
        assertThrows(AccessDeniedException.class, () -> service.cancel(t(), requestId, checker));
        service.cancel(t(), requestId, maker);
        verify(jdbc).update(contains("status = 'CANCELLED'"), eq(maker), eq(requestId));
    }

    @Test
    @DisplayName("switching the rule needs a reason and is audited")
    void ruleChangeAudited() {
        rule(false);
        assertThrows(IllegalArgumentException.class, () -> service.setRequired(t(), true, "x", checker));
        UUID profile = UUID.randomUUID();
        doReturn(List.of(profile)).when(jdbc).query(eq("SELECT id FROM whse_profile WHERE tenant_id = ?"), any(RowMapper.class), any(Object[].class));
        service.setRequired(t(), true, "client contracts changed", checker);
        verify(jdbc).update(contains("SET tariff_approval_required"), eq(true), eq(tenant));
        verify(jdbc).update(contains("audit_events"), any(Object[].class));
    }
}
