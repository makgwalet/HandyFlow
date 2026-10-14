package za.co.handyflow.platform.invoicing.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money received from customers and how it settles invoices (finance patch F8, 2026-10-14). Pure, so it is tested without
 * Spring.
 * <p>
 * An invoice is settled by payments allocated to it and by credit notes against it:
 * {@code balance = total - paid - credited}. A receipt (money received, or the part of a credit note that was more than
 * the invoice still owed) is allocated to one or more of the same customer's invoices; whatever is not allocated stays on
 * the customer's account as credit to use later.
 */
public final class ReceiptRules {

    private static final BigDecimal CENT = new BigDecimal("0.005");

    private ReceiptRules() {}

    public record Open(UUID invoiceId, BigDecimal balance) {}
    public record Allocation(UUID invoiceId, BigDecimal amount) {}

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** What an invoice still owes; never below zero. */
    public static BigDecimal balance(BigDecimal total, BigDecimal paid, BigDecimal credited) {
        return money(money(total).subtract(money(paid)).subtract(money(credited)).max(BigDecimal.ZERO));
    }

    /** The status an issued invoice should have once settled by {@code paid} and {@code credited}; null keeps the current one. */
    public static String settledStatus(BigDecimal total, BigDecimal paid, BigDecimal credited) {
        BigDecimal settled = money(paid).add(money(credited));
        if (settled.signum() <= 0) return null;
        return settled.compareTo(money(total)) >= 0 ? "PAID" : "PARTIALLY_PAID";
    }

    /**
     * Null when the allocations may be made from {@code available} against these open invoices, otherwise why not. Each
     * allocation must be above zero, for an open invoice of this customer, not more than it owes, and together not more
     * than is available.
     */
    public static String allocationProblem(List<Allocation> allocations, BigDecimal available, Map<UUID, BigDecimal> openBalances) {
        if (allocations == null || allocations.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        Map<UUID, BigDecimal> perInvoice = new LinkedHashMap<>();
        for (Allocation a : allocations) {
            if (a == null || a.invoiceId() == null) return "Each allocation needs an invoice";
            if (a.amount() == null || money(a.amount()).signum() <= 0) return "Each allocation must be more than zero";
            BigDecimal open = openBalances.get(a.invoiceId());
            if (open == null) return "One of the invoices is not open for this customer";
            BigDecimal soFar = perInvoice.merge(a.invoiceId(), money(a.amount()), BigDecimal::add);
            if (soFar.compareTo(money(open).add(CENT)) > 0)
                return "R " + money(soFar).toPlainString() + " is more than the R " + money(open).toPlainString() + " that invoice still owes";
            sum = sum.add(money(a.amount()));
        }
        if (sum.compareTo(money(available).add(CENT)) > 0)
            return "Allocations of R " + money(sum).toPlainString() + " are more than the R " + money(available).toPlainString() + " available";
        return null;
    }

    /** Allocates {@code amount} to the open invoices in the order given (oldest first), until it runs out. */
    public static List<Allocation> oldestFirst(BigDecimal amount, List<Open> openInvoices) {
        List<Allocation> out = new ArrayList<>();
        BigDecimal left = money(amount);
        for (Open o : openInvoices) {
            if (left.signum() <= 0) break;
            BigDecimal take = left.min(money(o.balance()));
            if (take.signum() > 0) {
                out.add(new Allocation(o.invoiceId(), take));
                left = left.subtract(take);
            }
        }
        return out;
    }

    /** Null when a credit note of {@code creditTotal} may be issued against an invoice already credited {@code alreadyCredited}. */
    public static String creditNoteProblem(BigDecimal invoiceTotal, BigDecimal alreadyCredited, BigDecimal creditTotal) {
        BigDecimal room = money(invoiceTotal).subtract(money(alreadyCredited));
        if (money(creditTotal).compareTo(room.add(CENT)) > 0)
            return "Credit notes on this invoice would come to more than it was for. At most R " + money(room.max(BigDecimal.ZERO)).toPlainString() + " can still be credited";
        return null;
    }

    /** How a new credit note splits: the part that reduces the invoice, and the rest that goes on the customer's account. */
    public static BigDecimal[] splitCredit(BigDecimal creditTotal, BigDecimal invoiceBalance) {
        BigDecimal toInvoice = money(money(creditTotal).min(money(invoiceBalance).max(BigDecimal.ZERO)));
        return new BigDecimal[] { toInvoice, money(creditTotal).subtract(toInvoice) };
    }

    /**
     * Finance patch F9: null when {@code amount} may be refunded out of credit of {@code available}, otherwise why not.
     * Money going out always says from which bank account and why.
     */
    public static String refundProblem(BigDecimal amount, BigDecimal available, boolean hasBankAccount, String reason) {
        if (amount == null || money(amount).signum() <= 0) return "Enter the amount to refund";
        if (money(amount).compareTo(money(available).add(CENT)) > 0)
            return "Only R " + money(available).toPlainString() + " of this credit is left to refund";
        if (!hasBankAccount) return "Choose the bank account the refund is paid from";
        if (reason == null || reason.trim().length() < 5) return "Say why the money is refunded (at least 5 characters)";
        return null;
    }
}
