package za.co.handyflow.platform.accounting.application.internal;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.FixedAssetRules.Charge;
import za.co.handyflow.platform.accounting.application.internal.FixedAssetRules.JLine;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.FixedAssetDtos.*;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Fixed-asset register, monthly depreciation and disposals (finance F25). JDBC like the other finance services.
 * Depreciation and disposals are made as DRAFT journals, so a different person posts them (finance F2). The register
 * counts a run as soon as its journal exists and drops it again if that draft is discarded.
 */
@Service
public class FixedAssetService {

    /** A depreciation line counts while its journal is a live draft or posted. */
    private static final String VALID_JOURNAL = "j.status IN ('DRAFT', 'POSTED') AND j.deleted_at IS NULL";
    private static final String ACCUMULATED_SQL = """
            COALESCE((SELECT SUM(dl.amount) FROM acc_depreciation_lines dl
                        JOIN acc_depreciation_runs r ON r.id = dl.run_id
                        JOIN acc_journal_entries j ON j.id = r.journal_entry_id
                       WHERE dl.asset_id = a.id AND %s), 0)""".formatted(VALID_JOURNAL);
    private static final String ASSET_SELECT = """
            SELECT a.id, a.asset_number, a.name, a.description, a.serial_number, a.location,
                   a.cost_account_id, a.accumulated_account_id, a.expense_account_id, a.cost_centre_id,
                   a.acquired_on, a.cost, a.residual_value, a.method, a.life_months, a.rate_percent,
                   a.status, a.disposed_on, a.disposal_proceeds, %s
              FROM acc_fixed_assets a WHERE a.tenant_id = ?""".formatted(ACCUMULATED_SQL);

    private final JdbcTemplate jdbc;
    private final AccountingService accounting;

    public FixedAssetService(JdbcTemplate jdbc, AccountingService accounting) {
        this.jdbc = jdbc;
        this.accounting = accounting;
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "FIXED_ASSET_REFUSED"); }
    private static HandyFlowException missing() { return new HandyFlowException("Asset not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }

    private static Asset map(java.sql.ResultSet rs) throws java.sql.SQLException {
        BigDecimal cost = rs.getBigDecimal(12), acc = rs.getBigDecimal(20);
        return new Asset(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7, UUID.class), rs.getObject(8, UUID.class), rs.getObject(9, UUID.class), rs.getObject(10, UUID.class),
                rs.getDate(11).toLocalDate(), cost, rs.getBigDecimal(13), rs.getString(14), (Integer) rs.getObject(15), rs.getBigDecimal(16),
                rs.getString(17), rs.getDate(18) == null ? null : rs.getDate(18).toLocalDate(), rs.getBigDecimal(19), acc, cost.subtract(acc));
    }

    @Transactional(readOnly = true)
    public List<Asset> list(TenantId tenantId) {
        return jdbc.query(ASSET_SELECT + " ORDER BY a.asset_number", (rs, n) -> map(rs), tenantId.getValue());
    }

    private Asset one(TenantId tenantId, UUID id) {
        List<Asset> r = jdbc.query(ASSET_SELECT + " AND a.id = ?", (rs, n) -> map(rs), tenantId.getValue(), id);
        if (r.isEmpty()) throw missing();
        return r.get(0);
    }

    private void requireAccounts(TenantId tenantId, Save req) {
        Map<UUID, String> types = new HashMap<>();
        List<UUID> ids = Arrays.asList(req.costAccountId(), req.accumulatedAccountId(), req.expenseAccountId());
        if (ids.contains(null)) throw bad("Choose the cost, accumulated depreciation and depreciation expense accounts.");
        jdbc.query("SELECT id, account_type FROM acc_accounts WHERE tenant_id = ? AND id IN (?, ?, ?)",
                rs -> { types.put(rs.getObject(1, UUID.class), rs.getString(2)); },
                tenantId.getValue(), ids.get(0), ids.get(1), ids.get(2));
        if (!"ASSET".equals(types.get(req.costAccountId()))) throw bad("The cost account must be an asset account.");
        if (!"ASSET".equals(types.get(req.accumulatedAccountId()))) throw bad("The accumulated depreciation account must be an asset account.");
        if (!"EXPENSE".equals(types.get(req.expenseAccountId()))) throw bad("The depreciation expense account must be an expense account.");
        if (req.costAccountId().equals(req.accumulatedAccountId())) throw bad("Cost and accumulated depreciation need different accounts.");
    }

    private void requireCentre(TenantId tenantId, UUID centreId) {
        if (centreId == null) return;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM acc_cost_centres WHERE id = ? AND tenant_id = ? AND active = TRUE", Integer.class, centreId, tenantId.getValue());
        if (n == null || n == 0) throw bad("That cost centre does not exist or is switched off.");
    }

    @Transactional
    public Asset create(TenantId tenantId, Save req, UUID by) {
        String problem = FixedAssetRules.saveProblem(req.name(), req.acquiredOn(), req.cost(), req.residualValue(), req.method(),
                req.lifeMonths(), req.ratePercent(), LocalDate.now());
        if (problem != null) throw bad(problem);
        requireAccounts(tenantId, req);
        requireCentre(tenantId, req.costCentreId());
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(asset_number FROM 4) AS INT)), 0) + 1 FROM acc_fixed_assets
                 WHERE tenant_id = ? AND asset_number ~ '^FA-[0-9]+$'""", Integer.class, tenantId.getValue());
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO acc_fixed_assets (id, tenant_id, asset_number, name, description, serial_number, location, cost_account_id,
                        accumulated_account_id, expense_account_id, cost_centre_id, acquired_on, cost, residual_value, method, life_months, rate_percent, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id, tenantId.getValue(), String.format("FA-%04d", next), req.name().trim(), blank(req.description()), blank(req.serialNumber()), blank(req.location()),
                    req.costAccountId(), req.accumulatedAccountId(), req.expenseAccountId(), req.costCentreId(), Date.valueOf(req.acquiredOn()),
                    req.cost(), req.residualValue(), req.method(),
                    FixedAssetRules.STRAIGHT_LINE.equals(req.method()) ? req.lifeMonths() : null,
                    FixedAssetRules.REDUCING_BALANCE.equals(req.method()) ? req.ratePercent() : null, by);
        } catch (DuplicateKeyException e) {
            throw bad("Another asset was added at the same moment. Try again.");
        }
        return one(tenantId, id);
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    @Transactional
    public Asset update(TenantId tenantId, UUID id, Save req) {
        Asset cur = one(tenantId, id);
        if (!"ACTIVE".equals(cur.status())) throw bad("A disposed asset cannot be changed.");
        String problem = FixedAssetRules.saveProblem(req.name(), req.acquiredOn(), req.cost(), req.residualValue(), req.method(),
                req.lifeMonths(), req.ratePercent(), LocalDate.now());
        if (problem != null) throw bad(problem);
        requireAccounts(tenantId, req);
        requireCentre(tenantId, req.costCentreId());
        if (cur.accumulated().signum() > 0) {
            boolean same = cur.cost().compareTo(req.cost()) == 0 && cur.residualValue().compareTo(req.residualValue()) == 0
                    && cur.acquiredOn().equals(req.acquiredOn()) && cur.method().equals(req.method())
                    && Objects.equals(cur.lifeMonths(), FixedAssetRules.STRAIGHT_LINE.equals(req.method()) ? req.lifeMonths() : null)
                    && (cur.ratePercent() == null ? req.ratePercent() == null || !FixedAssetRules.REDUCING_BALANCE.equals(req.method())
                        : req.ratePercent() != null && cur.ratePercent().compareTo(req.ratePercent()) == 0)
                    && cur.costAccountId().equals(req.costAccountId()) && cur.accumulatedAccountId().equals(req.accumulatedAccountId())
                    && cur.expenseAccountId().equals(req.expenseAccountId());
            if (!same) throw bad("Cost, dates, method and accounts are locked once depreciation has been recorded. You can still change the name, description, serial number, location and cost centre.");
        }
        jdbc.update("""
                UPDATE acc_fixed_assets SET name = ?, description = ?, serial_number = ?, location = ?, cost_centre_id = ?,
                       cost_account_id = ?, accumulated_account_id = ?, expense_account_id = ?, acquired_on = ?, cost = ?, residual_value = ?,
                       method = ?, life_months = ?, rate_percent = ?
                 WHERE id = ? AND tenant_id = ?""",
                req.name().trim(), blank(req.description()), blank(req.serialNumber()), blank(req.location()), req.costCentreId(),
                req.costAccountId(), req.accumulatedAccountId(), req.expenseAccountId(), Date.valueOf(req.acquiredOn()), req.cost(), req.residualValue(),
                req.method(), FixedAssetRules.STRAIGHT_LINE.equals(req.method()) ? req.lifeMonths() : null,
                FixedAssetRules.REDUCING_BALANCE.equals(req.method()) ? req.ratePercent() : null, id, tenantId.getValue());
        return one(tenantId, id);
    }

    // ── depreciation ─────────────────────────────────────────────────────────

    /** Removes runs whose journal was discarded or reversed, so the month can be run again. */
    private void purgeVoidRuns(TenantId tenantId) {
        jdbc.update("""
                DELETE FROM acc_depreciation_lines WHERE tenant_id = ? AND run_id IN (
                    SELECT r.id FROM acc_depreciation_runs r JOIN acc_journal_entries j ON j.id = r.journal_entry_id
                     WHERE r.tenant_id = ? AND NOT (%s))""".formatted(VALID_JOURNAL), tenantId.getValue(), tenantId.getValue());
        jdbc.update("""
                DELETE FROM acc_depreciation_runs WHERE tenant_id = ? AND id IN (
                    SELECT r.id FROM acc_depreciation_runs r JOIN acc_journal_entries j ON j.id = r.journal_entry_id
                     WHERE r.tenant_id = ? AND NOT (%s))""".formatted(VALID_JOURNAL), tenantId.getValue(), tenantId.getValue());
    }

    private LocalDate lastRunPeriod(TenantId tenantId) {
        Date d = jdbc.queryForObject("""
                SELECT MAX(r.period_start) FROM acc_depreciation_runs r JOIN acc_journal_entries j ON j.id = r.journal_entry_id
                 WHERE r.tenant_id = ? AND %s""".formatted(VALID_JOURNAL), Date.class, tenantId.getValue());
        return d == null ? null : d.toLocalDate();
    }

    private record Planned(Asset asset, BigDecimal charge) {}

    private List<Planned> plan(TenantId tenantId, LocalDate period) {
        List<Planned> out = new ArrayList<>();
        LocalDate end = FixedAssetRules.lastOfMonth(period);
        for (Asset a : list(tenantId)) {
            if (!"ACTIVE".equals(a.status()) || a.acquiredOn().isAfter(end)) continue;
            BigDecimal charge = FixedAssetRules.chargeFor(a.method(), a.cost(), a.residualValue(), a.lifeMonths(), a.ratePercent(), a.acquiredOn(), period, a.accumulated());
            if (charge.signum() > 0) out.add(new Planned(a, charge));
        }
        return out;
    }

    @Transactional
    public Preview preview(TenantId tenantId, LocalDate period) {
        purgeVoidRuns(tenantId);
        LocalDate last = lastRunPeriod(tenantId);
        LocalDate earliest = list(tenantId).stream().filter(a -> "ACTIVE".equals(a.status())).map(Asset::acquiredOn).min(Comparator.naturalOrder()).orElse(null);
        LocalDate next = FixedAssetRules.nextPeriod(last, earliest, LocalDate.now());
        LocalDate chosen = period != null ? period : next;
        if (chosen == null) return new Preview(null, last, null, List.of(), BigDecimal.ZERO, "Depreciation is up to date.");
        String problem = FixedAssetRules.runProblem(chosen, LocalDate.now(), last);
        List<Planned> planned = problem == null ? plan(tenantId, chosen) : List.of();
        List<PreviewLine> lines = planned.stream().map(p -> new PreviewLine(p.asset().id(), p.asset().assetNumber(), p.asset().name(), p.charge(),
                p.asset().accumulated().add(p.charge()), p.asset().cost().subtract(p.asset().accumulated()).subtract(p.charge()))).toList();
        BigDecimal total = lines.stream().map(PreviewLine::charge).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (problem == null && lines.isEmpty()) problem = "Nothing to depreciate for that month.";
        return new Preview(chosen, last, next, lines, total, problem);
    }

    @Transactional
    public Run run(TenantId tenantId, LocalDate period, UUID by) {
        purgeVoidRuns(tenantId);
        String problem = FixedAssetRules.runProblem(period, LocalDate.now(), lastRunPeriod(tenantId));
        if (problem != null) throw bad(problem);
        List<Planned> planned = plan(tenantId, period);
        if (planned.isEmpty()) throw bad("Nothing to depreciate for that month.");
        String label = period.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + period.getYear();
        List<Charge> charges = planned.stream().map(p -> new Charge(p.asset().id(), p.asset().assetNumber(), p.asset().expenseAccountId(),
                p.asset().accumulatedAccountId(), p.asset().costCentreId(), p.charge())).toList();
        BigDecimal total = charges.stream().map(Charge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        JournalEntryResponse j = accounting.createJournalEntry(tenantId, request(FixedAssetRules.lastOfMonth(period), "Depreciation " + label,
                String.format("DEP-%d-%02d", period.getYear(), period.getMonthValue()), "DEPRECIATION", FixedAssetRules.runLines(charges, label)), by);
        UUID runId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO acc_depreciation_runs (id, tenant_id, period_start, journal_entry_id, total, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                    runId, tenantId.getValue(), Date.valueOf(period), j.id(), total, by);
        } catch (DuplicateKeyException e) {
            throw bad("Depreciation has already been run for that month.");
        }
        for (Charge c : charges)
            jdbc.update("INSERT INTO acc_depreciation_lines (tenant_id, run_id, asset_id, amount) VALUES (?, ?, ?, ?)", tenantId.getValue(), runId, c.assetId(), c.amount());
        return new Run(runId, period, j.id(), j.entryNumber(), j.status(), total, charges.size(), java.time.Instant.now());
    }

    @Transactional
    public List<Run> runs(TenantId tenantId) {
        purgeVoidRuns(tenantId);
        return jdbc.query("""
                SELECT r.id, r.period_start, r.journal_entry_id, j.entry_number, j.status, r.total,
                       (SELECT COUNT(*) FROM acc_depreciation_lines dl WHERE dl.run_id = r.id), r.created_at
                  FROM acc_depreciation_runs r JOIN acc_journal_entries j ON j.id = r.journal_entry_id
                 WHERE r.tenant_id = ? ORDER BY r.period_start DESC""",
                (rs, n) -> new Run(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                        rs.getBigDecimal(6), rs.getInt(7), rs.getTimestamp(8).toInstant()),
                tenantId.getValue());
    }

    private static CreateJournalEntryRequest request(LocalDate date, String description, String reference, String type, List<JLine> lines) {
        return new CreateJournalEntryRequest(date, description, reference, type, lines.stream()
                .map(l -> new CreateJournalEntryRequest.JournalLineRequest(l.accountId(), l.description(), l.debit(), l.credit(), l.costCentreId())).toList());
    }

    // ── disposal ─────────────────────────────────────────────────────────────

    @Transactional
    public Disposal dispose(TenantId tenantId, UUID id, DisposeRequest req, UUID by) {
        purgeVoidRuns(tenantId);
        Asset a = one(tenantId, id);
        BigDecimal proceeds = req.proceeds() == null ? BigDecimal.ZERO : req.proceeds();
        // Months before the disposal month with nothing left to charge need no run; the check only matters when something is still owed.
        LocalDate effectiveLast = lastRunPeriod(tenantId);
        if (req.disposedOn() != null) {
            LocalDate needed = FixedAssetRules.firstOfMonth(req.disposedOn()).minusMonths(1);
            BigDecimal owed = FixedAssetRules.chargeFor(a.method(), a.cost(), a.residualValue(), a.lifeMonths(), a.ratePercent(), a.acquiredOn(), needed, a.accumulated());
            if (owed.signum() == 0) effectiveLast = needed;
        }
        String problem = FixedAssetRules.disposalProblem(a.status(), a.acquiredOn(), req.disposedOn(), proceeds, req.proceedsAccountId(),
                req.gainLossAccountId(), effectiveLast, LocalDate.now());
        if (problem != null) throw bad(problem);
        requireDisposalAccounts(tenantId, req, proceeds);
        String label = a.assetNumber();
        var lines = FixedAssetRules.disposalLines(a.costAccountId(), a.accumulatedAccountId(), req.proceedsAccountId(), req.gainLossAccountId(),
                a.cost(), a.accumulated(), proceeds, a.costCentreId(), label);
        JournalEntryResponse j = accounting.createJournalEntry(tenantId, request(req.disposedOn(), "Disposal of " + a.assetNumber() + " " + a.name(),
                "DISP-" + a.assetNumber(), "ADJUSTMENT", lines), by);
        int changed = jdbc.update("""
                UPDATE acc_fixed_assets SET status = 'DISPOSED', disposed_on = ?, disposal_proceeds = ?, disposal_journal_id = ?
                 WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'""", Date.valueOf(req.disposedOn()), proceeds, j.id(), id, tenantId.getValue());
        if (changed == 0) throw bad("This asset has already been disposed of.");
        return new Disposal(id, j.id(), j.entryNumber(), a.cost().subtract(a.accumulated()), FixedAssetRules.gainOrLoss(proceeds, a.cost(), a.accumulated()));
    }

    private void requireDisposalAccounts(TenantId tenantId, DisposeRequest req, BigDecimal proceeds) {
        Map<UUID, String> types = new HashMap<>();
        jdbc.query("SELECT id, account_type FROM acc_accounts WHERE tenant_id = ? AND id IN (?, ?)",
                rs -> { types.put(rs.getObject(1, UUID.class), rs.getString(2)); },
                tenantId.getValue(), req.gainLossAccountId(), req.proceedsAccountId() == null ? req.gainLossAccountId() : req.proceedsAccountId());
        String gl = types.get(req.gainLossAccountId());
        if (gl == null || !(gl.equals("INCOME") || gl.equals("EXPENSE"))) throw bad("The gain or loss account must be an income or expense account.");
        if (proceeds.signum() > 0 && !types.containsKey(req.proceedsAccountId())) throw bad("The proceeds account does not exist.");
    }

    // ── register against the ledger ──────────────────────────────────────────

    /** For each cost and accumulated-depreciation account used by active assets: what the register says and what the ledger holds. */
    @Transactional
    public List<ReconRow> reconciliation(TenantId tenantId) {
        purgeVoidRuns(tenantId);
        List<Asset> active = list(tenantId).stream().filter(a -> "ACTIVE".equals(a.status())).toList();
        Map<UUID, BigDecimal> cost = new LinkedHashMap<>(), acc = new LinkedHashMap<>();
        for (Asset a : active) {
            cost.merge(a.costAccountId(), a.cost(), BigDecimal::add);
            acc.merge(a.accumulatedAccountId(), a.accumulated(), BigDecimal::add);
        }
        List<ReconRow> rows = new ArrayList<>();
        cost.forEach((id, reg) -> rows.add(row(tenantId, id, "COST", reg, false)));
        acc.forEach((id, reg) -> rows.add(row(tenantId, id, "ACCUMULATED", reg, true)));
        return rows;
    }

    private ReconRow row(TenantId tenantId, UUID accountId, String role, BigDecimal register, boolean creditBalance) {
        BigDecimal net = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit_amount - l.credit_amount), 0)
                  FROM acc_journal_lines l JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                 WHERE l.tenant_id = ? AND e.tenant_id = ? AND l.account_id = ? AND e.status IN ('POSTED', 'REVERSED') AND e.deleted_at IS NULL""",
                BigDecimal.class, tenantId.getValue(), tenantId.getValue(), accountId);
        BigDecimal ledger = creditBalance ? net.negate() : net;
        List<String> info = jdbc.query("SELECT account_code, account_name FROM acc_accounts WHERE id = ? AND tenant_id = ?",
                (rs, n) -> rs.getString(1) + "|" + rs.getString(2), accountId, tenantId.getValue());
        String[] p = info.isEmpty() ? new String[]{"?", "?"} : info.get(0).split("\\|", 2);
        return new ReconRow(accountId, p[0], p[1], role, ledger, register, FixedAssetRules.difference(ledger, register));
    }
}
