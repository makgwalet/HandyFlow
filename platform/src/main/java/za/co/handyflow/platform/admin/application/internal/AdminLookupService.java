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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 6 — Lookup Data Management.
 *
 * Manages:
 *  - SA Public Holidays (acc_public_holidays) — used by DeadlineEngine
 *  - SARS Tax Tables (hr_tax_tables + hr_tax_rebates) — read only, admin updates post-budget
 *  - Discount Codes (admin_discounts)
 *  - Module Catalogue admin operations (deactivate, update notes)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLookupService {

    private final JdbcTemplate           jdbc;
    private final AdminAuditLogRepository auditRepo;

    // ── Public Holidays ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHolidays(Integer year) {
        if (year != null) {
            return jdbc.queryForList("""
                SELECT id, holiday_date, name, year
                FROM acc_public_holidays
                WHERE year = ?
                ORDER BY holiday_date
                """, year);
        }
        return jdbc.queryForList("""
            SELECT id, holiday_date, name, year
            FROM acc_public_holidays
            ORDER BY holiday_date DESC
            """);
    }

    @Transactional
    public Map<String, Object> addHoliday(LocalDate date, String name,
                                           UUID adminId, String adminEmail) {
        // Check duplicate
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM acc_public_holidays WHERE holiday_date = ?",
                Integer.class, date);
        if (existing != null && existing > 0) throw new HandyFlowException(
                "Holiday already exists for " + date, HttpStatus.CONFLICT, "DUPLICATE");

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO acc_public_holidays (id, holiday_date, name, year, created_at)
            VALUES (?, ?, ?, ?, NOW())
            """, id, date, name, date.getYear());

        audit(adminId, adminEmail, "ADD_HOLIDAY", "HOLIDAY", id.toString(), name,
                "{\"date\":\"" + date + "\"}");
        log.info("Admin {} added holiday: {} on {}", adminEmail, name, date);

        return jdbc.queryForMap(
                "SELECT id, holiday_date, name, year FROM acc_public_holidays WHERE id = ?", id);
    }

    @Transactional
    public void deleteHoliday(UUID id, UUID adminId, String adminEmail) {
        Map<String, Object> h;
        try {
            h = jdbc.queryForMap(
                    "SELECT name, holiday_date FROM acc_public_holidays WHERE id = ?", id);
        } catch (Exception e) {
            throw new HandyFlowException("Holiday not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        jdbc.update("DELETE FROM acc_public_holidays WHERE id = ?", id);
        audit(adminId, adminEmail, "DELETE_HOLIDAY", "HOLIDAY", id.toString(),
                (String) h.get("name"), null);
    }

    // ── SARS Tax Tables ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTaxTables(Integer taxYear) {
        if (taxYear != null) {
            return jdbc.queryForList("""
                SELECT * FROM hr_tax_tables WHERE tax_year = ? ORDER BY income_from
                """, taxYear);
        }
        // Current year only
        int currentYear = LocalDate.now().getYear();
        return jdbc.queryForList("""
            SELECT * FROM hr_tax_tables WHERE tax_year = ? ORDER BY income_from
            """, currentYear);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTaxRebates(Integer taxYear) {
        int year = taxYear != null ? taxYear : LocalDate.now().getYear();
        try {
            return jdbc.queryForList(
                    "SELECT * FROM hr_tax_rebates WHERE tax_year = ? ORDER BY rebate_type", year);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Update a tax bracket after the February budget speech.
     * WHY JDBC? hr_tax_tables is not a JPA entity — it's seed data managed
     * directly via migrations and admin updates.
     */
    @Transactional
    public void updateTaxBracket(UUID bracketId, BigDecimal rate, BigDecimal incomeFrom,
                                  BigDecimal incomeTo, BigDecimal baseTax,
                                  UUID adminId, String adminEmail) {
        int updated = jdbc.update("""
            UPDATE hr_tax_tables
            SET rate = ?, income_from = ?, income_to = ?,
                base_tax = ?, updated_at = NOW()
            WHERE id = ?
            """, rate, incomeFrom, incomeTo, baseTax, bracketId);

        if (updated == 0) throw new HandyFlowException(
                "Tax bracket not found", HttpStatus.NOT_FOUND, "NOT_FOUND");

        audit(adminId, adminEmail, "UPDATE_TAX_BRACKET", "TAX_TABLE",
                bracketId.toString(), "Tax bracket",
                "{\"rate\":" + rate + ",\"incomeFrom\":" + incomeFrom + "}");
        log.info("Admin {} updated tax bracket {}", adminEmail, bracketId);
    }

    @Transactional
    public void updateTaxRebate(UUID rebateId, BigDecimal amount,
                                 UUID adminId, String adminEmail) {
        int updated = jdbc.update("""
            UPDATE hr_tax_rebates SET amount = ?, updated_at = NOW() WHERE id = ?
            """, amount, rebateId);
        if (updated == 0) throw new HandyFlowException(
                "Rebate not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        audit(adminId, adminEmail, "UPDATE_TAX_REBATE", "TAX_TABLE",
                rebateId.toString(), "Tax rebate", "{\"amount\":" + amount + "}");
    }

    // ── Discount Codes ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDiscounts() {
        return jdbc.queryForList("""
            SELECT id, code, description, discount_type, value, applies_to,
                   module_key, valid_from, valid_to, max_uses, uses_count,
                   active, created_at
            FROM admin_discounts
            ORDER BY created_at DESC
            """);
    }

    @Transactional
    public Map<String, Object> createDiscount(String code, String description,
                                               String discountType, BigDecimal value,
                                               String appliesTo, String moduleKey,
                                               String validFrom, String validTo,
                                               Integer maxUses,
                                               UUID adminId, String adminEmail) {
        // Validate code uniqueness
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_discounts WHERE UPPER(code) = UPPER(?)",
                Integer.class, code);
        if (existing != null && existing > 0) throw new HandyFlowException(
                "Discount code '" + code + "' already exists",
                HttpStatus.CONFLICT, "DUPLICATE");

        // Validate
        if (value.compareTo(BigDecimal.ZERO) <= 0)
            throw new HandyFlowException("Value must be positive", HttpStatus.BAD_REQUEST, "INVALID");
        if ("PERCENT".equals(discountType) && value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new HandyFlowException("Percentage cannot exceed 100", HttpStatus.BAD_REQUEST, "INVALID");

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO admin_discounts
            (id, code, description, discount_type, value, applies_to, module_key,
             valid_from, valid_to, max_uses, uses_count, active, created_by, created_at, updated_at)
            VALUES (?, UPPER(?), ?, ?, ?, ?, ?,
                    ?::timestamp, ?::timestamp,
                    ?, 0, true, ?, NOW(), NOW())
            """,
                id, code, description, discountType, value, appliesTo,
                moduleKey, validFrom, validTo, maxUses, adminId);

        audit(adminId, adminEmail, "CREATE_DISCOUNT", "DISCOUNT", id.toString(),
                code, "{\"type\":\"" + discountType + "\",\"value\":" + value + "}");
        log.info("Admin {} created discount code: {} ({}% off)", adminEmail, code, value);

        return jdbc.queryForMap("SELECT * FROM admin_discounts WHERE id = ?", id);
    }

    @Transactional
    public void deactivateDiscount(UUID id, UUID adminId, String adminEmail) {
        Map<String, Object> d;
        try {
            d = jdbc.queryForMap("SELECT code FROM admin_discounts WHERE id = ?", id);
        } catch (Exception e) {
            throw new HandyFlowException("Discount not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        jdbc.update("UPDATE admin_discounts SET active = false, updated_at = NOW() WHERE id = ?", id);
        audit(adminId, adminEmail, "DEACTIVATE_DISCOUNT", "DISCOUNT",
                id.toString(), (String) d.get("code"), null);
    }

    /**
     * Validate and apply a discount code at module activation.
     * Returns the discount percentage (0–100) or fixed rand amount.
     * Called by ModuleService when a tenant activates with a code.
     */
    @Transactional
    public Map<String, Object> validateAndApplyDiscount(String code, String moduleKey) {
        Map<String, Object> discount;
        try {
            discount = jdbc.queryForMap("""
                SELECT * FROM admin_discounts
                WHERE UPPER(code) = UPPER(?)
                  AND active = true
                  AND (valid_from IS NULL OR valid_from <= NOW())
                  AND (valid_to   IS NULL OR valid_to   >= NOW())
                  AND (max_uses   IS NULL OR uses_count < max_uses)
                """, code);
        } catch (Exception e) {
            throw new HandyFlowException(
                    "Invalid or expired discount code", HttpStatus.BAD_REQUEST, "INVALID_CODE");
        }

        // Check module restriction
        String appliesTo = (String) discount.get("applies_to");
        String restrictedKey = (String) discount.get("module_key");
        if ("MODULE".equals(appliesTo) && !moduleKey.equals(restrictedKey)) {
            throw new HandyFlowException(
                    "This code is not valid for module: " + moduleKey,
                    HttpStatus.BAD_REQUEST, "WRONG_MODULE");
        }

        // Increment usage
        jdbc.update("UPDATE admin_discounts SET uses_count = uses_count + 1, updated_at = NOW() WHERE id = ?",
                discount.get("id"));

        return discount;
    }

    // ── Module Catalogue admin ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModuleCatalogue() {
        return jdbc.queryForList("""
            SELECT key, name, description, monthly_price, icon, category,
                   sort_order, is_active, admin_notes
            FROM module_catalogue
            ORDER BY sort_order, name
            """);
    }

    @Transactional
    public void updateModuleNotes(String moduleKey, String notes,
                                   UUID adminId, String adminEmail) {
        jdbc.update("""
            UPDATE module_catalogue SET admin_notes = ? WHERE key = ?
            """, notes, moduleKey);
        audit(adminId, adminEmail, "UPDATE_MODULE_NOTES", "MODULE",
                moduleKey, moduleKey, null);
    }

    @Transactional
    public void setModuleActive(String moduleKey, boolean active,
                                 UUID adminId, String adminEmail) {
        jdbc.update("""
            UPDATE module_catalogue SET is_active = ? WHERE key = ?
            """, active, moduleKey);
        String action = active ? "ACTIVATE_MODULE_CATALOGUE" : "DEACTIVATE_MODULE_CATALOGUE";
        audit(adminId, adminEmail, action, "MODULE", moduleKey, moduleKey, null);
        log.info("Admin {} {} module {} in catalogue", adminEmail,
                active ? "activated" : "deactivated", moduleKey);
    }

    @Transactional
    public Map<String, Object> createModule(
            String key, String name, String description,
            java.math.BigDecimal monthlyPrice, String icon, String category,
            Integer sortOrder, List<String> extraPermissions,
            UUID adminId, String adminEmail) {

        // 1. Validate key format — UPPER_SNAKE_CASE only
        if (!key.matches("^[A-Z][A-Z0-9_]{1,48}[A-Z0-9]$")) {
            throw new HandyFlowException(
                    "Module key must be UPPER_SNAKE_CASE (2–50 chars, e.g. FLEET_TRACKING)",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_KEY");
        }

        // 2. Check uniqueness
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM module_catalogue WHERE key = ?",
                Integer.class, key);
        if (existing != null && existing > 0) {
            throw new HandyFlowException(
                    "Module key '" + key + "' already exists in the catalogue",
                    org.springframework.http.HttpStatus.CONFLICT, "DUPLICATE");
        }

        // 3. Insert into module_catalogue
        jdbc.update("""
            INSERT INTO module_catalogue
                (key, name, description, monthly_price, icon, category, sort_order, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, true)
            """,
                key, name, description, monthlyPrice,
                icon != null ? icon : "Package",
                category != null ? category : "OTHER",
                sortOrder != null ? sortOrder : 999);

        // 4. Build full permission list: standard 3 + any extras
        List<String> allPermissions = new java.util.ArrayList<>();
        allPermissions.add(key + "_READ");
        allPermissions.add(key + "_MANAGE");
        allPermissions.add(key + "_ADMIN");
        if (extraPermissions != null) {
            extraPermissions.stream()
                    .map(String::toUpperCase)
                    .filter(p -> !allPermissions.contains(p))
                    .forEach(allPermissions::add);
        }

        // 5. Insert permissions
        for (String perm : allPermissions) {
            String desc = switch (perm.substring(perm.lastIndexOf('_') + 1)) {
                case "READ"   -> "View " + name + " data";
                case "MANAGE" -> "Create and manage " + name + " records";
                case "ADMIN"  -> "Full administrative access to " + name;
                default       -> name + " — " + perm;
            };
            jdbc.update("""
                INSERT INTO permissions (id, name, description)
                VALUES (gen_random_uuid(), ?, ?)
                ON CONFLICT (name) DO NOTHING
                """, perm, desc);
        }

        // 6. Grant all new permissions to every ADMIN role (CROSS JOIN)
        String inClause = allPermissions.stream()
                .map(p -> "'" + p + "'")
                .collect(java.util.stream.Collectors.joining(", "));

        int granted = jdbc.update("""
            INSERT INTO role_permissions (role_id, permission_id)
            SELECT r.id, p.id
            FROM roles r
            CROSS JOIN permissions p
            WHERE r.name = 'ADMIN'
              AND p.name IN (%s)
              AND NOT EXISTS (
                SELECT 1 FROM role_permissions rp
                WHERE rp.role_id = r.id AND rp.permission_id = p.id
              )
            """.formatted(inClause));

        log.info("Admin {} created module '{}' — {} permissions created, {} ADMIN role grants",
                adminEmail, key, allPermissions.size(), granted);

        // 7. Audit
        audit(adminId, adminEmail, "CREATE_MODULE", "MODULE", key, name,
                "{\"permissions\":" + allPermissions.size() + ",\"adminGrants\":" + granted +
                        ",\"price\":" + monthlyPrice + "}");

        // 8. Return the created module + stats
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("key", key);
        result.put("name", name);
        result.put("description", description);
        result.put("monthlyPrice", monthlyPrice);
        result.put("icon", icon);
        result.put("category", category);
        result.put("sortOrder", sortOrder);
        result.put("permissionsCreated", allPermissions);
        result.put("adminRoleGrantsCount", granted);
        return result;
    }

    /** List all permissions in the system — used by the new-module form's permission picker. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllPermissions() {
        return jdbc.queryForList("""
            SELECT id, name, description
            FROM permissions
            ORDER BY name
            """);
    }

    /** Per-module stats — active tenant count, trial count, total revenue. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getModuleStats() {
        return jdbc.queryForList("""
            SELECT
                mc.key,
                mc.name,
                mc.category,
                mc.monthly_price,
                mc.is_active,
                COUNT(CASE WHEN tm.status = 'ACTIVE' THEN 1 END)  AS active_tenants,
                COUNT(CASE WHEN tm.status = 'TRIAL'  THEN 1 END)  AS trial_tenants,
                COALESCE(SUM(CASE WHEN tm.status = 'ACTIVE' THEN mc.monthly_price ELSE 0 END), 0) AS mrr
            FROM module_catalogue mc
            LEFT JOIN tenant_modules tm ON tm.module_key = mc.key
            GROUP BY mc.key, mc.name, mc.category, mc.monthly_price, mc.is_active
            ORDER BY mc.sort_order, mc.name
            """);
    }


    // ── Private ───────────────────────────────────────────────────────────────

    private void audit(UUID adminId, String adminEmail, String action,
                        String targetType, String targetId, String targetName,
                        String details) {
        auditRepo.save(AdminAuditLog.create(adminId, adminEmail, action,
                targetType, targetId, targetName, details, null));
    }
}
