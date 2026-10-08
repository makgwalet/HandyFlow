package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.dto.AccountDtos.*;

import java.math.BigDecimal;
import java.util.*;

/** Pure arithmetic of the patient account. Same balance rule as the outstanding list: rejected and voided claims are not owed. */
final class AccountRules {
    private AccountRules() {}

    static final String NOT_BILLED = "NOT_BILLED";

    private static boolean owed(String status) { return !"REJECTED".equals(status) && !"VOIDED".equals(status); }

    private static BigDecimal z(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    static AccountResponse build(UUID patientId, List<AccountVisit> visits, List<AccountPayment> payments) {
        BigDecimal charged = BigDecimal.ZERO, scheme = BigDecimal.ZERO, patient = BigDecimal.ZERO, schemeOut = BigDecimal.ZERO;
        int notBilled = 0;
        for (AccountVisit v : visits) {
            if (NOT_BILLED.equals(v.claimStatus())) { notBilled++; continue; }
            if (!owed(v.claimStatus())) continue;
            charged = charged.add(z(v.charged()));
            scheme = scheme.add(z(v.schemePortion()));
            patient = patient.add(z(v.patientPortion()));
            schemeOut = schemeOut.add(z(v.schemeOutstanding()));
        }
        BigDecimal paid = payments.stream().map(p -> z(p.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = patient.subtract(paid).max(BigDecimal.ZERO);
        return new AccountResponse(patientId, charged, scheme, patient, paid, balance, schemeOut, notBilled, visits, payments);
    }

    /** Payments recorded against this claim only; payments with no claim are on account and counted in the total. */
    static BigDecimal paidAgainst(UUID claimId, List<AccountPayment> payments) {
        if (claimId == null) return BigDecimal.ZERO;
        return payments.stream().filter(p -> claimId.equals(p.claimId())).map(p -> z(p.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
