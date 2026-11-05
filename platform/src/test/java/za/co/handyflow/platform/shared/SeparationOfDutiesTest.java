package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeparationOfDutiesTest {

    static final UUID MAKER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    static final UUID ITEM = UUID.fromString("33333333-3333-4333-8333-333333333333");
    static final UUID OTHER = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock JdbcTemplate jdbc;

    private SeparationOfDutiesChecker checkerWithOtherHolders(long n) {
        doReturn(n).when(jdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        return new SeparationOfDutiesChecker(jdbc);
    }

    private static TenantId tenant() {
        TenantId t = mock(TenantId.class);
        lenient().when(t.getValue()).thenReturn(UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"));
        return t;
    }

    // ── the pure rule ──────────────────────────────────────────────────────────

    @Test void aDifferentPersonIsAlwaysFine() {
        assertNull(SeparationOfDuties.problem("m", MAKER, OTHER, 5));
        assertFalse(SeparationOfDuties.soleException(MAKER, OTHER, 0));
    }

    @Test void theSamePersonIsRefusedWhenSomeoneElseCould() {
        assertEquals("m (one other person can)", SeparationOfDuties.problem("m", MAKER, MAKER, 1));
        assertEquals("m (3 other people can)", SeparationOfDuties.problem("m", MAKER, MAKER, 3));
    }

    @Test void theSamePersonIsAllowedWhenNobodyElseCan() {
        assertNull(SeparationOfDuties.problem("m", MAKER, MAKER, 0));
        assertTrue(SeparationOfDuties.soleException(MAKER, MAKER, 0));
    }

    @Test void anUnknownEarlierActorOrActorIsNotAConflict() {
        assertNull(SeparationOfDuties.problem("m", null, MAKER, 2));
        assertNull(SeparationOfDuties.problem("m", MAKER, null, 2));
    }

    // ── the checker ────────────────────────────────────────────────────────────

    @Test void checkerRefusesSelfApprovalWhenAnotherApproverExists() {
        var c = checkerWithOtherHolders(1);
        var e = assertThrows(HandyFlowException.class,
                () -> c.require(tenant(), "ACCOUNTING_ADMIN", MAKER, MAKER, "You made this", "JOURNAL", ITEM, "journal=J1"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("SAME_PERSON", e.getErrorCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void checkerAllowsASoleUserToApproveTheirOwnWork() {
        var c = checkerWithOtherHolders(0);
        assertDoesNotThrow(() -> c.require(tenant(), "ACCOUNTING_ADMIN", MAKER, MAKER, "You made this", "JOURNAL", ITEM, "journal=J1"));
        // the sole-approver step is written to the audit trail so the owner can report it
        verify(jdbc).update(contains("INSERT INTO audit_events"), any(Object[].class));
    }

    @Test void checkerDoesNotEvenCountWhenTheActorsDiffer() {
        var c = new SeparationOfDutiesChecker(jdbc);
        assertDoesNotThrow(() -> c.require(tenant(), "ACCOUNTING_ADMIN", MAKER, OTHER, "x", "JOURNAL", ITEM, "y"));
        verifyNoInteractions(jdbc);
    }

    @Test void aConflictDecidedByTheCallerStillAllowsTheSoleApprover() {
        assertDoesNotThrow(() -> checkerWithOtherHolders(0).requireIfConflict(tenant(), "EXPENSES_MANAGE", true, MAKER, "x", "JOURNAL", ITEM, "y"));
        assertThrows(HandyFlowException.class,
                () -> checkerWithOtherHolders(2).requireIfConflict(tenant(), "EXPENSES_MANAGE", true, MAKER, "x", "JOURNAL", ITEM, "y"));
        assertDoesNotThrow(() -> new SeparationOfDutiesChecker(jdbc).requireIfConflict(tenant(), "EXPENSES_MANAGE", false, MAKER, "x", "JOURNAL", ITEM, "y"));
    }
}
