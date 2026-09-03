package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Phase 9 — Discount and promotional pricing engine.
 *
 * Three discount mechanisms:
 *
 *  1. DISCOUNT CODES — one-time or limited-use promo codes (table: admina_discounts,
 *     seeded in Phase 6). Applied at module activation by the tenant.
 *
 *  2. VOLUME DISCOUNTS — automatic tier-based discounts based on how many modules
 *     a tenant already has active (table: admin_volume_discounts). No code needed.
 *
 *  3. PARTNERSHIP PRICING — named partner agreements with a fixed % off, scoped
 *     to specific tenants or all tenants of a partner (table: admin_partnerships).
 *
 * Resolution order when multiple discounts apply to the same activation:
 *   Partnership > Volume > Discount code
 * Only the highest discount is applied — they do NOT stack.
 * WHY? Stacking discounts can produce perverse pricing (negative or near-zero MRR).
 * A single best-discount rule is simpler and safer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDiscountService {

    private final JdbcTemplate             jdbc;
    private final AdminAuditLogRepository  auditRepo;

    // ── Volume Discounts ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getVolumeTiers() {
        return jdbc.queryForList("""
            SELECT id, min_modules, discount_pct, description, active
            FROM admin_volume_discounts
            ORDER BY min_modules
            """);
    }

    @Transactional
    public Map<String, Object> createVolumeTier(int minModules, BigDecimal pct,
                                                String description,
                                                UUID adminId, String adminEmail) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_volume_discounts WHERE min_modules = ?",
                Integer.class, minModules);
        if (existing != null && existing > 0)
            throw new HandyFlowException("Tier for " + minModules + " modules already exists",
                    HttpStatus.CONFLICT, "DUPLICATE");

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO admin_volume_discounts (id, min_modules, discount_pct, description, active)
            VALUES (?, ?, ?, ?, true)
            """, id, minModules, pct, description);

        audit(adminId, adminEmail, "CREATE_VOLUME_TIER", "VOLUME_DISCOUNT", id.toString(),
                minModules + "+ modules → " + pct + "%", null);
        return jdbc.queryForMap("SELECT * FROM admin_volume_discounts WHERE id = ?", id);
    }

    @Transactional
    public void updateVolumeTier(UUID id, BigDecimal pct, String description,
                                 boolean active, UUID adminId, String adminEmail) {
        int updated = jdbc.update("""
            UPDATE admin_volume_discounts
            SET discount_pct = ?, description = ?, active = ?, updated_at = NOW()
            WHERE id = ?
            """, pct, description, active, id);
        if (updated == 0) throw new HandyFlowException(
                "Volume tier not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        audit(adminId, adminEmail, "UPDATE_VOLUME_TIER", "VOLUME_DISCOUNT",
                id.toString(), pct + "%", null);
    }

    @Transactional
    public void deleteVolumeTier(UUID id, UUID adminId, String adminEmail) {
        jdbc.update("DELETE FROM admin_volume_discounts WHERE id = ?", id);
        audit(adminId, adminEmail, "DELETE_VOLUME_TIER", "VOLUME_DISCOUNT", id.toString(), null, null);
    }

    // ── Partnership Pricing ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPartnerships() {
        return jdbc.queryForList("""
            SELECT id, partner_name, contact_email, discount_pct, applies_to,
                   module_key, tenant_ids, valid_from, valid_to, notes, active, created_at
            FROM admin_partnerships
            ORDER BY created_at DESC
            """);
    }

    @Transactional
    public Map<String, Object> createPartnership(String partnerName, String contactEmail,
                                                 BigDecimal pct, String appliesTo,
                                                 String moduleKey, List<String> tenantSlugs,
                                                 String validFrom, String validTo,
                                                 String notes,
                                                 UUID adminId, String adminEmail) {
        // Resolve tenant slugs to UUIDs
        List<UUID> tenantIds = new ArrayList<>();
        if (tenantSlugs != null) {
            for (String slug : tenantSlugs) {
                try {
                    String idStr = jdbc.queryForObject(
                            "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug);
                    if (idStr != null) tenantIds.add(UUID.fromString(idStr));
                } catch (Exception e) {
                    log.warn("Partnership: tenant slug not found: {}", slug);
                }
            }
        }

        // Build tenant_ids array SQL
        String tenantArr = tenantIds.isEmpty() ? "'{}'::uuid[]"
                : "ARRAY[" + tenantIds.stream()
                .map(u -> "'" + u + "'::uuid")
                .reduce((a, b) -> a + "," + b).orElse("") + "]";

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO admin_partnerships
            (id, partner_name, contact_email, discount_pct, applies_to, module_key,
             tenant_ids, valid_from, valid_to, notes, active, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?,
                    %s,
                    ?::date, ?::date, ?, true, ?, NOW(), NOW())
            """.formatted(tenantArr),
                id, partnerName, contactEmail, pct, appliesTo, moduleKey,
                validFrom, validTo, notes, adminId);

        audit(adminId, adminEmail, "CREATE_PARTNERSHIP", "PARTNERSHIP", id.toString(),
                partnerName, "{\"pct\":" + pct + ",\"tenants\":" + tenantIds.size() + "}");
        log.info("Admin {} created partnership: {} ({}%)", adminEmail, partnerName, pct);

        return jdbc.queryForMap("SELECT * FROM admin_partnerships WHERE id = ?", id);
    }

    @Transactional
    public void deactivatePartnership(UUID id, UUID adminId, String adminEmail) {
        Map<String, Object> p;
        try { p = jdbc.queryForMap("SELECT partner_name FROM admin_partnerships WHERE id = ?", id); }
        catch (Exception e) { throw new HandyFlowException("Partnership not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
        jdbc.update("UPDATE admin_partnerships SET active = false, updated_at = NOW() WHERE id = ?", id);
        audit(adminId, adminEmail, "DEACTIVATE_PARTNERSHIP", "PARTNERSHIP",
                id.toString(), (String) p.get("partner_name"), null);
    }

    // ── Discount resolution — called at module activation ─────────────────────

    /**
     * Resolves the best applicable discount for a tenant activating a module.
     *
     * Checks (in priority order):
     *   1. Partnership discount for this tenant + module
     *   2. Volume discount based on current active module count
     *   3. Discount code (if provided and valid)
     *
     * Returns a DiscountResult with the discount % and source, or zero discount.
     */
    @Transactional(readOnly = true)
    public DiscountResult resolveDiscount(UUID tenantId, String moduleKey,
                                          String discountCode) {
        BigDecimal bestPct    = BigDecimal.ZERO;
        String     bestSource = "NONE";

        // 1. Partnership discount
        try {
            List<Map<String, Object>> partnerships = jdbc.queryForList("""
                SELECT discount_pct, partner_name FROM admin_partnerships
                WHERE active = true
                  AND (valid_from IS NULL OR valid_from <= CURRENT_DATE)
                  AND (valid_to   IS NULL OR valid_to   >= CURRENT_DATE)
                  AND (? = ANY(tenant_ids) OR array_length(tenant_ids, 1) = 0 OR tenant_ids = '{}')
                  AND (applies_to = 'ALL' OR (applies_to = 'MODULE' AND module_key = ?))
                ORDER BY discount_pct DESC
                LIMIT 1
                """, tenantId, moduleKey);

            if (!partnerships.isEmpty()) {
                BigDecimal partnerPct = (BigDecimal) partnerships.get(0).get("discount_pct");
                if (partnerPct.compareTo(bestPct) > 0) {
                    bestPct    = partnerPct;
                    bestSource = "PARTNERSHIP:" + partnerships.get(0).get("partner_name");
                }
            }
        } catch (Exception e) {
            log.debug("Partnership check failed: {}", e.getMessage());
        }

        // 2. Volume discount — count current active modules for this tenant
        try {
            Integer activeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM tenant_modules WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
            if (activeCount != null && activeCount > 0) {
                List<Map<String, Object>> tiers = jdbc.queryForList("""
                    SELECT discount_pct FROM admin_volume_discounts
                    WHERE active = true AND min_modules <= ?
                    ORDER BY min_modules DESC
                    LIMIT 1
                    """, activeCount);
                if (!tiers.isEmpty()) {
                    BigDecimal volPct = (BigDecimal) tiers.get(0).get("discount_pct");
                    if (volPct.compareTo(bestPct) > 0) {
                        bestPct    = volPct;
                        bestSource = "VOLUME:" + activeCount + "_MODULES";
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Volume discount check failed: {}", e.getMessage());
        }

        // 3. Discount code — only if provided
        if (discountCode != null && !discountCode.isBlank()) {
            try {
                Map<String, Object> code = jdbc.queryForMap("""
                    SELECT id, discount_type, value, applies_to, module_key
                    FROM admin_discounts
                    WHERE UPPER(code) = UPPER(?)
                      AND active = true
                      AND (valid_from IS NULL OR valid_from <= NOW())
                      AND (valid_to   IS NULL OR valid_to   >= NOW())
                      AND (max_uses   IS NULL OR uses_count < max_uses)
                    """, discountCode);

                String appliesTo     = (String) code.get("applies_to");
                String codeModuleKey = (String) code.get("module_key");

                if ("ALL".equals(appliesTo) ||
                        ("MODULE".equals(appliesTo) && moduleKey.equals(codeModuleKey))) {
                    BigDecimal codePct = resolveCodePercent(code, moduleKey);
                    if (codePct.compareTo(bestPct) > 0) {
                        bestPct    = codePct;
                        bestSource = "CODE:" + discountCode.toUpperCase();
                    }
                }
            } catch (Exception e) {
                log.debug("Discount code not found or expired: {}", discountCode);
            }
        }

        return new DiscountResult(bestPct, bestSource);
    }

    /**
     * FIX: closes the "FIXED handled separately below" gap — that
     * comment referred to logic that was never actually written, so
     * every FIXED-type discount code silently resolved to a permanent
     * 0% discount despite being a fully creatable, fully validated
     * option in the real admin API (confirmed directly: {@code
     * AdminLookupController.createDiscount()}'s own Swagger summary
     * reads "Create a discount code — PERCENT or FIXED").
     * <p>
     * Deliberately does NOT change resolveDiscount()'s own resolution
     * algorithm — Partnership/Volume/Code stay compared the exact same
     * "highest percentage wins, never stacks" way they already were.
     * This only finishes computing what a FIXED code's own percentage
     * VALUE actually is, using data resolveDiscount() already has
     * (moduleKey) via one extra lookup in this same raw-JDBC style
     * already used throughout this class — no new field, no new
     * cross-module dependency, no signature change to resolveDiscount()
     * or either of its two real callers (AdminDiscountController.
     * previewDiscount(), DiscountFacadeImpl.resolveAndRecordDiscount()).
     * <p>
     * A FIXED amount is converted to the equivalent percentage of that
     * specific module's real catalogue price, so it can be compared
     * like-for-like against the percentage-based Partnership/Volume
     * sources using the identical ">"-wins rule already in place in the
     * caller. Capped at 100% — a fixed discount larger than the price
     * itself must never imply a negative effective price once
     * applyAndRecord() later multiplies this percentage back against
     * the tenant's real activation price.
     * <p>
     * If the module's catalogue price can't be resolved for any reason
     * (unknown key, zero/null price), a FIXED code falls back to 0% —
     * the same safe "no discount rather than guessing" behaviour every
     * other failure path in resolveDiscount() already uses (see the
     * catch blocks around Partnership/Volume above), not a crash and
     * not a silently wrong number.
     */
    private BigDecimal resolveCodePercent(Map<String, Object> code, String moduleKey) {
        String discountType = (String) code.get("discount_type");
        BigDecimal value    = (BigDecimal) code.get("value");

        if ("PERCENT".equals(discountType)) {
            return value;
        }

        if ("FIXED".equals(discountType)) {
            try {
                BigDecimal price = jdbc.queryForObject(
                        "SELECT monthly_price FROM module_catalogue WHERE key = ?",
                        BigDecimal.class, moduleKey);
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("FIXED discount code has no positive catalogue price to convert against for module={}", moduleKey);
                    return BigDecimal.ZERO;
                }
                return value.divide(price, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .min(BigDecimal.valueOf(100));
            } catch (Exception e) {
                log.debug("Could not resolve catalogue price for FIXED discount conversion, module={}: {}",
                        moduleKey, e.getMessage());
                return BigDecimal.ZERO;
            }
        }

        // Unrecognized discount_type — same "no discount rather than
        // guessing" posture as everywhere else in this method.
        return BigDecimal.ZERO;
    }

    /**
     * Apply a discount to a monthly price and return the final price.
     * Also records the redemption for audit purposes.
     */
    @Transactional
    public BigDecimal applyAndRecord(UUID tenantId, String moduleKey,
                                     BigDecimal originalPrice, DiscountResult discount,
                                     UUID activatedBy) {
        if (discount.pct().compareTo(BigDecimal.ZERO) == 0) return originalPrice;

        BigDecimal discountAmount = originalPrice.multiply(discount.pct())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = originalPrice.subtract(discountAmount).max(BigDecimal.ZERO);

        // Record redemption if source was a discount code
        if (discount.source().startsWith("CODE:")) {
            String code = discount.source().substring(5);
            try {
                UUID discountId = UUID.fromString(
                        jdbc.queryForObject(
                                "SELECT id::text FROM admin_discounts WHERE UPPER(code) = UPPER(?)",
                                String.class, code));
                jdbc.update("""
                    INSERT INTO admin_discount_redemptions
                    (id, discount_id, tenant_id, module_key, discount_pct,
                     original_price, final_price, redeemed_by, created_at)
                    VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, NOW())
                    """, discountId, tenantId, moduleKey, discount.pct(),
                        originalPrice, finalPrice, activatedBy);
                // Increment usage counter
                jdbc.update("UPDATE admin_discounts SET uses_count = uses_count + 1 WHERE id = ?",
                        discountId);
            } catch (Exception e) {
                log.warn("Failed to record discount redemption: {}", e.getMessage());
            }
        }

        log.info("Discount applied: tenant={} module={} original={} discount={}% final={} source={}",
                tenantId, moduleKey, originalPrice, discount.pct(), finalPrice, discount.source());
        return finalPrice;
    }

    // ── Redemption history ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRedemptions(UUID tenantId, int limit) {
        if (tenantId != null) {
            return jdbc.queryForList("""
                SELECT r.*, d.code, t.name AS tenant_name, t.slug
                FROM admin_discount_redemptions r
                JOIN admin_discounts d ON d.id = r.discount_id
                JOIN tenants t ON t.id = r.tenant_id
                WHERE r.tenant_id = ?
                ORDER BY r.created_at DESC LIMIT ?
                """, tenantId, limit);
        }
        return jdbc.queryForList("""
            SELECT r.*, d.code, t.name AS tenant_name, t.slug
            FROM admin_discount_redemptions r
            JOIN admin_discounts d ON d.id = r.discount_id
            JOIN tenants t ON t.id = r.tenant_id
            ORDER BY r.created_at DESC LIMIT ?
            """, limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDiscountStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalRedemptions", jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_discount_redemptions", Long.class));
        stats.put("totalDiscountGiven", jdbc.queryForObject(
                "SELECT COALESCE(SUM(original_price - final_price), 0) FROM admin_discount_redemptions",
                BigDecimal.class));
        stats.put("activeCodeCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_discounts WHERE active = true", Long.class));
        stats.put("activePartnershipCount", jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_partnerships WHERE active = true", Long.class));
        stats.put("topCodes", jdbc.queryForList("""
            SELECT d.code, COUNT(r.id) AS redemptions,
                   SUM(r.original_price - r.final_price) AS total_discount
            FROM admin_discounts d
            LEFT JOIN admin_discount_redemptions r ON r.discount_id = d.id
            GROUP BY d.code ORDER BY redemptions DESC LIMIT 5
            """));
        return stats;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void audit(UUID adminId, String adminEmail, String action,
                       String targetType, String targetId, String targetName, String details) {
        auditRepo.save(AdminAuditLog.create(adminId, adminEmail, action,
                targetType, targetId, targetName, details, null));
    }

    // ── Value object ──────────────────────────────────────────────────────────

    public record DiscountResult(BigDecimal pct, String source) {
        public boolean hasDiscount() { return pct.compareTo(BigDecimal.ZERO) > 0; }
        public String label() {
            if (!hasDiscount()) return "No discount";
            if (source.startsWith("PARTNERSHIP:")) return "Partner: " + source.substring(12);
            if (source.startsWith("VOLUME:"))      return "Volume discount";
            if (source.startsWith("CODE:"))        return "Code: " + source.substring(5);
            return pct + "% off";
        }
    }
}
