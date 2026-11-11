package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.application.internal.TierPricing.ChangeKind;
import za.co.handyflow.platform.admin.application.internal.TierPricing.Period;
import za.co.handyflow.platform.admin.application.internal.TierPricing.Price;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Tier price book and tenant tier subscriptions for any module. A tier price only reaches an invoice once it has
 * been approved; a subscription without a price keeps billing at the flat catalogue price.
 */
@Service
@RequiredArgsConstructor
public class ModuleTierService {

    private final JdbcTemplate jdbc;

    /** What one module bills for a tenant in an invoice month. */
    public record Charge(UUID subscriptionId, String tierCode, String tierName, Period period,
                         BigDecimal amount, boolean prepaid, BigDecimal proration, UUID prorationSubscriptionId) {}

    // ── Price book ───────────────────────────────────────────────────────────────

    public List<Map<String, Object>> tiers(String moduleKey) {
        return jdbc.queryForList("SELECT tier_code, name, sort_order, active FROM module_tiers WHERE module_key = ? ORDER BY sort_order", moduleKey);
    }

    public List<Map<String, Object>> prices(String moduleKey) {
        return jdbc.queryForList("""
            SELECT id, tier_code, billing_period, amount, currency, effective_from, approved, approved_by, approved_at, created_at, note
            FROM module_tier_prices WHERE module_key = ? ORDER BY tier_code, billing_period, effective_from DESC
            """, moduleKey);
    }

    /** Records a proposed price. It is not used for billing until approved. amount null = negotiated per tenant. */
    @Transactional
    public UUID proposePrice(String moduleKey, String tierCode, Period period, BigDecimal amount,
                             LocalDate effectiveFrom, UUID adminId, String note) {
        requireTier(moduleKey, tierCode);
        if (amount != null && amount.signum() < 0) throw bad("Price cannot be negative", "PRICE_NEGATIVE");
        if (effectiveFrom == null) throw bad("An effective date is required", "PRICE_DATE_REQUIRED");
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO module_tier_prices (id, module_key, tier_code, billing_period, amount, effective_from, created_by, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, moduleKey, tierCode, period.name(), amount, effectiveFrom, adminId, trim(note));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw bad("A price for this tier and period already starts on that date", "PRICE_EXISTS");
        }
        return id;
    }

    @Transactional
    public void approvePrice(UUID priceId, UUID adminId) {
        if (adminId == null) throw bad("The approving administrator could not be identified", "APPROVER_UNKNOWN");
        int n = jdbc.update("""
            UPDATE module_tier_prices SET approved = TRUE, approved_by = ?, approved_at = now()
            WHERE id = ? AND approved = FALSE
            """, adminId, priceId);
        if (n == 0) throw bad("Price not found or already approved", "PRICE_NOT_PENDING");
    }

    /** An approved price is history; only an unapproved proposal can be withdrawn. */
    @Transactional
    public void withdrawPrice(UUID priceId) {
        int n = jdbc.update("DELETE FROM module_tier_prices WHERE id = ? AND approved = FALSE", priceId);
        if (n == 0) throw bad("Only an unapproved price can be withdrawn", "PRICE_APPROVED");
    }

    // ── Tenant subscriptions ─────────────────────────────────────────────────────

    public List<Map<String, Object>> history(UUID tenantId, String moduleKey) {
        return jdbc.queryForList("""
            SELECT s.id, s.tier_code, t.name AS tier_name, s.billing_period, s.effective_from, s.effective_to, s.price_snapshot,
                   s.negotiated, s.change_kind, s.proration_amount, s.proration_invoiced, s.cancelled, s.created_at, s.note
            FROM tenant_module_subscriptions s JOIN module_tiers t ON t.module_key = s.module_key AND t.tier_code = s.tier_code
            WHERE s.tenant_id = ? AND s.module_key = ? ORDER BY s.effective_from DESC, s.created_at DESC
            """, tenantId, moduleKey);
    }

    /**
     * Puts a tenant on a tier from a date. Upgrades start on the requested date and bill a prorated difference once.
     * Downgrades start when the current period ends. With no approved price (and no negotiated amount) the tier is
     * recorded but unpriced, and the module keeps billing at the flat catalogue price.
     */
    @Transactional
    public Map<String, Object> assign(UUID tenantId, String moduleKey, String tierCode, Period period,
                                      LocalDate requestedFrom, BigDecimal negotiatedAmount, UUID adminId, String note) {
        int newSort = requireTier(moduleKey, tierCode);
        if (requestedFrom == null) throw bad("An effective date is required", "DATE_REQUIRED");
        if (negotiatedAmount != null && negotiatedAmount.signum() < 0) throw bad("Price cannot be negative", "PRICE_NEGATIVE");
        Integer active = jdbc.queryForObject("""
            SELECT count(*) FROM tenant_modules WHERE tenant_id = ? AND module_key = ? AND status IN ('ACTIVE','TRIAL')
            """, Integer.class, tenantId, moduleKey);
        if (active == null || active == 0) throw bad("The tenant does not have this module active", "MODULE_NOT_ACTIVE");

        Map<String, Object> current = openRow(tenantId, moduleKey);
        Integer currentSort = null;
        BigDecimal currentAmount = null;
        LocalDate currentStart = null;
        LocalDate anchor = null;
        Period currentPeriod = null;
        if (current != null) {
            currentSort = sortOf(moduleKey, (String) current.get("tier_code"));
            currentAmount = (BigDecimal) current.get("price_snapshot");
            currentStart = ((java.sql.Date) current.get("effective_from")).toLocalDate();
            currentPeriod = Period.valueOf((String) current.get("billing_period"));
            anchor = currentPeriod == Period.ANNUAL ? originOf(tenantId, moduleKey, currentPeriod, currentStart) : currentStart;
        }
        ChangeKind kind = TierPricing.classify(currentSort, newSort);
        if (kind == ChangeKind.SAME_TIER && current != null && currentPeriod == period && negotiatedAmount == null) {
            throw bad("The tenant is already on this tier", "ALREADY_ON_TIER");
        }
        LocalDate from = requestedFrom;
        if (current != null) {
            LocalDate periodEnd = TierPricing.periodEnd(currentPeriod, anchor, requestedFrom);
            from = TierPricing.changeEffective(kind, requestedFrom, periodEnd);
            if (!from.isAfter(currentStart)) throw bad("The change must start after the current subscription began", "DATE_TOO_EARLY");
        }

        UUID priceId = null;
        BigDecimal snapshot = null;
        boolean negotiated = false;
        if (negotiatedAmount != null) {
            snapshot = negotiatedAmount;
            negotiated = true;
        } else {
            Optional<Price> p = TierPricing.effective(priceBook(moduleKey), tierCode, period, from);
            if (p.isPresent() && p.get().amount() != null) {
                snapshot = p.get().amount();
                priceId = priceIdFor(moduleKey, tierCode, period, p.get().effectiveFrom());
            }
        }

        BigDecimal proration = null;
        if (current != null && kind == ChangeKind.UPGRADE && snapshot != null && currentAmount != null && currentPeriod == period) {
            LocalDate ps = TierPricing.periodStart(period, anchor, from);
            LocalDate pe = TierPricing.periodEnd(period, anchor, from);
            BigDecimal amt = TierPricing.prorateUpgrade(currentAmount, snapshot, from, ps, pe);
            if (amt.signum() > 0) proration = amt;
        }

        if (current != null) {
            jdbc.update("UPDATE tenant_module_subscriptions SET effective_to = ? WHERE id = ?", from, current.get("id"));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO tenant_module_subscriptions
              (id, tenant_id, module_key, tier_code, billing_period, effective_from, price_id, price_snapshot, negotiated,
               change_kind, proration_amount, created_by, note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, tenantId, moduleKey, tierCode, period.name(), from, priceId, snapshot, negotiated,
                kind.name(), proration, adminId, trim(note));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("change", kind.name());
        out.put("effectiveFrom", from);
        out.put("priced", snapshot != null);
        out.put("price", snapshot);
        out.put("prorationAmount", proration);
        if (snapshot == null) out.put("warning", "No approved price for this tier yet. The module keeps billing at the flat catalogue price.");
        return out;
    }

    /** Ends the tenant's subscription on a date (cancellation or expiry); billing falls back to the catalogue price rule. */
    @Transactional
    public void cancel(UUID tenantId, String moduleKey, LocalDate endDate, UUID adminId, String note) {
        Map<String, Object> current = openRow(tenantId, moduleKey);
        if (current == null) throw bad("The tenant has no open subscription for this module", "NO_SUBSCRIPTION");
        LocalDate start = ((java.sql.Date) current.get("effective_from")).toLocalDate();
        if (endDate == null || !endDate.isAfter(start)) throw bad("The end date must be after the subscription start", "DATE_TOO_EARLY");
        jdbc.update("UPDATE tenant_module_subscriptions SET effective_to = ?, cancelled = TRUE, note = COALESCE(?, note) WHERE id = ?",
                endDate, trim(note), current.get("id"));
    }

    // ── Used by invoicing ────────────────────────────────────────────────────────

    /**
     * Priced subscriptions to bill for the invoice month, by module key. Bills the subscription in effect on the first of
     * the month, plus any prorated upgrade adjustment not yet invoiced. Unpriced subscriptions are left out so the flat
     * catalogue price still applies.
     */
    public Map<String, Charge> chargesFor(UUID tenantId, YearMonth month) {
        LocalDate first = month.atDay(1);
        Map<String, Charge> out = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("""
            SELECT s.id, s.module_key, s.tier_code, t.name AS tier_name, s.billing_period, s.effective_from, s.price_snapshot
            FROM tenant_module_subscriptions s JOIN module_tiers t ON t.module_key = s.module_key AND t.tier_code = s.tier_code
            WHERE s.tenant_id = ? AND s.price_snapshot IS NOT NULL AND s.effective_from <= ?
              AND (s.effective_to IS NULL OR s.effective_to > ?)
            """, tenantId, first, first)) {
            Period period = Period.valueOf((String) r.get("billing_period"));
            LocalDate start = ((java.sql.Date) r.get("effective_from")).toLocalDate();
            BigDecimal price = (BigDecimal) r.get("price_snapshot");
            BigDecimal amount = TierPricing.chargeForMonth(period, price, start, month);
            // Annual subscriptions run on the anniversary of the first subscription row, not of each tier change.
            if (period == Period.ANNUAL) {
                LocalDate origin = originOf(tenantId, (String) r.get("module_key"), period, start);
                amount = TierPricing.chargeForMonth(period, price, origin, month);
            }
            boolean prepaid = amount.signum() == 0 && period == Period.ANNUAL;
            out.put((String) r.get("module_key"), new Charge((UUID) r.get("id"), (String) r.get("tier_code"),
                    (String) r.get("tier_name"), period, amount, prepaid, null, null));
        }
        // Prorated upgrade adjustments due on this invoice (effective on or before the end of the invoice month).
        for (Map<String, Object> r : jdbc.queryForList("""
            SELECT id, module_key, proration_amount FROM tenant_module_subscriptions
            WHERE tenant_id = ? AND proration_amount IS NOT NULL AND proration_invoiced = FALSE AND effective_from < ?
            """, tenantId, month.plusMonths(1).atDay(1))) {
            Charge base = out.get((String) r.get("module_key"));
            if (base == null) continue;
            out.put((String) r.get("module_key"), new Charge(base.subscriptionId(), base.tierCode(), base.tierName(), base.period(),
                    base.amount(), base.prepaid(), (BigDecimal) r.get("proration_amount"), (UUID) r.get("id")));
        }
        return out;
    }

    @Transactional
    public void markProrationInvoiced(Collection<UUID> subscriptionIds) {
        for (UUID id : subscriptionIds) jdbc.update("UPDATE tenant_module_subscriptions SET proration_invoiced = TRUE WHERE id = ?", id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private LocalDate originOf(UUID tenantId, String moduleKey, Period period, LocalDate fallback) {
        // Walk back through consecutive annual rows to find where the current annual run began.
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT billing_period, effective_from FROM tenant_module_subscriptions
            WHERE tenant_id = ? AND module_key = ? AND price_snapshot IS NOT NULL AND cancelled = FALSE AND effective_from <= ?
            ORDER BY effective_from DESC
            """, tenantId, moduleKey, fallback);
        LocalDate origin = fallback;
        for (Map<String, Object> r : rows) {
            if (!period.name().equals(r.get("billing_period"))) break;
            origin = ((java.sql.Date) r.get("effective_from")).toLocalDate();
        }
        return origin;
    }

    private List<Price> priceBook(String moduleKey) {
        List<Price> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
            SELECT tier_code, billing_period, amount, effective_from, approved FROM module_tier_prices WHERE module_key = ?
            """, moduleKey)) {
            out.add(new Price((String) r.get("tier_code"), Period.valueOf((String) r.get("billing_period")),
                    (BigDecimal) r.get("amount"), ((java.sql.Date) r.get("effective_from")).toLocalDate(), (Boolean) r.get("approved")));
        }
        return out;
    }

    private UUID priceIdFor(String moduleKey, String tier, Period period, LocalDate from) {
        List<UUID> ids = jdbc.query("""
            SELECT id FROM module_tier_prices WHERE module_key = ? AND tier_code = ? AND billing_period = ? AND effective_from = ?
            """, (rs, i) -> (UUID) rs.getObject(1), moduleKey, tier, period.name(), from);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> openRow(UUID tenantId, String moduleKey) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, tier_code, billing_period, effective_from, price_snapshot FROM tenant_module_subscriptions
            WHERE tenant_id = ? AND module_key = ? AND effective_to IS NULL ORDER BY effective_from DESC LIMIT 1
            """, tenantId, moduleKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int requireTier(String moduleKey, String tierCode) {
        List<Integer> r = jdbc.query("SELECT sort_order FROM module_tiers WHERE module_key = ? AND tier_code = ? AND active",
                (rs, i) -> rs.getInt(1), moduleKey, tierCode);
        if (r.isEmpty()) throw bad("Unknown tier for this module", "TIER_UNKNOWN");
        return r.get(0);
    }

    private int sortOf(String moduleKey, String tierCode) {
        Integer s = jdbc.queryForObject("SELECT sort_order FROM module_tiers WHERE module_key = ? AND tier_code = ?",
                Integer.class, moduleKey, tierCode);
        return s == null ? 0 : s;
    }

    private static String trim(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    private static HandyFlowException bad(String msg, String code) {
        return new HandyFlowException(msg, HttpStatus.BAD_REQUEST, code);
    }
}
