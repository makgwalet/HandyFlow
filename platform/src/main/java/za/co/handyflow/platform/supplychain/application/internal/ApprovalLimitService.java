package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitRules.Authority;
import za.co.handyflow.platform.supplychain.application.internal.ApprovalLimitRules.Limit;
import za.co.handyflow.platform.supplychain.dto.ApprovalLimitDtos.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Monetary approval limits (see ApprovalLimitRules and migration V766). Off until the company switches it on; once on, every
 * approval of a requisition, purchase order, RFQ award, PO amendment or supplier invoice is checked against the approver's limit
 * and refused with 409 APPROVAL_LIMIT_EXCEEDED, naming people who could approve it instead.
 * <p>
 * Enforcement cannot strand the company: it can only be switched on, and a limit can only be changed or removed while it is on,
 * if somebody active still holds an unlimited limit over every document type.
 */
@Slf4j
@Component("scmApprovalLimits")
@RequiredArgsConstructor
public class ApprovalLimitService {

    public static final String OFF = "OFF", ENFORCE = "ENFORCE";

    private final JdbcTemplate jdbc;
    private final ScmSeparationOfDuties sod;

    // ── Enforcement hooks ─────────────────────────────────────────────────────

    /** Throws 409 APPROVAL_LIMIT_EXCEEDED when enforcement is on and {@code amount} is above what {@code actor} may approve. */
    public void enforce(TenantId tenantId, UUID actor, String documentType, BigDecimal amount) {
        if (amount == null || actor == null) return;
        UUID tid = tenantId.getValue();
        if (!ENFORCE.equals(mode(tid))) return;
        List<Limit> all = limits(tid);
        Authority a = ApprovalLimitRules.resolve(actor, rolesOf(actor), all, documentType);
        if (ApprovalLimitRules.within(a, amount)) return;
        String problem = ApprovalLimitRules.problem(a, amount, documentType, approversFor(tid, all, documentType, amount, actor));
        log.warn("[SCM] Approval limit blocked {} of R {} for user {} ({})", documentType, amount, actor, a.source());
        throw new HandyFlowException(problem, HttpStatus.CONFLICT, "APPROVAL_LIMIT_EXCEEDED");
    }

    /** A requisition has no stored total: it is the lines' quantity x estimated cost plus VAT. */
    public void enforceRequisition(TenantId tenantId, UUID actor, UUID requisitionId) {
        if (actor == null || !ENFORCE.equals(mode(tenantId.getValue()))) return;
        BigDecimal total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(ROUND(qty_requested * estimated_unit_cost * (1 + vat_rate / 100), 2)), 0)
                  FROM sc_requisition_lines WHERE tenant_id = ? AND requisition_id = ?""",
                BigDecimal.class, tenantId.getValue(), requisitionId);
        enforce(tenantId, actor, ApprovalLimitRules.REQUISITION, total);
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LimitsView view(TenantId tenantId) {
        UUID tid = tenantId.getValue();
        List<LimitRow> rows = jdbc.query("""
                SELECT l.id, l.subject_type, l.subject_id, l.document_type, l.max_amount, l.created_by_name, l.updated_at,
                       CASE WHEN l.subject_type = 'USER' THEN u.first_name || ' ' || u.last_name ELSE r.name END AS subject_name
                  FROM sc_approval_limits l
                  LEFT JOIN users u ON l.subject_type = 'USER' AND u.id = l.subject_id
                  LEFT JOIN roles r ON l.subject_type = 'ROLE' AND r.id = l.subject_id
                 WHERE l.tenant_id = ?
                 ORDER BY l.subject_type, subject_name, l.document_type""",
                (rs, n) -> new LimitRow((UUID) rs.getObject("id"), rs.getString("subject_type"), (UUID) rs.getObject("subject_id"),
                        rs.getString("subject_name"), rs.getString("document_type"), rs.getBigDecimal("max_amount"),
                        rs.getString("created_by_name"), rs.getTimestamp("updated_at").toInstant()), tid);
        return new LimitsView(mode(tid), unlimitedHolderExists(tid), rows);
    }

    /** What the signed-in person may approve, per document type. */
    @Transactional(readOnly = true)
    public MyLimits mine(TenantId tenantId, UUID userId) {
        UUID tid = tenantId.getValue();
        List<Limit> all = limits(tid);
        Set<UUID> roles = rolesOf(userId);
        List<MyAuthority> out = new ArrayList<>();
        for (String type : ApprovalLimitRules.DOCUMENT_TYPES) {
            Authority a = ApprovalLimitRules.resolve(userId, roles, all, type);
            out.add(new MyAuthority(type, a.granted(), a.unlimited(), a.max(), a.source()));
        }
        return new MyLimits(mode(tid), out);
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    @Transactional
    public LimitsView save(TenantId tenantId, UUID userId, String userName, SaveLimitRequest req) {
        UUID tid = tenantId.getValue();
        if (req == null || req.subjectId() == null) throw bad("Choose a person or a role");
        String subjectType = req.subjectType() == null ? "" : req.subjectType().trim().toUpperCase(Locale.ROOT);
        if (!subjectType.equals("USER") && !subjectType.equals("ROLE")) throw bad("subjectType must be USER or ROLE");
        String doc = req.documentType() == null || req.documentType().isBlank() ? ApprovalLimitRules.ANY : req.documentType().trim().toUpperCase(Locale.ROOT);
        if (!ApprovalLimitRules.validDocumentType(doc)) throw bad("Unknown document type '" + req.documentType() + "'");
        if (req.maxAmount() != null && req.maxAmount().signum() < 0) throw bad("A limit cannot be negative");
        Integer exists = jdbc.queryForObject(subjectType.equals("USER")
                        ? "SELECT count(*) FROM users WHERE id = ? AND tenant_id = ?"
                        : "SELECT count(*) FROM roles WHERE id = ? AND tenant_id = ?",
                Integer.class, req.subjectId(), tid);
        if (exists == null || exists == 0) throw new HandyFlowException("That " + subjectType.toLowerCase(Locale.ROOT) + " was not found",
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        // Nobody sets their own limit while someone else could; a one-person company is allowed and the exception is audited.
        if (subjectType.equals("USER"))
            sod.require(tenantId, "SCM_APPROVAL_LIMIT_MANAGE", req.subjectId(), userId,
                    "You cannot set your own approval limit; ask another administrator", "ScApprovalLimit", req.subjectId(), "Approval limit change");

        List<Limit> before = limits(tid);
        Limit old = before.stream().filter(l -> l.subjectType().equals(subjectType) && l.subjectId().equals(req.subjectId())
                && l.documentType().equals(doc)).findFirst().orElse(null);
        jdbc.update("""
                INSERT INTO sc_approval_limits (id, tenant_id, subject_type, subject_id, document_type, max_amount, created_by, created_by_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, subject_type, subject_id, document_type)
                DO UPDATE SET max_amount = EXCLUDED.max_amount, updated_at = now()""",
                UUID.randomUUID(), tid, subjectType, req.subjectId(), doc, req.maxAmount(), userId, userName);
        requireNotStranded(tid);
        audit(tid, userId, userName, "SCM_APPROVAL_LIMIT_SET", req.subjectId(),
                subjectType + " " + req.subjectId() + " " + doc + ": " + describe(old != null, old == null ? null : old.max())
                        + " -> " + describe(true, req.maxAmount()));
        return view(tenantId);
    }

    @Transactional
    public LimitsView delete(TenantId tenantId, UUID userId, String userName, UUID limitId) {
        UUID tid = tenantId.getValue();
        List<Map<String, Object>> row = jdbc.queryForList(
                "SELECT subject_type, subject_id, document_type, max_amount FROM sc_approval_limits WHERE tenant_id = ? AND id = ?", tid, limitId);
        if (row.isEmpty()) throw new HandyFlowException("Limit not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        jdbc.update("DELETE FROM sc_approval_limits WHERE tenant_id = ? AND id = ?", tid, limitId);
        requireNotStranded(tid);
        Map<String, Object> r = row.get(0);
        audit(tid, userId, userName, "SCM_APPROVAL_LIMIT_REMOVED", (UUID) r.get("subject_id"),
                r.get("subject_type") + " " + r.get("subject_id") + " " + r.get("document_type") + " was "
                        + describe(true, (BigDecimal) r.get("max_amount")));
        return view(tenantId);
    }

    @Transactional
    public LimitsView setMode(TenantId tenantId, UUID userId, String userName, String mode) {
        UUID tid = tenantId.getValue();
        String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!m.equals(OFF) && !m.equals(ENFORCE)) throw bad("mode must be OFF or ENFORCE");
        String before = mode(tid);
        if (m.equals(ENFORCE) && !unlimitedHolderExists(tid))
            throw new HandyFlowException("Before turning limits on, give at least one active person (or a role with an active member) an "
                    + "unlimited limit for all documents, otherwise nobody could approve large documents.", HttpStatus.CONFLICT, "NO_UNLIMITED_APPROVER");
        jdbc.update("""
                INSERT INTO sc_approval_limit_policy (tenant_id, mode, updated_by, updated_by_name, updated_at) VALUES (?, ?, ?, ?, now())
                ON CONFLICT (tenant_id) DO UPDATE SET mode = EXCLUDED.mode, updated_by = EXCLUDED.updated_by,
                    updated_by_name = EXCLUDED.updated_by_name, updated_at = now()""", tid, m, userId, userName);
        if (!before.equals(m)) audit(tid, userId, userName, "SCM_APPROVAL_LIMIT_MODE_CHANGED", tid, before + " -> " + m);
        return view(tenantId);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    public String mode(UUID tid) {
        List<String> m = jdbc.queryForList("SELECT mode FROM sc_approval_limit_policy WHERE tenant_id = ?", String.class, tid);
        return m.isEmpty() ? OFF : m.get(0);
    }

    private List<Limit> limits(UUID tid) {
        return jdbc.query("SELECT subject_type, subject_id, document_type, max_amount FROM sc_approval_limits WHERE tenant_id = ?",
                (rs, n) -> new Limit(rs.getString("subject_type"), (UUID) rs.getObject("subject_id"), rs.getString("document_type"),
                        rs.getBigDecimal("max_amount")), tid);
    }

    private Set<UUID> rolesOf(UUID userId) {
        return new HashSet<>(jdbc.queryForList("SELECT role_id FROM user_roles WHERE user_id = ?", UUID.class, userId));
    }

    /** Somebody active, directly or through a role with an active member, holds an unlimited limit over every document type. */
    private boolean unlimitedHolderExists(UUID tid) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM sc_approval_limits l
                 WHERE l.tenant_id = ? AND l.document_type = '*' AND l.max_amount IS NULL
                   AND ((l.subject_type = 'USER' AND EXISTS (SELECT 1 FROM users u WHERE u.id = l.subject_id AND u.tenant_id = l.tenant_id AND u.status = 'ACTIVE'))
                     OR (l.subject_type = 'ROLE' AND EXISTS (SELECT 1 FROM user_roles ur JOIN users u ON u.id = ur.user_id
                                                              WHERE ur.role_id = l.subject_id AND u.tenant_id = l.tenant_id AND u.status = 'ACTIVE')))""",
                Integer.class, tid);
        return n != null && n > 0;
    }

    private void requireNotStranded(UUID tid) {
        if (ENFORCE.equals(mode(tid)) && !unlimitedHolderExists(tid))
            throw new HandyFlowException("Limits are being enforced, and this change would leave nobody with an unlimited limit for all "
                    + "documents. Give someone an unlimited limit first, or switch enforcement off.", HttpStatus.CONFLICT, "NO_UNLIMITED_APPROVER");
    }

    /** Up to five other active people whose limit covers the amount: shown in the refusal so the approver knows who to ask. */
    private List<String> approversFor(UUID tid, List<Limit> all, String documentType, BigDecimal amount, UUID actor) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> roles = new HashMap<>();
        jdbc.query("""
                SELECT u.id, u.first_name, u.last_name, ur.role_id FROM users u
                  LEFT JOIN user_roles ur ON ur.user_id = u.id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND u.id <> ?""",
                rs -> {
                    UUID id = (UUID) rs.getObject("id");
                    names.putIfAbsent(id, (rs.getString("first_name") + " " + rs.getString("last_name")).trim());
                    UUID role = (UUID) rs.getObject("role_id");
                    roles.computeIfAbsent(id, k -> new HashSet<>());
                    if (role != null) roles.get(id).add(role);
                }, tid, actor);
        List<String> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            Authority a = ApprovalLimitRules.resolve(e.getKey(), roles.get(e.getKey()), all, documentType);
            if (ApprovalLimitRules.within(a, amount)) out.add(e.getValue());
            if (out.size() == 5) break;
        }
        return out;
    }

    private void audit(UUID tid, UUID userId, String userName, String action, UUID itemId, String detail) {
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, 'STAFF', ?, ?, ?, 'ScApprovalLimit', ?, NULL, ?::jsonb)""",
                tid, userId, userName, action, itemId, "{\"detail\":" + quote(detail) + "}");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String describe(boolean exists, BigDecimal max) {
        return !exists ? "none" : max == null ? "unlimited" : "R " + max.toPlainString();
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "INVALID_LIMIT"); }
}
