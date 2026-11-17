package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a customer account was charged by Fuel in a period: fuel deliveries (Distribution) and overnight stays and driver tabs (Truck Stop).
 * Line amounts are before VAT. The invoices Fuel raised for those lines are listed with their standing from Invoicing (total including VAT,
 * paid, credited, still owed). Fuel keeps no ledger of payments: those figures are read from Invoicing, and only for invoices Fuel itself raised.
 */
public final class AccountStatementDtos {
    private AccountStatementDtos() {}

    /** kind is DELIVERY, STAY or TAB. invoiceState and invoiceNumber are as on the source (null or NONE state means not queued yet). */
    public record Line(LocalDate date, String kind, UUID sourceId, String vehicleRegistration, String description, BigDecimal amount, String invoiceState, String invoiceNumber, UUID invoiceId) {
        public Line(LocalDate date, String kind, UUID sourceId, String vehicleRegistration, String description, BigDecimal amount, String invoiceState, String invoiceNumber) {
            this(date, kind, sourceId, vehicleRegistration, description, amount, invoiceState, invoiceNumber, null);
        }
    }

    /** One invoice Fuel raised for this account, with what Invoicing says about it. Amounts include VAT. */
    public record InvoiceStanding(UUID invoiceId, String invoiceNumber, String status, Instant issuedAt, BigDecimal total, BigDecimal paid, BigDecimal credited, BigDecimal outstanding) {}

    /**
     * division is ALL, DISTRIBUTION or TRUCK_STOP. invoices, paid, credited and outstanding cover the invoices Fuel raised for the lines shown;
     * settlementKnown is false when Invoicing could not be read, in which case those figures are empty and the lines are still correct.
     */
    public record Statement(UUID accountId, LocalDate from, LocalDate to, List<Line> lines, BigDecimal total, BigDecimal invoiced, BigDecimal notInvoiced,
                            String division, List<InvoiceStanding> invoices, BigDecimal invoicedWithVat, BigDecimal paid, BigDecimal credited, BigDecimal outstanding, boolean settlementKnown) {
        public Statement(UUID accountId, LocalDate from, LocalDate to, List<Line> lines, BigDecimal total, BigDecimal invoiced, BigDecimal notInvoiced) {
            this(accountId, from, to, lines, total, invoiced, notInvoiced, "ALL", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
    }
}
