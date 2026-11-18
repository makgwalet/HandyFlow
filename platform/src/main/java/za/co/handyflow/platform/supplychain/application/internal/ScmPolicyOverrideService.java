package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.supplychain.dto.PolicyRefinementDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Maintains the per-category price tolerance, the per-supplier VAT recoverability and the exchange rates. Every change is
 * audited; a change that moves money (supplier VAT, a rate) restates the existing budget commitments in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class ScmPolicyOverrideService {

    private final JdbcTemplate jdbc;
    private final ProcurementPolicy policy;

    @Transactional(readOnly = true)
    public Overview overview(UUID tid) {
        ProcurementPolicy.Policy p = policy.forTenant(tid);
        List<CategoryTolerance> tolerances = jdbc.query("""
                SELECT t.category_id, c.name, t.tolerance_pct, t.updated_by_name, t.updated_at
                  FROM sc_category_price_tolerance t JOIN catalogue_categories c ON c.id = t.category_id
                 WHERE t.tenant_id = ? ORDER BY lower(c.name)""",
                (rs, n) -> new CategoryTolerance((UUID) rs.getObject(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant()), tid);
        List<CategoryOption> categories = jdbc.query("SELECT id, name FROM catalogue_categories WHERE tenant_id = ? ORDER BY lower(name)",
                (rs, n) -> new CategoryOption((UUID) rs.getObject(1), rs.getString(2)), tid);
        List<SupplierVat> vat = jdbc.query("""
                SELECT v.supplier_id, s.name, v.vat_recoverable_pct, v.note, v.updated_by_name, v.updated_at
                  FROM sc_supplier_vat v JOIN sc_suppliers s ON s.id = v.supplier_id
                 WHERE v.tenant_id = ? ORDER BY lower(s.name)""",
                (rs, n) -> new SupplierVat((UUID) rs.getObject(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant()), tid);
        List<SupplierOption> suppliers = jdbc.query("SELECT id, name FROM sc_suppliers WHERE tenant_id = ? ORDER BY lower(name) LIMIT 1000",
                (rs, n) -> new SupplierOption((UUID) rs.getObject(1), rs.getString(2)), tid);
        List<ExchangeRate> rates = jdbc.query("""
                SELECT id, currency, rate, effective_from, created_by_name, created_at FROM sc_exchange_rates
                 WHERE tenant_id = ? ORDER BY currency, effective_from DESC LIMIT 500""",
                (rs, n) -> new ExchangeRate((UUID) rs.getObject(1), rs.getString(2), rs.getBigDecimal(3),
                        rs.getDate(4).toLocalDate(), rs.getString(5), rs.getTimestamp(6).toInstant()), tid);
        // Currencies used by POs that are not the base currency and have no rate at all: their commitments cannot be measured.
        List<String> missing = jdbc.query("""
                SELECT DISTINCT upper(po.currency) FROM sc_purchase_orders po
                 WHERE po.tenant_id = ? AND upper(po.currency) <> ?
                   AND NOT EXISTS (SELECT 1 FROM sc_exchange_rates r WHERE r.tenant_id = po.tenant_id AND r.currency = upper(po.currency))
                 ORDER BY 1""", (rs, n) -> rs.getString(1), tid, p.baseCurrency());
        return new Overview(p.baseCurrency(), p.priceIncreaseTolerancePct(), p.vatRecoverablePct(), tolerances, categories, vat,
                suppliers, rates, missing);
    }

    // ── Category tolerance ────────────────────────────────────────────────────

    @Transactional
    public Change setCategoryTolerance(UUID tid, UUID categoryId, BigDecimal pct, UUID userId, String userName) {
        String problem = ScmPolicyRules.percentProblem(pct, "The tolerance");
        if (problem != null) throw bad(problem, "BAD_TOLERANCE");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM catalogue_categories WHERE tenant_id = ? AND id = ?", Integer.class, tid, categoryId);
        if (exists == null || exists == 0) throw new HandyFlowException("Category not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        BigDecimal before = jdbc.query("SELECT tolerance_pct FROM sc_category_price_tolerance WHERE tenant_id = ? AND category_id = ?",
                (rs, n) -> rs.getBigDecimal(1), tid, categoryId).stream().findFirst().orElse(null);
        jdbc.update("""
                INSERT INTO sc_category_price_tolerance (tenant_id, category_id, tolerance_pct, updated_by, updated_by_name, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id, category_id) DO UPDATE SET tolerance_pct = EXCLUDED.tolerance_pct,
                    updated_by = EXCLUDED.updated_by, updated_by_name = EXCLUDED.updated_by_name, updated_at = now()""",
                tid, categoryId, pct, userId, userName);
        audit(tid, userId, userName, "SCM_CATEGORY_TOLERANCE_CHANGED", categoryId,
                before == null ? "{}" : "{\"tolerancePct\":" + before + "}", "{\"tolerancePct\":" + pct + "}");
        return new Change(overview(tid), 0, 0);
    }

    @Transactional
    public Change clearCategoryTolerance(UUID tid, UUID categoryId, UUID userId, String userName) {
        BigDecimal before = jdbc.query("SELECT tolerance_pct FROM sc_category_price_tolerance WHERE tenant_id = ? AND category_id = ?",
                (rs, n) -> rs.getBigDecimal(1), tid, categoryId).stream().findFirst().orElse(null);
        if (before != null) {
            jdbc.update("DELETE FROM sc_category_price_tolerance WHERE tenant_id = ? AND category_id = ?", tid, categoryId);
            audit(tid, userId, userName, "SCM_CATEGORY_TOLERANCE_CLEARED", categoryId, "{\"tolerancePct\":" + before + "}", "{}");
        }
        return new Change(overview(tid), 0, 0);
    }

    // ── Supplier VAT ──────────────────────────────────────────────────────────

    @Transactional
    public Change setSupplierVat(UUID tid, UUID supplierId, BigDecimal pct, String note, UUID userId, String userName) {
        String problem = ScmPolicyRules.percentProblem(pct, "Recoverable VAT");
        if (problem != null) throw bad(problem, "BAD_VAT_PCT");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sc_suppliers WHERE tenant_id = ? AND id = ?", Integer.class, tid, supplierId);
        if (exists == null || exists == 0) throw new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        String cleanNote = note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(255, note.trim().length()));
        BigDecimal before = jdbc.query("SELECT vat_recoverable_pct FROM sc_supplier_vat WHERE tenant_id = ? AND supplier_id = ?",
                (rs, n) -> rs.getBigDecimal(1), tid, supplierId).stream().findFirst().orElse(null);
        jdbc.update("""
                INSERT INTO sc_supplier_vat (tenant_id, supplier_id, vat_recoverable_pct, note, updated_by, updated_by_name, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (tenant_id, supplier_id) DO UPDATE SET vat_recoverable_pct = EXCLUDED.vat_recoverable_pct,
                    note = EXCLUDED.note, updated_by = EXCLUDED.updated_by, updated_by_name = EXCLUDED.updated_by_name, updated_at = now()""",
                tid, supplierId, pct, cleanNote, userId, userName);
        int[] r = policy.restateCommitments(tid, supplierId);
        audit(tid, userId, userName, "SCM_SUPPLIER_VAT_CHANGED", supplierId,
                before == null ? "{}" : "{\"vatRecoverablePct\":" + before + "}",
                "{\"vatRecoverablePct\":" + pct + ",\"commitmentsRestated\":" + r[0] + ",\"commitmentsSkipped\":" + r[1] + "}");
        return new Change(overview(tid), r[0], r[1]);
    }

    @Transactional
    public Change clearSupplierVat(UUID tid, UUID supplierId, UUID userId, String userName) {
        BigDecimal before = jdbc.query("SELECT vat_recoverable_pct FROM sc_supplier_vat WHERE tenant_id = ? AND supplier_id = ?",
                (rs, n) -> rs.getBigDecimal(1), tid, supplierId).stream().findFirst().orElse(null);
        int[] r = {0, 0};
        if (before != null) {
            jdbc.update("DELETE FROM sc_supplier_vat WHERE tenant_id = ? AND supplier_id = ?", tid, supplierId);
            r = policy.restateCommitments(tid, supplierId);
            audit(tid, userId, userName, "SCM_SUPPLIER_VAT_CLEARED", supplierId, "{\"vatRecoverablePct\":" + before + "}",
                    "{\"commitmentsRestated\":" + r[0] + ",\"commitmentsSkipped\":" + r[1] + "}");
        }
        return new Change(overview(tid), r[0], r[1]);
    }

    // ── Exchange rates ────────────────────────────────────────────────────────

    @Transactional
    public Change addRate(UUID tid, String currency, BigDecimal rate, LocalDate effectiveFrom, UUID userId, String userName) {
        String base = policy.forTenant(tid).baseCurrency();
        String problem = ScmPolicyRules.rateProblem(currency, base, rate);
        if (problem != null) throw bad(problem, "BAD_RATE");
        if (effectiveFrom == null) throw bad("Say the date the rate applies from", "BAD_RATE");
        if (effectiveFrom.isAfter(LocalDate.now().plusDays(1)))
            throw bad("A rate cannot start in the future", "BAD_RATE");
        String cur = currency.trim().toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO sc_exchange_rates (id, tenant_id, currency, rate, effective_from, created_by, created_by_name)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, currency, effective_from) DO UPDATE SET rate = EXCLUDED.rate,
                    created_by = EXCLUDED.created_by, created_by_name = EXCLUDED.created_by_name""",
                UUID.randomUUID(), tid, cur, rate, java.sql.Date.valueOf(effectiveFrom), userId, userName);
        int[] r = policy.restateCommitments(tid, null);
        audit(tid, userId, userName, "SCM_EXCHANGE_RATE_SET", tid, "{}", "{\"currency\":\"" + cur + "\",\"rate\":" + rate
                + ",\"effectiveFrom\":\"" + effectiveFrom + "\",\"commitmentsRestated\":" + r[0] + ",\"commitmentsSkipped\":" + r[1] + "}");
        return new Change(overview(tid), r[0], r[1]);
    }

    @Transactional
    public Change deleteRate(UUID tid, UUID rateId, UUID userId, String userName) {
        List<String> found = jdbc.query("SELECT currency || ' ' || rate || ' from ' || effective_from FROM sc_exchange_rates WHERE tenant_id = ? AND id = ?",
                (rs, n) -> rs.getString(1), tid, rateId);
        if (found.isEmpty()) throw new HandyFlowException("Rate not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        jdbc.update("DELETE FROM sc_exchange_rates WHERE tenant_id = ? AND id = ?", tid, rateId);
        int[] r = policy.restateCommitments(tid, null);
        audit(tid, userId, userName, "SCM_EXCHANGE_RATE_DELETED", rateId, "{\"rate\":\"" + found.get(0) + "\"}",
                "{\"commitmentsRestated\":" + r[0] + ",\"commitmentsSkipped\":" + r[1] + "}");
        return new Change(overview(tid), r[0], r[1]);
    }

    private void audit(UUID tid, UUID userId, String userName, String action, UUID itemId, String beforeJson, String afterJson) {
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, 'STAFF', ?, ?, ?, 'ScProcurementPolicy', ?, ?::jsonb, ?::jsonb)""",
                tid, userId, userName, action, itemId, beforeJson, afterJson);
    }

    private static HandyFlowException bad(String message, String code) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }
}
