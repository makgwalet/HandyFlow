package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.AccountDtos.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountRulesTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static AccountVisit visit(UUID claim, String status, String gross, String scheme, String patient) {
        return new AccountVisit(UUID.randomUUID(), claim, Instant.now(), "Cough", status, "Discovery",
                d(gross), d(scheme), d(patient), d("0"), BigDecimal.ZERO);
    }

    private static AccountPayment pay(UUID claim, String amount) {
        return new AccountPayment(UUID.randomUUID(), claim, Instant.now(), "CASH", d(amount), null);
    }

    @Test
    void balanceIsWhatThePatientOwesMinusEveryPayment() {
        var r = AccountRules.build(UUID.randomUUID(),
                List.of(visit(UUID.randomUUID(), "ACCEPTED", "500", "400", "100"), visit(UUID.randomUUID(), "DRAFT", "300", "0", "300")),
                List.of(pay(null, "150")));
        assertEquals(0, d("800").compareTo(r.totalCharged()));
        assertEquals(0, d("400").compareTo(r.patientShare()));
        assertEquals(0, d("250").compareTo(r.balanceOwing()));
    }

    @Test
    void rejectedAndVoidedClaimsAreNotOwed() {
        var r = AccountRules.build(UUID.randomUUID(),
                List.of(visit(UUID.randomUUID(), "REJECTED", "500", "0", "500"), visit(UUID.randomUUID(), "VOIDED", "200", "0", "200")),
                List.of());
        assertEquals(0, r.balanceOwing().signum());
        assertEquals(0, r.totalCharged().signum());
    }

    @Test
    void overpaymentNeverGivesANegativeBalance() {
        var r = AccountRules.build(UUID.randomUUID(), List.of(visit(UUID.randomUUID(), "SUBMITTED", "100", "0", "100")), List.of(pay(null, "250")));
        assertEquals(0, r.balanceOwing().signum());
        assertEquals(0, d("250").compareTo(r.paidByPatient()));
    }

    @Test
    void visitsWithoutAClaimAreCountedNotCharged() {
        var r = AccountRules.build(UUID.randomUUID(), List.of(visit(null, AccountRules.NOT_BILLED, "350", "0", "0")), List.of());
        assertEquals(1, r.visitsNotBilled());
        assertEquals(0, r.totalCharged().signum());
    }

    @Test
    void paidAgainstCountsOnlyPaymentsOnThatClaim() {
        UUID c = UUID.randomUUID();
        assertEquals(0, d("60").compareTo(AccountRules.paidAgainst(c, List.of(pay(c, "60"), pay(UUID.randomUUID(), "40"), pay(null, "10")))));
        assertEquals(0, AccountRules.paidAgainst(null, List.of(pay(null, "10"))).signum());
    }
}
