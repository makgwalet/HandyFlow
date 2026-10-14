package za.co.handyflow.platform.ap.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a supplier bill still owes and how payments and supplier credit notes settle it (finance patch F10, 2026-10-14).
 * Pure, so it is tested without Spring. Mirrors the customer side ({@code ReceiptRules}).
 * <p>
 * {@code balance = total - paid - credited}. A payment can be part of the balance. A supplier credit note is applied to
 * a bill of the same supplier; whatever is not applied stays as credit held with the supplier, to use against later bills.
 */
public final class ApPaymentRules {

    private static final BigDecimal CENT = new BigDecimal("0.005");

    private ApPaymentRules() {}

    public record Open(UUID billId, BigDecimal balance) {}
    public record Allocation(UUID billId, BigDecimal amount) {}

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** What a bill still owes; never below zero. */
    public static BigDecimal balance(BigDecimal total, BigDecimal paid, BigDecimal credited) {
        return money(money(total).subtract(money(paid)).subtract(money(credited)).max(BigDecimal.ZERO));
    }

    /** The status an approved bill has once settled by {@code paid} and {@code credited}; null when nothing is settled yet. */
    public static String settledStatus(BigDecimal total, BigDecimal paid, BigDecimal credited) {
        BigDecimal settled = money(paid).add(money(credited));
        if (settled.signum() <= 0) return null;
        return settled.compareTo(money(total)) >= 0 ? "PAID" : "PARTIALLY_PAID";
    }

    /** True for the statuses a bill can be paid, put in a batch or credited in. */
    public static boolean payable(String status) {
        return "APPROVED".equals(status) || "OVERDUE".equals(status) || "PARTIALLY_PAID".equals(status);
    }

    /** The amount to pay: what was asked for, or the whole balance when nothing was asked. */
    public static BigDecimal amountToPay(BigDecimal requested, BigDecimal balance) {
        return requested == null ? money(balance) : money(requested);
    }

    /** Null when {@code amount} may be paid against a bill that owes {@code balance}, otherwise why not. */
    public static String paymentProblem(BigDecimal amount, BigDecimal balance, boolean hasBankAccount) {
        if (amount == null || money(amount).signum() <= 0) return "Enter the amount to pay";
        if (money(balance).signum() <= 0) return "This bill has nothing left to pay";
        if (money(amount).compareTo(money(balance).add(CENT)) > 0)
            return "R " + money(amount).toPlainString() + " is more than the R " + money(balance).toPlainString() + " this bill still owes";
        if (!hasBankAccount) return "Choose the bank account the payment is made from";
        return null;
    }

    /** Null when a supplier credit note's amounts are usable, otherwise why not. */
    public static String creditAmountsProblem(BigDecimal amount, BigDecimal vat) {
        if (amount == null || money(amount).signum() <= 0) return "Enter the amount of the credit note, before VAT";
        if (vat != null && money(vat).signum() < 0) return "VAT cannot be less than zero";
        if (vat != null && money(vat).compareTo(money(amount)) > 0) return "VAT cannot be more than the amount it is charged on";
        return null;
    }

    /** Null when a credit note of {@code creditTotal} may be raised against a bill already credited {@code alreadyCredited}. */
    public static String creditNoteProblem(BigDecimal billTotal, BigDecimal alreadyCredited, BigDecimal creditTotal) {
        BigDecimal room = money(billTotal).subtract(money(alreadyCredited));
        if (money(creditTotal).compareTo(room.add(CENT)) > 0)
            return "Credit notes on this bill would come to more than it was for. At most R "
                    + money(room.max(BigDecimal.ZERO)).toPlainString() + " can still be credited";
        return null;
    }

    /** How a credit note splits: the part that reduces the bill, and the rest kept as credit with the supplier. */
    public static BigDecimal[] splitCredit(BigDecimal creditTotal, BigDecimal billBalance) {
        BigDecimal toBill = money(money(creditTotal).min(money(billBalance).max(BigDecimal.ZERO)));
        return new BigDecimal[] { toBill, money(creditTotal).subtract(toBill) };
    }

    /**
     * Null when the allocations may be made from {@code available} credit against these open bills, otherwise why not.
     * Each must be above zero, for an open bill of this supplier, not more than it owes, and together not more than is
     * available.
     */
    public static String allocationProblem(List<Allocation> allocations, BigDecimal available, Map<UUID, BigDecimal> openBalances) {
        if (allocations == null || allocations.isEmpty()) return "Choose at least one bill to apply the credit to";
        BigDecimal sum = BigDecimal.ZERO;
        Map<UUID, BigDecimal> perBill = new LinkedHashMap<>();
        for (Allocation a : allocations) {
            if (a == null || a.billId() == null) return "Each allocation needs a bill";
            if (a.amount() == null || money(a.amount()).signum() <= 0) return "Each allocation must be more than zero";
            BigDecimal open = openBalances.get(a.billId());
            if (open == null) return "One of the bills is not open for this supplier";
            BigDecimal soFar = perBill.merge(a.billId(), money(a.amount()), BigDecimal::add);
            if (soFar.compareTo(money(open).add(CENT)) > 0)
                return "R " + money(soFar).toPlainString() + " is more than the R " + money(open).toPlainString() + " that bill still owes";
            sum = sum.add(money(a.amount()));
        }
        if (sum.compareTo(money(available).add(CENT)) > 0)
            return "Allocations of R " + money(sum).toPlainString() + " are more than the R " + money(available).toPlainString() + " of credit left";
        return null;
    }

    /** Applies {@code amount} to the open bills in the order given (oldest due first), until it runs out. */
    public static List<Allocation> oldestFirst(BigDecimal amount, List<Open> openBills) {
        List<Allocation> out = new ArrayList<>();
        BigDecimal left = money(amount);
        for (Open o : openBills) {
            if (left.signum() <= 0) break;
            BigDecimal take = left.min(money(o.balance()));
            if (take.signum() > 0) {
                out.add(new Allocation(o.billId(), take));
                left = left.subtract(take);
            }
        }
        return out;
    }

    /**
     * Null when every batch line can still be paid as planned, otherwise which bills now owe less than the batch
     * says. A credit note applied or a payment made after the batch was made would otherwise pay the supplier too much.
     */
    public static String batchProblem(Map<String, BigDecimal[]> linesByBill) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : linesByBill.entrySet()) {
            BigDecimal planned = e.getValue()[0], balance = e.getValue()[1];
            if (money(planned).compareTo(money(balance).add(CENT)) > 0) changed.add(e.getKey());
        }
        if (changed.isEmpty()) return null;
        return "These bills now owe less than the batch will pay: " + String.join(", ", changed)
                + ". Cancel the batch and make it again";
    }
}
