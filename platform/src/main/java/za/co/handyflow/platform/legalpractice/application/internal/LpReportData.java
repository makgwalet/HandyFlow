package za.co.handyflow.platform.legalpractice.application.internal;

import za.co.handyflow.platform.legalpractice.dto.ReportDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The totals the reports are built from. Every method already leaves out the matters and clients passed in (the caller's ethical walls); the lists are never empty
 * (a placeholder id stands in). Implemented with SQL in {@code JpaLpReportData}; tests use an in-memory one.
 */
public interface LpReportData {

    List<MatterCount> matterCounts(TenantId tenant, List<UUID> barredMatters, List<UUID> barredClients);

    /** Matters with unbilled billable time or unbilled disbursements, largest first, at most {@code limit} rows. */
    List<UnbilledRow> unbilled(TenantId tenant, List<UUID> barredMatters, List<UUID> barredClients, int limit);

    /** Issued invoices (sent or part paid) with something still owing, oldest due date first. {@code daysOverdue} and {@code bucket} are filled in by the service. */
    List<DebtorRow> openInvoices(TenantId tenant, List<UUID> barredMatters, List<UUID> barredClients, int limit);

    /** Invoices issued in the range (drafts left out), totalled by month and status. */
    List<BillingRow> invoicesIssued(TenantId tenant, LocalDate from, LocalDate to, List<UUID> barredMatters, List<UUID> barredClients);

    /** Trust balance per client up to and including {@code asAt}, clients whose balance is not zero. */
    List<TrustRow> trustBalances(TenantId tenant, LocalDate asAt, List<UUID> barredMatters, List<UUID> barredClients);
}
