package za.co.handyflow.platform.admin.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.admin.dto.TenantDiagnosticCheck;
import za.co.handyflow.platform.shared.HandyFlowException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant Diagnostic Engine — read-only health checks a HandyFlow support
 * staffer can run against one tenant, per the platform brief's section 19.
 * <p>
 * Deliberately a first, small, real set of checks rather than the full
 * wishlist the brief describes (scheduled-job health, failed-notification
 * counts, etc. aren't checkable yet — there's no job-execution-history or
 * email-delivery-tracking table in this codebase to query; see
 * PLATFORM-ENGINES-PROGRESS.md). Every check below queries a table that
 * genuinely exists and was confirmed by reading the schema, not assumed.
 * <p>
 * Follows this module's own established convention (see AdminService) of
 * querying directly via JdbcTemplate rather than going through JPA
 * repositories or module facades — this module already crosses every
 * module's data for support/ops purposes by design.
 */
@Service
@RequiredArgsConstructor
public class AdminTenantDiagnosticService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<TenantDiagnosticCheck> getDiagnostics(String slugOrId) {
        Map<String, Object> tenant;
        try {
            tenant = jdbc.queryForMap("""
                SELECT t.id, t.status, t.document_code, t.vat_number, t.logo_url
                FROM tenants t
                WHERE (t.slug = ? OR t.id::text = ?)
                """, slugOrId, slugOrId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new HandyFlowException("Tenant not found: " + slugOrId,
                    HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND");
        }

        UUID tenantId = (UUID) tenant.get("id");
        List<TenantDiagnosticCheck> checks = new ArrayList<>();

        // ── Tenant status ────────────────────────────────────────────────────
        String status = (String) tenant.get("status");
        checks.add(TenantDiagnosticCheck.critical(
                "TENANT_STATUS", "Tenant active",
                "ACTIVE".equals(status) || "TRIAL".equals(status),
                "Status: " + status));

        // ── Has at least one user ───────────────────────────────────────────
        Integer userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE tenant_id = ?", Integer.class, tenantId);
        checks.add(TenantDiagnosticCheck.critical(
                "HAS_USERS", "At least one user account",
                userCount != null && userCount > 0,
                userCount + " user(s)"));

        // ── Has at least one active module ──────────────────────────────────
        Integer activeModuleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_modules WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, tenantId);
        checks.add(TenantDiagnosticCheck.critical(
                "HAS_ACTIVE_MODULES", "At least one active module",
                activeModuleCount != null && activeModuleCount > 0,
                activeModuleCount + " active module(s)"));

        // ── Document numbering configured (Tenant Numbering Engine) ────────
        // See TenantNumberingFacade / V285 — document_code is assigned
        // lazily on first document issued if still null here, so "not yet
        // set" is informational, not a fault: it just means this tenant
        // hasn't issued a numbered document since the engine shipped.
        String documentCode = (String) tenant.get("document_code");
        checks.add(TenantDiagnosticCheck.informational(
                "DOCUMENT_CODE", "Document numbering code assigned",
                documentCode != null && !documentCode.isBlank(),
                documentCode != null ? "Code: " + documentCode
                        : "Not yet assigned — set automatically on first numbered document"));

        // ── Email signature (Email Engine) ──────────────────────────────────
        // Opt-in feature (see V286) — informational only, absence is not a
        // fault.
        Integer signatureEnabledCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenant_email_signature
                WHERE tenant_id = ? AND enabled = true
                """, Integer.class, tenantId);
        checks.add(TenantDiagnosticCheck.informational(
                "EMAIL_SIGNATURE", "Email signature configured",
                signatureEnabledCount != null && signatureEnabledCount > 0,
                (signatureEnabledCount != null && signatureEnabledCount > 0)
                        ? "Configured and enabled"
                        : "Not configured — outbound emails use no tenant sign-off"));

        // ── VAT number on file ───────────────────────────────────────────────
        // Informational: a tenant genuinely may not be VAT-registered.
        String vatNumber = (String) tenant.get("vat_number");
        checks.add(TenantDiagnosticCheck.informational(
                "VAT_NUMBER", "VAT number on file",
                vatNumber != null && !vatNumber.isBlank(),
                (vatNumber != null && !vatNumber.isBlank()) ? "On file" : "Not set"));

        // ── Logo uploaded ─────────────────────────────────────────────────
        String logoUrl = (String) tenant.get("logo_url");
        checks.add(TenantDiagnosticCheck.informational(
                "LOGO", "Logo uploaded",
                logoUrl != null && !logoUrl.isBlank(),
                (logoUrl != null && !logoUrl.isBlank()) ? "Uploaded" : "Not uploaded"));

        return checks;
    }
}
