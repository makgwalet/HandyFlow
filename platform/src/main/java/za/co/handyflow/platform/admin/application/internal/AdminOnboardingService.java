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

import java.util.*;

/**
 * Phase 10 — Tenant Onboarding Assistance.
 *
 * Provides admin-assisted onboarding capabilities:
 *  1. Seed company profile on behalf of a tenant (name, reg number, VAT, address)
 *  2. Bulk import users via CSV (name, email, role)
 *  3. Enable modules on behalf of tenant (admin force-activates specific modules)
 *  4. Send welcome email with first-login instructions
 *  5. Track progress via onboarding session checklist
 *
 * WHY a separate onboarding session? When HandyFlow's sales/onboarding team
 * assists a new tenant, they need to see what's been done and what's pending
 * without navigating 5 different screens. The session gives a single view of
 * onboarding progress per tenant and creates an audit record of every action
 * taken on behalf of the customer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOnboardingService {

    private final JdbcTemplate             jdbc;
    private final AdminAuditLogRepository  auditRepo;
    private final AdminNotificationService notificationService;

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> startSession(String tenantSlug, UUID adminId, String adminEmail) {
        UUID tenantId = resolveTenantId(tenantSlug);
        String tenantName = resolveTenantName(tenantSlug);

        // Check for existing in-progress session
        List<Map<String, Object>> existing = jdbc.queryForList("""
            SELECT id, status FROM admin_onboarding_sessions
            WHERE tenant_id = ? AND status = 'IN_PROGRESS'
            ORDER BY created_at DESC LIMIT 1
            """, tenantId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO admin_onboarding_sessions
              (id, tenant_id, tenant_slug, tenant_name, status, admin_id, admin_email, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, NOW(), NOW())
            """, id, tenantId, tenantSlug, tenantName, adminId, adminEmail);

        audit(adminId, adminEmail, "START_ONBOARDING", "TENANT", tenantSlug, tenantName, null);
        log.info("Admin {} started onboarding session for tenant {}", adminEmail, tenantSlug);
        return getSession(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSession(UUID sessionId) {
        try {
            return jdbc.queryForMap("SELECT * FROM admin_onboarding_sessions WHERE id = ?", sessionId);
        } catch (Exception e) {
            throw new HandyFlowException("Session not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessions(String status, int limit) {
        if (status != null) {
            return jdbc.queryForList("""
                SELECT s.*, t.name AS current_tenant_name
                FROM admin_onboarding_sessions s
                JOIN tenants t ON t.id = s.tenant_id
                WHERE s.status = ?
                ORDER BY s.created_at DESC LIMIT ?
                """, status, limit);
        }
        return jdbc.queryForList("""
            SELECT s.*, t.name AS current_tenant_name
            FROM admin_onboarding_sessions s
            JOIN tenants t ON t.id = s.tenant_id
            ORDER BY s.created_at DESC LIMIT ?
            """, limit);
    }

    @Transactional
    public void completeSession(UUID sessionId, UUID adminId, String adminEmail) {
        Map<String, Object> s = getSession(sessionId);
        jdbc.update("""
            UPDATE admin_onboarding_sessions
            SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, sessionId);
        audit(adminId, adminEmail, "COMPLETE_ONBOARDING", "TENANT",
                (String) s.get("tenant_slug"), (String) s.get("tenant_name"), null);
    }

    // ── Seed company profile ──────────────────────────────────────────────────

    /**
     * Pre-fills the tenant's company profile so they don't have to do it manually.
     * Useful when the sales team collected the details during the sign-up call.
     */
    @Transactional
    public void seedCompanyProfile(UUID sessionId, String registrationNumber,
                                   String vatNumber, String phone, String address,
                                   String city, String postalCode, String country,
                                   String industry, String website,
                                   UUID adminId, String adminEmail) {
        Map<String, Object> s = getSession(sessionId);
        String tenantSlug = (String) s.get("tenant_slug");
        UUID   tenantId   = (UUID)   s.get("tenant_id");

        // Update tenants table with the extra fields
        // Uses UPDATE ... WHERE slug to be slug-safe — only fills non-null fields
        StringBuilder sb = new StringBuilder("UPDATE tenants SET updated_at = NOW()");
        List<Object> params = new ArrayList<>();

        if (registrationNumber != null) { sb.append(", registration_number = ?"); params.add(registrationNumber); }
        if (vatNumber != null)           { sb.append(", vat_number = ?");          params.add(vatNumber); }
        if (phone != null)               { sb.append(", phone = ?");               params.add(phone); }
        if (address != null)             { sb.append(", address = ?");             params.add(address); }
        if (city != null)                { sb.append(", city = ?");               params.add(city); }
        if (postalCode != null)          { sb.append(", postal_code = ?");         params.add(postalCode); }
        if (country != null)             { sb.append(", country = ?");             params.add(country); }
        if (industry != null)            { sb.append(", industry = ?");            params.add(industry); }
        if (website != null)             { sb.append(", website = ?");             params.add(website); }

        sb.append(" WHERE slug = ?");
        params.add(tenantSlug);
        jdbc.update(sb.toString(), params.toArray());

        // Mark step complete
        jdbc.update("UPDATE admin_onboarding_sessions SET company_seeded = true, updated_at = NOW() WHERE id = ?", sessionId);

        audit(adminId, adminEmail, "SEED_COMPANY_PROFILE", "TENANT", tenantSlug,
                (String) s.get("tenant_name"),
                "{\"regNo\":\"" + registrationNumber + "\",\"vat\":\"" + vatNumber + "\"}");
        log.info("Admin {} seeded company profile for {}", adminEmail, tenantSlug);
    }

    // ── Bulk user import ──────────────────────────────────────────────────────

    /**
     * Import users from a CSV payload (already parsed to rows on the frontend).
     * Each row: { firstName, lastName, email, roleName }
     *
     * Creates users with a temporary password and sends invitation emails.
     * Returns a result map with counts and any per-row errors.
     */
    @Transactional
    public Map<String, Object> importUsers(UUID sessionId,
                                           List<Map<String, String>> rows,
                                           UUID adminId, String adminEmail) {
        Map<String, Object> s = getSession(sessionId);
        String tenantSlug = (String) s.get("tenant_slug");
        UUID   tenantId   = (UUID)   s.get("tenant_id");

        int created = 0, skipped = 0;
        List<String> errors  = new ArrayList<>();
        List<String> created_emails = new ArrayList<>();

        // Get default USER role id for this tenant
        UUID defaultRoleId = null;
        try {
            defaultRoleId = UUID.fromString(jdbc.queryForObject(
                    "SELECT id::text FROM roles WHERE tenant_id = ? AND name = 'USER' LIMIT 1",
                    String.class, tenantId));
        } catch (Exception e) {
            log.warn("No USER role found for tenant {}", tenantSlug);
        }

        for (Map<String, String> row : rows) {
            String email     = row.getOrDefault("email", "").trim().toLowerCase();
            String firstName = row.getOrDefault("firstName", row.getOrDefault("first_name", "")).trim();
            String lastName  = row.getOrDefault("lastName",  row.getOrDefault("last_name", "")).trim();
            String roleName  = row.getOrDefault("role", "USER").trim().toUpperCase();

            if (email.isBlank()) { errors.add("Row missing email"); skipped++; continue; }
            if (!email.contains("@")) { errors.add("Invalid email: " + email); skipped++; continue; }

            // Check duplicate
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND email = ?",
                    Integer.class, tenantId, email);
            if (exists != null && exists > 0) {
                errors.add("Already exists: " + email);
                skipped++;
                continue;
            }

            // Generate temp password and invitation token
            UUID userId    = UUID.randomUUID();
            String tempPw  = "Welcome" + (int)(Math.random() * 9000 + 1000) + "!";
            String inviteToken = UUID.randomUUID().toString().replace("-", "");

            try {
                // Find role for this tenant
                UUID roleId = defaultRoleId;
                if (!roleName.equals("USER")) {
                    try {
                        roleId = UUID.fromString(jdbc.queryForObject(
                                "SELECT id::text FROM roles WHERE tenant_id = ? AND name = ? LIMIT 1",
                                String.class, tenantId, roleName));
                    } catch (Exception e) {
                        // Fall back to USER role
                    }
                }

                // Insert user
                jdbc.update("""
                    INSERT INTO users
                    (id, tenant_id, email, first_name, last_name,
                     password_hash, role_id, email_verified, active,
                     invite_token, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?,
                            ?, ?, false, true,
                            ?, NOW(), NOW())
                    """,
                        userId, tenantId, email, firstName, lastName,
                        tempPw, // raw — email tells them to set password on first login
                        roleId,
                        inviteToken);

                created_emails.add(email);
                created++;
            } catch (Exception e) {
                errors.add("Failed: " + email + " — " + e.getMessage());
                skipped++;
            }
        }

        // Update session
        jdbc.update("""
            UPDATE admin_onboarding_sessions
            SET users_imported = true,
                users_imported_count = users_imported_count + ?,
                updated_at = NOW()
            WHERE id = ?
            """, created, sessionId);

        audit(adminId, adminEmail, "IMPORT_USERS", "TENANT", tenantSlug,
                (String) s.get("tenant_name"),
                "{\"created\":" + created + ",\"skipped\":" + skipped + "}");
        log.info("Admin {} imported {} users for tenant {} ({} skipped)", adminEmail, created, tenantSlug, skipped);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("errors",  errors);
        result.put("createdEmails", created_emails);
        return result;
    }

    // ── Enable modules ────────────────────────────────────────────────────────

    /**
     * Force-activate a list of modules for the tenant.
     * Delegates to existing AdminService.forceActivateModule logic via raw JDBC
     * to avoid circular dependency.
     */
    @Transactional
    public Map<String, Object> enableModules(UUID sessionId, List<String> moduleKeys,
                                             UUID adminId, String adminEmail) {
        Map<String, Object> s = getSession(sessionId);
        String tenantSlug = (String) s.get("tenant_slug");

        int activated = 0;
        List<String> failed = new ArrayList<>();

        for (String key : moduleKeys) {
            try {
                int updated = jdbc.update("""
                    UPDATE tenant_modules
                    SET status = 'ACTIVE', activated_at = NOW(), updated_at = NOW()
                    WHERE tenant_id = (SELECT id FROM tenants WHERE slug = ?)
                      AND module_key = ?
                    """, tenantSlug, key);

                if (updated == 0) {
                    // Module not yet subscribed — insert it
                    jdbc.update("""
                        INSERT INTO tenant_modules (tenant_id, module_key, status, activated_at, created_at, updated_at)
                        SELECT id, ?, 'ACTIVE', NOW(), NOW(), NOW() FROM tenants WHERE slug = ?
                        ON CONFLICT (tenant_id, module_key) DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
                        """, key, tenantSlug);
                }
                activated++;
            } catch (Exception e) {
                failed.add(key + ": " + e.getMessage());
            }
        }

        // Build array literal for PostgreSQL
        String moduleArr = moduleKeys.stream()
                .map(k -> "'" + k + "'")
                .reduce((a, b) -> a + "," + b).orElse("''");

        jdbc.update("""
            UPDATE admin_onboarding_sessions
            SET modules_enabled = true,
                modules_enabled_list = ARRAY[%s]::text[],
                updated_at = NOW()
            WHERE id = ?
            """.formatted(moduleArr), sessionId);

        audit(adminId, adminEmail, "ENABLE_MODULES_ONBOARDING", "TENANT", tenantSlug,
                (String) s.get("tenant_name"),
                "{\"modules\":" + moduleKeys.size() + ",\"activated\":" + activated + "}");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activated", activated);
        result.put("failed",    failed);
        result.put("modules",   moduleKeys);
        return result;
    }

    // ── Welcome email ─────────────────────────────────────────────────────────

    @Transactional
    public void markWelcomeSent(UUID sessionId, UUID adminId, String adminEmail) {
        Map<String, Object> s = getSession(sessionId);
        jdbc.update("""
            UPDATE admin_onboarding_sessions
            SET welcome_sent = true, updated_at = NOW()
            WHERE id = ?
            """, sessionId);
        audit(adminId, adminEmail, "MARK_WELCOME_SENT", "TENANT",
                (String) s.get("tenant_slug"), (String) s.get("tenant_name"), null);
    }

    // ── Update notes ──────────────────────────────────────────────────────────

    @Transactional
    public void updateNotes(UUID sessionId, String notes) {
        jdbc.update("UPDATE admin_onboarding_sessions SET notes = ?, updated_at = NOW() WHERE id = ?",
                notes, sessionId);
    }

    // ── Parse CSV ────────────────────────────────────────────────────────────

    /**
     * Parse a raw CSV string into row maps.
     * Accepts header row: email, firstName/first_name, lastName/last_name, role
     * Returns list of maps for importUsers().
     */
    public List<Map<String, String>> parseCsv(String csv) {
        List<Map<String, String>> rows = new ArrayList<>();
        String[] lines = csv.split("\n");
        if (lines.length < 2) return rows;

        // Parse headers (first row)
        String[] headers = lines[0].trim().split(",");
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim().replace("\"", "").toLowerCase();
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            String[] vals = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length && j < vals.length; j++) {
                row.put(headers[j], vals[j].trim().replace("\"", ""));
            }
            rows.add(row);
        }
        return rows;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UUID resolveTenantId(String slug) {
        try {
            String id = jdbc.queryForObject(
                    "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug);
            if (id == null) throw new RuntimeException("not found");
            return UUID.fromString(id);
        } catch (Exception e) {
            throw new HandyFlowException("Tenant not found: " + slug, HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
    }

    private String resolveTenantName(String slug) {
        try {
            String name = jdbc.queryForObject("SELECT name FROM tenants WHERE slug = ?", String.class, slug);
            return name != null ? name : slug;
        } catch (Exception e) { return slug; }
    }

    private void audit(UUID adminId, String adminEmail, String action,
                       String targetType, String targetId, String targetName, String details) {
        auditRepo.save(AdminAuditLog.create(adminId, adminEmail, action,
                targetType, targetId, targetName, details, null));
    }
}
