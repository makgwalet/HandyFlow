package za.co.handyflow.platform.accounting.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.AuditPackBuilder.FileEntry;
import za.co.handyflow.platform.accounting.application.internal.AuditPackBuilder.Pack;
import za.co.handyflow.platform.accounting.application.internal.AuditPackBuilder.Table;
import za.co.handyflow.platform.accounting.domain.model.AccJournalEntry;
import za.co.handyflow.platform.accounting.domain.repository.AccJournalEntryRepository;
import za.co.handyflow.platform.accounting.dto.AccountDrillDownResponse;
import za.co.handyflow.platform.accounting.dto.AgingReportResponse;
import za.co.handyflow.platform.accounting.dto.BankAccountResponse;
import za.co.handyflow.platform.accounting.dto.BankReconciliationResponse;
import za.co.handyflow.platform.accounting.dto.FinancialReportResponse;
import za.co.handyflow.platform.accounting.dto.FixedAssetDtos;
import za.co.handyflow.platform.accounting.dto.Vat201Response;
import za.co.handyflow.platform.identity.domain.model.User;
import za.co.handyflow.platform.identity.domain.repository.UserRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The financial audit pack: the schedules an external auditor asks for, assembled from the books for one period and handed over as a
 * ZIP of CSV files with a manifest of hashes. Built from the ledger each time; "verify" rebuilds it and compares fingerprints.
 */
@Service
@RequiredArgsConstructor
public class AuditPackService {

    public static final int MAX_DAYS = 731;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Comparator<String> BY_TEXT = Comparator.nullsFirst(Comparator.naturalOrder());

    private final AccountingService accounting;
    private final AccJournalEntryRepository journalRepo;
    private final BankReconciliationService reconciliations;
    private final FixedAssetService assets;
    private final UserRepository userRepo;
    private final JdbcTemplate jdbc;
    private final za.co.handyflow.platform.audittrail.application.AuditTrailFacade trail;

    public record RunInfo(UUID id, UUID engagementId, LocalDate from, LocalDate to, UUID generatedBy, Instant generatedAt, String packSha256, int fileCount) {}
    public record Generated(RunInfo run, Pack pack) {}
    public record Verification(boolean matches, String storedSha256, String currentSha256, List<String> changedFiles) {}

    @Transactional
    public Generated generate(TenantId tenantId, LocalDate from, LocalDate to, UUID engagementId, UUID by, String byName) {
        checkPeriod(from, to);
        Instant now = Instant.now();
        Pack pack = assemble(tenantId, from, to, engagementId, now, byName);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO audit_pack_runs (id, tenant_id, engagement_id, period_from, period_to, generated_by, generated_at, pack_sha256, file_count, manifest) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, tenantId.getValue(), engagementId, from, to, by, java.sql.Timestamp.from(now), pack.packSha256(), pack.files().size(), pack.manifestJson());
        trail.record(tenantId, "AUDIT_PACK_GENERATED", "AUDIT_PACK", id, null,
                Map.of("from", from.toString(), "to", to.toString(), "packSha256", pack.packSha256(), "files", pack.files().size()));
        return new Generated(new RunInfo(id, engagementId, from, to, by, now, pack.packSha256(), pack.files().size()), pack);
    }

    @Transactional(readOnly = true)
    public List<RunInfo> runs(TenantId tenantId) {
        return jdbc.query("SELECT id, engagement_id, period_from, period_to, generated_by, generated_at, pack_sha256, file_count FROM audit_pack_runs WHERE tenant_id = ? ORDER BY generated_at DESC LIMIT 100",
                (rs, n) -> info(rs), tenantId.getValue());
    }

    @Transactional(readOnly = true)
    public String manifest(TenantId tenantId, UUID runId) {
        List<String> m = jdbc.queryForList("SELECT manifest FROM audit_pack_runs WHERE tenant_id = ? AND id = ?", String.class, tenantId.getValue(), runId);
        if (m.isEmpty()) missing(runId);
        return m.get(0);
    }

    /** Builds the pack again for the same period and compares it with what was produced then. Differences mean the books have changed since. */
    @Transactional(readOnly = true)
    public Verification verify(TenantId tenantId, UUID runId) {
        List<RunInfo> found = jdbc.query("SELECT id, engagement_id, period_from, period_to, generated_by, generated_at, pack_sha256, file_count FROM audit_pack_runs WHERE tenant_id = ? AND id = ?",
                (rs, n) -> info(rs), tenantId.getValue(), runId);
        if (found.isEmpty()) missing(runId);
        RunInfo run = found.get(0);
        Pack now = assemble(tenantId, run.from(), run.to(), run.engagementId(), Instant.now(), "verification");
        return compare(run.packSha256(), manifest(tenantId, runId), now);
    }

    static Verification compare(String storedSha, String storedManifest, Pack current) {
        if (storedSha.equals(current.packSha256())) return new Verification(true, storedSha, current.packSha256(), List.of());
        Map<String, String> before = new HashMap<>();
        try {
            for (JsonNode f : JSON.readTree(storedManifest).get("files")) before.put(f.get("name").asText(), f.get("sha256").asText());
        } catch (Exception e) { throw new IllegalStateException(e); }
        Set<String> changed = new TreeSet<>();
        for (FileEntry f : current.files()) if (!f.sha256().equals(before.get(f.name()))) changed.add(f.name());
        before.keySet().stream().filter(n -> current.files().stream().noneMatch(f -> f.name().equals(n))).forEach(changed::add);
        return new Verification(false, storedSha, current.packSha256(), new ArrayList<>(changed));
    }

    static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new HandyFlowException("Choose the period to audit", HttpStatus.BAD_REQUEST, "PERIOD_REQUIRED");
        if (to.isBefore(from)) throw new HandyFlowException("The period must end on or after the day it starts", HttpStatus.BAD_REQUEST, "PERIOD_BACKWARDS");
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) throw new HandyFlowException("A pack covers at most two years", HttpStatus.BAD_REQUEST, "PERIOD_TOO_LONG");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private Pack assemble(TenantId tenantId, LocalDate from, LocalDate to, UUID engagementId, Instant now, String byName) {
        LocalDate priorFrom = from.minusYears(1), priorTo = to.minusYears(1);
        FinancialReportResponse tb = accounting.getTrialBalance(tenantId, from, to);
        FinancialReportResponse tbPrior = accounting.getTrialBalance(tenantId, priorFrom, priorTo);

        List<Table> tables = new ArrayList<>();
        tables.add(trialBalance(tb, tbPrior));
        tables.add(generalLedger(tenantId, tb, from, to));
        tables.add(journals(tenantId, from, to));
        tables.add(bankReconciliations(tenantId, from, to));
        tables.add(debtors(accounting.getArAging(tenantId)));
        List<FixedAssetDtos.Asset> register = assets.list(tenantId);
        tables.add(fixedAssets(register));
        tables.add(depreciationRuns(tenantId, from, to));
        tables.add(fixedAssetReconciliation(tenantId));
        tables.add(vat(accounting.getVat201(tenantId, from, to)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenantId.getValue().toString());
        params.put("engagementId", engagementId == null ? null : engagementId.toString());
        params.put("periodFrom", from.toString());
        params.put("periodTo", to.toString());
        params.put("priorPeriodFrom", priorFrom.toString());
        params.put("priorPeriodTo", priorTo.toString());

        List<String> notes = List.of(
                "The trial balance, general ledger, journal listing, bank reconciliations, depreciation runs and VAT figures are for the period in 'parameters'.",
                "The debtors schedule, the fixed asset register and the fixed asset reconciliation are as at the day the pack was built, not the end of the period, because the books can only report them as they stand now.",
                "Journals listed include reversed ones; each reversal is listed beside the entry it reverses.",
                "Re-running 'verify' rebuilds the pack and compares its fingerprint with this one. A different fingerprint means the books have changed since, and the manifest names the files that differ.");
        List<String> notIncluded = List.of(
                "Creditors (accounts payable) schedule: held in the payables module and not yet part of the pack.",
                "Approval histories for journals, payments and bills.",
                "VAT workings beyond the VAT201 totals (no per-invoice VAT listing).",
                "Supporting documents.");
        return AuditPackBuilder.build(params, tables, notIncluded, notes, now, byName);
    }

    private static String n(BigDecimal v) { return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static String d(LocalDate v) { return v == null ? "" : v.toString(); }
    private static String s(Object v) { return v == null ? "" : v.toString(); }

    static Table trialBalance(FinancialReportResponse cur, FinancialReportResponse prior) {
        Map<String, String[]> rows = new TreeMap<>();
        for (var sec : cur.sections()) for (var l : sec.lines())
            rows.put(l.accountCode(), new String[]{l.accountCode(), l.accountName(), sec.title(), n(l.grossDebit()), n(l.grossCredit()), n(l.amount()), "0.00"});
        for (var sec : prior.sections()) for (var l : sec.lines()) {
            String[] r = rows.get(l.accountCode());
            if (r != null) r[6] = n(l.amount());
            else rows.put(l.accountCode(), new String[]{l.accountCode(), l.accountName(), sec.title(), "0.00", "0.00", "0.00", n(l.amount())});
        }
        return new Table("01_trial_balance.csv", "Trial balance for the period, with the prior-year period beside it",
                List.of("account_code", "account_name", "section", "debit", "credit", "net", "prior_period_net"),
                rows.values().stream().map(Arrays::asList).toList());
    }

    private Table generalLedger(TenantId tenantId, FinancialReportResponse tb, LocalDate from, LocalDate to) {
        List<List<String>> rows = new ArrayList<>();
        Set<String> codes = new TreeSet<>();
        for (var sec : tb.sections()) for (var l : sec.lines()) codes.add(l.accountCode());
        for (String code : codes) {
            AccountDrillDownResponse dd = accounting.getAccountDrillDown(tenantId, code, from, to);
            List<AccountDrillDownResponse.DrillDownLine> lines = new ArrayList<>(dd.lines());
            lines.sort(Comparator.comparing(AccountDrillDownResponse.DrillDownLine::entryDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(AccountDrillDownResponse.DrillDownLine::entryNumber, BY_TEXT)
                    .thenComparing(l -> n(l.debitAmount())).thenComparing(l -> n(l.creditAmount())));
            for (var l : lines) rows.add(List.of(code, s(dd.accountName()), s(l.entryNumber()), d(l.entryDate()), s(l.entryDescription()), s(l.lineDescription()), n(l.debitAmount()), n(l.creditAmount())));
        }
        return new Table("02_general_ledger.csv", "Every posted journal line behind each trial balance account",
                List.of("account_code", "account_name", "entry_number", "entry_date", "entry_description", "line_description", "debit", "credit"), rows);
    }

    private Table journals(TenantId tenantId, LocalDate from, LocalDate to) {
        List<AccJournalEntry> entries = journalRepo.findPostedInRange(tenantId, from, to);
        Map<UUID, String> numberById = entries.stream().collect(Collectors.toMap(AccJournalEntry::getId, AccJournalEntry::getEntryNumber, (a, b) -> a));
        Set<UUID> people = new HashSet<>();
        entries.forEach(e -> { if (e.getCreatedBy() != null) people.add(e.getCreatedBy()); if (e.getPostedBy() != null) people.add(e.getPostedBy()); });
        Map<UUID, String> names = new HashMap<>();
        for (User u : userRepo.findAllById(people)) names.put(u.getId(), (u.getFirstName() + " " + u.getLastName()).trim());
        List<AccJournalEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(AccJournalEntry::getEntryDate).thenComparing(AccJournalEntry::getEntryNumber, BY_TEXT));
        List<List<String>> rows = new ArrayList<>();
        for (AccJournalEntry e : sorted) {
            String reverses = e.getReversesEntryId() == null ? "" : numberById.getOrDefault(e.getReversesEntryId(), "(outside period)");
            rows.add(List.of(s(e.getEntryNumber()), d(e.getEntryDate()), s(e.getEntryType()), s(e.getStatus()), s(e.getDescription()), s(e.getReference()),
                    n(e.getTotalDebit()), n(e.getTotalCredit()), e.getSourceKey() == null ? "manual" : "system", reverses,
                    names.getOrDefault(e.getCreatedBy(), s(e.getCreatedBy())), names.getOrDefault(e.getPostedBy(), s(e.getPostedBy())),
                    e.getPostedAt() == null ? "" : e.getPostedAt().toString()));
        }
        return new Table("03_journals.csv", "All journals in the period, including reversed ones and the reversals that cancel them",
                List.of("entry_number", "entry_date", "entry_type", "status", "description", "reference", "total_debit", "total_credit", "origin",
                        "reverses_entry", "created_by", "posted_by", "posted_at"), rows);
    }

    private Table bankReconciliations(TenantId tenantId, LocalDate from, LocalDate to) {
        List<List<String>> rows = new ArrayList<>();
        List<BankAccountResponse> banks = new ArrayList<>(accounting.getBankAccounts(tenantId));
        banks.sort(Comparator.comparing(BankAccountResponse::accountName, BY_TEXT));
        for (BankAccountResponse b : banks) {
            List<BankReconciliationResponse> hist = new ArrayList<>(reconciliations.history(tenantId, b.id()));
            hist.sort(Comparator.comparing(BankReconciliationResponse::periodEnd).thenComparing(r -> s(r.signedAt())));
            for (BankReconciliationResponse r : hist) {
                if (r.periodEnd().isBefore(from) || r.periodEnd().isAfter(to)) continue;
                rows.add(List.of(s(b.accountName()), s(b.accountNumber()), d(r.periodEnd()), n(r.statementBalance()), n(r.bookBalance()),
                        String.valueOf(r.unmatchedBookCount()), n(r.unmatchedBookNet()), n(r.difference()), s(r.status()), s(r.note()),
                        r.signedAt() == null ? "" : r.signedAt().toString(), r.reopenedAt() == null ? "" : r.reopenedAt().toString(), s(r.reopenReason())));
            }
        }
        return new Table("04_bank_reconciliations.csv", "Signed-off bank reconciliations with a period end in the period, including any that were reopened",
                List.of("bank_account", "account_number", "period_end", "statement_balance", "book_balance", "unmatched_book_items", "unmatched_book_net",
                        "difference", "status", "note", "signed_at", "reopened_at", "reopen_reason"), rows);
    }

    static Table debtors(AgingReportResponse a) {
        List<AgingReportResponse.AgingLine> lines = new ArrayList<>(a.lines());
        lines.sort(Comparator.comparing(AgingReportResponse.AgingLine::customerName, BY_TEXT).thenComparing(AgingReportResponse.AgingLine::invoiceNumber, BY_TEXT));
        List<List<String>> rows = new ArrayList<>();
        for (var l : lines) rows.add(List.of(d(a.asAt()), s(l.customerName()), s(l.invoiceNumber()), d(l.dueDate()), String.valueOf(l.daysOverdue()), n(l.balance()), s(l.bucket())));
        return new Table("05_debtors_aging.csv", "Outstanding customer invoices by age, as at the day the pack was built",
                List.of("as_at", "customer", "invoice_number", "due_date", "days_overdue", "balance", "bucket"), rows);
    }

    static Table fixedAssets(List<FixedAssetDtos.Asset> register) {
        List<FixedAssetDtos.Asset> sorted = new ArrayList<>(register);
        sorted.sort(Comparator.comparing(FixedAssetDtos.Asset::assetNumber, BY_TEXT));
        List<List<String>> rows = new ArrayList<>();
        for (var a : sorted) rows.add(List.of(s(a.assetNumber()), s(a.name()), s(a.serialNumber()), s(a.location()), d(a.acquiredOn()), n(a.cost()),
                n(a.residualValue()), s(a.method()), s(a.lifeMonths()), s(a.ratePercent()), s(a.status()), d(a.disposedOn()), n(a.disposalProceeds()),
                n(a.accumulated()), n(a.bookValue())));
        return new Table("06_fixed_asset_register.csv", "Fixed asset register with cost, accumulated depreciation and book value, as at the day the pack was built",
                List.of("asset_number", "name", "serial_number", "location", "acquired_on", "cost", "residual_value", "method", "life_months", "rate_percent",
                        "status", "disposed_on", "disposal_proceeds", "accumulated_depreciation", "book_value"), rows);
    }

    private Table depreciationRuns(TenantId tenantId, LocalDate from, LocalDate to) {
        List<FixedAssetDtos.Run> runs = new ArrayList<>(assets.runs(tenantId));
        runs.removeIf(r -> r.period().isBefore(from) || r.period().isAfter(to));
        runs.sort(Comparator.comparing(FixedAssetDtos.Run::period));
        List<List<String>> rows = new ArrayList<>();
        for (var r : runs) rows.add(List.of(d(r.period()), s(r.journalNumber()), s(r.journalStatus()), n(r.total()), String.valueOf(r.assets())));
        return new Table("07_depreciation_runs.csv", "Depreciation runs with a period in the period, and the journal each posted",
                List.of("period", "journal_number", "journal_status", "total_charge", "assets"), rows);
    }

    private Table fixedAssetReconciliation(TenantId tenantId) {
        List<FixedAssetDtos.ReconRow> recon = new ArrayList<>(assets.reconciliation(tenantId));
        recon.sort(Comparator.comparing(FixedAssetDtos.ReconRow::code, BY_TEXT));
        List<List<String>> rows = new ArrayList<>();
        for (var r : recon) rows.add(List.of(s(r.code()), s(r.name()), s(r.role()), n(r.ledger()), n(r.register()), n(r.difference())));
        return new Table("08_fixed_asset_reconciliation.csv", "Fixed asset register against the ledger accounts, as at the day the pack was built",
                List.of("account_code", "account_name", "role", "ledger", "register", "difference"), rows);
    }

    static Table vat(Vat201Response v) {
        return new Table("09_vat201.csv", "VAT201 totals for the period",
                List.of("from", "to", "invoice_count", "total_sales", "output_vat", "input_vat", "net_vat_payable"),
                List.of(List.of(d(v.from()), d(v.to()), String.valueOf(v.invoiceCount()), n(v.totalSales()), n(v.outputVat()), n(v.inputVat()), n(v.netVatPayable()))));
    }

    private static RunInfo info(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RunInfo(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getDate(3).toLocalDate(), rs.getDate(4).toLocalDate(),
                rs.getObject(5, UUID.class), rs.getTimestamp(6).toInstant(), rs.getString(7), rs.getInt(8));
    }

    private static String missing(UUID id) { throw new ResourceNotFoundException("AuditPackRun", id.toString()); }
}
