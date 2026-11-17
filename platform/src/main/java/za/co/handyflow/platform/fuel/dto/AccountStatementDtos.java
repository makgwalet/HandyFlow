package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What the truck stop charged to one customer account in a period: overnight stays and driver tabs. Amounts are before VAT; VAT is added on the invoice. */
public final class AccountStatementDtos {
    private AccountStatementDtos() {}

    /** kind is STAY or TAB. invoiceState and invoiceNumber are as on the stay or tab (null state means not queued yet). */
    public record Line(LocalDate date, String kind, UUID sourceId, String vehicleRegistration, String description, BigDecimal amount, String invoiceState, String invoiceNumber) {}

    public record Statement(UUID accountId, LocalDate from, LocalDate to, List<Line> lines, BigDecimal total, BigDecimal invoiced, BigDecimal notInvoiced) {}
}
