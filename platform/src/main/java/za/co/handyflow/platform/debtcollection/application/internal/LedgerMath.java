package za.co.handyflow.platform.debtcollection.application.internal;

import za.co.handyflow.platform.debtcollection.domain.model.LedgerEntryType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ledger arithmetic over plain lines (no entities, so it is trivially testable).
 * PAYMENT adds to recovered; REVERSAL subtracts exactly what the payment added (same split); FEE_CHARGE adds to fees
 * owed; FEE_WAIVER subtracts. feesOutstanding = charged − waived − fees recovered (net of reversals), floored at 0.
 */
final class LedgerMath {

    record Line(LedgerEntryType type, BigDecimal amount, BigDecimal principal, BigDecimal fees) {}

    record Totals(BigDecimal paymentsGross, BigDecimal reversals, BigDecimal netRecovered, BigDecimal principalRecoveredNet,
                  BigDecimal feesRecoveredNet, BigDecimal feesCharged, BigDecimal feesWaived, BigDecimal feesOutstanding) {}

    private LedgerMath() {}

    static Totals summarise(List<Line> lines) {
        BigDecimal pay = BigDecimal.ZERO, rev = BigDecimal.ZERO, pPay = BigDecimal.ZERO, pRev = BigDecimal.ZERO,
                fPay = BigDecimal.ZERO, fRev = BigDecimal.ZERO, charged = BigDecimal.ZERO, waived = BigDecimal.ZERO;
        for (Line l : lines) {
            switch (l.type()) {
                case PAYMENT -> { pay = pay.add(l.amount()); pPay = pPay.add(l.principal()); fPay = fPay.add(l.fees()); }
                case REVERSAL -> { rev = rev.add(l.amount()); pRev = pRev.add(l.principal()); fRev = fRev.add(l.fees()); }
                case FEE_CHARGE -> charged = charged.add(l.amount());
                case FEE_WAIVER -> waived = waived.add(l.amount());
            }
        }
        BigDecimal feesRecovered = fPay.subtract(fRev);
        return new Totals(pay, rev, pay.subtract(rev), pPay.subtract(pRev), feesRecovered, charged, waived,
                charged.subtract(waived).subtract(feesRecovered).max(BigDecimal.ZERO));
    }
}
