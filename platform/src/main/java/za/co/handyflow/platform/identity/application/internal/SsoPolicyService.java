package za.co.handyflow.platform.identity.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single sign-on settings for a company and the rule that stops a password sign-in where SSO is required.
 * First slice: the provider hand-off (redirect, token check) is not here yet, so turning enforcement on is allowed only with break-glass people
 * who keep a password, and never leaves the person switching it on locked out. Every change is audited. No secret is stored: only the name of
 * the server setting that will hold it.
 */
@Service
@RequiredArgsConstructor
public class SsoPolicyService {

    public record Settings(boolean enabled, String provider, String issuerUrl, String clientId, String clientSecretRef,
                           List<String> allowedDomains, boolean enforce, List<UUID> breakGlass) {}

    public record Request(boolean enabled, String issuerUrl, String clientId, String clientSecretRef,
                          List<String> allowedDomains, boolean enforce, List<UUID> breakGlass) {}

    private final JdbcTemplate jdbc;

    /** Optional so a hand-built service still works; in the application it is always there. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AccessAuditService audit;

    private static final String SETTINGS = """
            SELECT enabled, provider, issuer_url, client_id, client_secret_ref, array_to_string(allowed_domains, ',') AS domains, enforce
            FROM tenant_sso_settings WHERE tenant_id = ?""";

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s != null) for (String d : s.split(",")) if (!d.isBlank()) out.add(d);
        return out;
    }

    @Transactional(readOnly = true)
    public Settings get(TenantId tenantId) {
        UUID t = tenantId.getValue();
        List<Settings> rows = jdbc.query(SETTINGS, (rs, i) -> new Settings(rs.getBoolean("enabled"), rs.getString("provider"), rs.getString("issuer_url"),
                rs.getString("client_id"), rs.getString("client_secret_ref"), split(rs.getString("domains")), rs.getBoolean("enforce"), List.of()), t);
        List<UUID> glass = jdbc.queryForList("SELECT user_id FROM tenant_sso_break_glass WHERE tenant_id = ? ORDER BY created_at, user_id", UUID.class, t);
        Settings s = rows.isEmpty() ? new Settings(false, "OIDC", null, null, null, List.of(), false, List.of()) : rows.get(0);
        return new Settings(s.enabled(), s.provider(), s.issuerUrl(), s.clientId(), s.clientSecretRef(), s.allowedDomains(), s.enforce(), glass);
    }

    @Transactional
    public Settings save(TenantId tenantId, UUID actor, Request req) {
        UUID t = tenantId.getValue();
        boolean blankIssuer = req.issuerUrl() == null || req.issuerUrl().isBlank();
        boolean blankClient = req.clientId() == null || req.clientId().isBlank();
        String issuer = blankIssuer && !req.enabled() ? null : SsoRules.cleanIssuer(req.issuerUrl());
        String client = blankClient && !req.enabled() ? null : SsoRules.cleanClientId(req.clientId());
        String ref = SsoRules.cleanSecretRef(req.clientSecretRef());
        List<String> domains = SsoRules.cleanDomains(req.allowedDomains());
        List<UUID> glass = SsoRules.cleanBreakGlass(req.breakGlass());
        if (req.enabled() && domains.isEmpty()) throw new HandyFlowException("Add at least one company domain before turning single sign-on on.", HttpStatus.BAD_REQUEST, "SSO_DOMAIN_REQUIRED");
        if (req.enforce() && !req.enabled()) throw new HandyFlowException("Single sign-on must be on to enforce it.", HttpStatus.BAD_REQUEST, "SSO_ENFORCE_NEEDS_ENABLED");

        for (UUID g : glass) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ? AND tenant_id = ? AND status = 'ACTIVE'", Integer.class, g, t);
            if (n == null || n == 0) throw new HandyFlowException("A chosen person was not found or is not active.", HttpStatus.BAD_REQUEST, "SSO_BREAK_GLASS_UNKNOWN");
        }
        if (req.enforce()) {
            List<String> mail = jdbc.queryForList("SELECT email FROM users WHERE id = ? AND tenant_id = ?", String.class, actor, t);
            String problem = SsoRules.lockoutProblem(domains, glass, actor, mail.isEmpty() ? null : mail.get(0));
            if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "SSO_LOCKOUT");
        }

        Settings before = get(tenantId);
        jdbc.update("""
                INSERT INTO tenant_sso_settings (tenant_id, enabled, provider, issuer_url, client_id, client_secret_ref, allowed_domains, enforce, updated_by, updated_at)
                VALUES (?, ?, 'OIDC', ?, ?, ?, string_to_array(?, ','), ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET enabled = EXCLUDED.enabled, issuer_url = EXCLUDED.issuer_url, client_id = EXCLUDED.client_id,
                    client_secret_ref = EXCLUDED.client_secret_ref, allowed_domains = EXCLUDED.allowed_domains, enforce = EXCLUDED.enforce,
                    updated_by = EXCLUDED.updated_by, updated_at = now()""",
                t, req.enabled(), issuer, client, ref, String.join(",", domains), req.enforce(), actor);
        jdbc.update("DELETE FROM tenant_sso_break_glass WHERE tenant_id = ?", t);
        for (UUID g : glass) jdbc.update("INSERT INTO tenant_sso_break_glass (tenant_id, user_id, created_by) VALUES (?, ?, ?)", t, g, actor);

        Settings after = get(tenantId);
        if (audit != null) audit.record(tenantId, "SSO_SETTINGS_CHANGED", "TENANT", t, null, view(before), view(after));
        return after;
    }

    private static Map<String, Object> view(Settings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", s.enabled()); m.put("enforce", s.enforce()); m.put("issuerUrl", s.issuerUrl()); m.put("clientId", s.clientId());
        m.put("clientSecretRef", s.clientSecretRef()); m.put("allowedDomains", s.allowedDomains()); m.put("breakGlassCount", s.breakGlass().size());
        return m;
    }

    /**
     * Called at password sign-in, after the password has been checked. Refuses with 403 SSO_REQUIRED when the company enforces single sign-on
     * for this person's address and they are not a break-glass user. A company with no settings, or with enforcement off, is not affected.
     */
    @Transactional(readOnly = true)
    public void requirePasswordAllowed(TenantId tenantId, UUID userId, String email) {
        UUID t = tenantId.getValue();
        List<SsoRules.Policy> rows = jdbc.query("SELECT enabled, enforce, array_to_string(allowed_domains, ',') AS domains FROM tenant_sso_settings WHERE tenant_id = ?",
                (rs, i) -> new SsoRules.Policy(rs.getBoolean("enabled"), rs.getBoolean("enforce"), split(rs.getString("domains"))), t);
        if (rows.isEmpty()) return;
        if (SsoRules.decide(rows.get(0), email, false) != SsoRules.Method.SSO_REQUIRED) return;
        Integer glass = jdbc.queryForObject("SELECT count(*) FROM tenant_sso_break_glass WHERE tenant_id = ? AND user_id = ?", Integer.class, t, userId);
        if (glass != null && glass > 0) return;
        throw new HandyFlowException("Your company requires single sign-on. Use the single sign-on button instead of a password.", HttpStatus.FORBIDDEN, "SSO_REQUIRED");
    }
}
