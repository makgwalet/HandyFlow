package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplier;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplierBankAccount;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierBankAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSupplierRepository;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.BankAccountView;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.ProposeRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelSupplierBankServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID SUP = UUID.randomUUID(), ALICE = UUID.randomUUID(), BOB = UUID.randomUUID();

    @Mock FuelSupplierBankAccountRepository accounts;
    @Mock FuelSupplierRepository suppliers;
    FuelSupplierBankService service;

    @BeforeEach
    void setUp() {
        service = new FuelSupplierBankService(accounts, suppliers);
        when(suppliers.findActiveById(any(TenantId.class), eq(SUP))).thenReturn(Optional.of(mock(FuelSupplier.class)));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accounts.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(accounts.findPending(any(TenantId.class), eq(SUP))).thenReturn(Optional.empty());
        when(accounts.findVerified(any(TenantId.class), eq(SUP))).thenReturn(Optional.empty());
    }

    private static ProposeRequest req(String number, String reason) { return new ProposeRequest("Acme Fuels (Pty) Ltd", "Standard Bank", "051001", number, "CURRENT", reason); }

    private FuelSupplierBankAccount acct(String number, UUID by) {
        return FuelSupplierBankAccount.propose(T, SUP, "Acme", "Standard Bank", "051001", number, "CURRENT", null, by);
    }

    /** An account someone else already verified. */
    private FuelSupplierBankAccount verifiedAcct(String number) {
        FuelSupplierBankAccount a = acct(number, BOB);
        a.verify(ALICE, "Checked against the contract");
        return a;
    }

    private FuelSupplierBankAccount waiting(UUID by) {
        FuelSupplierBankAccount a = acct("62012345678", by);
        when(accounts.findOneForUpdate(any(TenantId.class), eq(SUP), eq(a.getId()))).thenReturn(Optional.of(a));
        return a;
    }

    @Test @DisplayName("a proposal waits for verification and lists show only the last four digits")
    void proposeIsMasked() {
        BankAccountView v = service.propose(T, SUP, req("6201 2345-678", null), ALICE);
        assertEquals("PENDING_VERIFICATION", v.status()); assertEquals("****5678", v.accountNumberMasked());
        assertFalse(v.toString().contains("62012345678"));
    }

    @Test @DisplayName("bad details are refused: holder, bank, digits only, 5 to 20 long, known type")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, " ", "B", null, "12345678", null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", " ", null, "12345678", null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", "B", null, "12AB5678", null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", "B", null, "1234", null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", "B", null, "1".repeat(21), null, null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", "B", null, "12345678", "CRYPTO", null, ALICE));
        assertThrows(IllegalArgumentException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", "B", "05 10;01", "12345678", null, null, ALICE));
        assertThrows(IllegalStateException.class, () -> FuelSupplierBankAccount.propose(T, SUP, "A", "B", null, "12345678", null, null, null));
    }

    @Test @DisplayName("only one proposal can wait at a time")
    void onePending() {
        when(accounts.findPending(any(TenantId.class), eq(SUP))).thenReturn(Optional.of(acct("11111111", ALICE)));
        assertThrows(IllegalStateException.class, () -> service.propose(T, SUP, req("22222222", null), BOB));
        verify(accounts, never()).save(any());
    }

    @Test @DisplayName("changing verified details needs a reason, and proposing the same details again is refused")
    void replacingNeedsReasonAndChange() {
        when(accounts.findVerified(any(TenantId.class), eq(SUP))).thenReturn(Optional.of(verifiedAcct("62012345678")));
        assertThrows(IllegalArgumentException.class, () -> service.propose(T, SUP, req("99998888", null), BOB));
        assertThrows(IllegalStateException.class, () -> service.propose(T, SUP, req("62012345678", "no change"), BOB));
        assertEquals("PENDING_VERIFICATION", service.propose(T, SUP, req("99998888", "Supplier moved banks"), BOB).status());
    }

    @Test @DisplayName("the proposer cannot verify or reject their own proposal; nothing is retired or saved")
    void secondPerson() {
        FuelSupplierBankAccount a = waiting(ALICE);
        FuelSupplierBankAccount old = verifiedAcct("11112222");
        when(accounts.findVerified(any(TenantId.class), eq(SUP))).thenReturn(Optional.of(old));
        assertThrows(IllegalStateException.class, () -> service.verify(T, SUP, a.getId(), "Called the supplier", ALICE));
        assertThrows(IllegalStateException.class, () -> service.reject(T, SUP, a.getId(), "Not right", ALICE));
        assertTrue(a.isPending()); assertTrue(old.isVerified());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test @DisplayName("verifying needs a note saying how, then the new account counts and the old one is kept as history")
    void verifyRetiresOld() {
        FuelSupplierBankAccount a = waiting(ALICE);
        FuelSupplierBankAccount old = verifiedAcct("11112222");
        when(accounts.findVerified(any(TenantId.class), eq(SUP))).thenReturn(Optional.of(old));
        assertThrows(IllegalArgumentException.class, () -> service.verify(T, SUP, a.getId(), "ok", BOB));
        BankAccountView v = service.verify(T, SUP, a.getId(), "Called the supplier on the number from the contract", BOB);
        assertEquals("VERIFIED", v.status()); assertEquals(BOB, v.decidedBy());
        assertEquals("SUPERSEDED", old.getStatus()); assertNotNull(old.getSupersededAt());
        var order = inOrder(accounts); order.verify(accounts).saveAndFlush(old); order.verify(accounts).save(a);
    }

    @Test @DisplayName("a decided proposal cannot be decided again")
    void decidedOnce() {
        FuelSupplierBankAccount a = waiting(ALICE);
        service.reject(T, SUP, a.getId(), "Details do not match the contract", BOB);
        assertEquals("REJECTED", a.getStatus());
        assertThrows(IllegalStateException.class, () -> service.verify(T, SUP, a.getId(), "Changed my mind", BOB));
    }

    @Test @DisplayName("only the proposer can withdraw, and only while it waits")
    void withdraw() {
        FuelSupplierBankAccount a = waiting(ALICE);
        assertThrows(IllegalStateException.class, () -> service.withdraw(T, SUP, a.getId(), BOB));
        assertEquals("WITHDRAWN", service.withdraw(T, SUP, a.getId(), ALICE).status());
        assertThrows(IllegalStateException.class, () -> service.withdraw(T, SUP, a.getId(), ALICE));
    }

    @Test @DisplayName("revealing gives the full number for that supplier's account only; an unknown account is not found")
    void reveal() {
        FuelSupplierBankAccount a = acct("62012345678", ALICE);
        when(accounts.findOne(any(TenantId.class), eq(SUP), eq(a.getId()))).thenReturn(Optional.of(a));
        assertEquals("62012345678", service.reveal(T, SUP, a.getId(), BOB).accountNumber());
        UUID other = UUID.randomUUID();
        when(accounts.findOne(any(TenantId.class), eq(SUP), eq(other))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.reveal(T, SUP, other, BOB));
    }

    @Test @DisplayName("an unknown supplier is not found for list and propose")
    void unknownSupplier() {
        UUID none = UUID.randomUUID();
        when(suppliers.findActiveById(any(TenantId.class), eq(none))).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.list(T, none));
        assertThrows(ResourceNotFoundException.class, () -> service.propose(T, none, req("12345678", null), ALICE));
    }

    @Test @DisplayName("the list is newest first and masked")
    void listMasked() {
        when(accounts.findBySupplier(any(TenantId.class), eq(SUP))).thenReturn(List.of(acct("62012345678", ALICE)));
        assertEquals("****5678", service.list(T, SUP).get(0).accountNumberMasked());
    }
}
