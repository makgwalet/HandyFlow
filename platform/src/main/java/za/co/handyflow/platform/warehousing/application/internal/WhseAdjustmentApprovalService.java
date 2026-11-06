package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock adjustments that need a second person. When the warehouse sets an approval threshold, an adjustment larger than
 * it (in either direction) is asked for by one person and decided by another who holds WAREHOUSING_ADJUST_APPROVE.
 * Where nobody else holds that permission the adjustment is applied and recorded as an audited sole-approver exception.
 * With no threshold set (the default) manual adjustments apply at once, as they always did.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhseAdjustmentApprovalService {

    public static final String PERMISSION = "WAREHOUSING_ADJUST_APPROVE";
    static final int MIN_REASON = 5;

    private final JdbcTemplate jdbc;
    private final WhseInventoryService inventoryService;
    private final SeparationOfDutiesChecker duties;

    public record Item(UUID id, UUID inventoryId, BigDecimal delta, String reason, String status, UUID requestedBy,
                       String requestedByName, Instant requestedAt) {}

    /** Units above which an adjustment needs a second person; null when the rule is off. */
    @Transactional(readOnly = true)
    public BigDecimal threshold(TenantId tenantId) {
        List<BigDecimal> rows = jdbc.query("SELECT adjustment_approval_threshold_qty FROM whse_profile WHERE tenant_id = ?",
                (rs, i) -> rs.getBigDecimal(1), tenantId.getValue());
        return rows.isEmpty() ? null : rows.get(0);
    }

    static boolean needsApproval(BigDecimal threshold, BigDecimal delta) {
        return threshold != null && delta != null && delta.abs().compareTo(threshold) > 0;
    }

    /** Throws 409 APPROVAL_REQUIRED when this adjustment is too large to apply directly. */
    @Transactional(readOnly = true)
    public void requireDirectAllowed(TenantId tenantId, BigDecimal delta) {
        BigDecimal t = threshold(tenantId);
        if (needsApproval(t, delta)) {
            throw new HandyFlowException("Adjustments of more than " + t.stripTrailingZeros().toPlainString()
                    + " units need a second person. Submit it as an adjustment request.", HttpStatus.CONFLICT, "APPROVAL_REQUIRED");
        }
    }

    @Transactional
    public UUID request(TenantId tenantId, UUID inventoryId, BigDecimal delta, String reason, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (delta == null || delta.signum() == 0) throw new IllegalArgumentException("The adjustment cannot be zero.");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason of at least " + MIN_REASON + " characters.");
        inventoryService.get(tenantId, inventoryId); // 404 for another tenant's or a missing position
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO whse_adjustment_requests (id, tenant_id, inventory_id, delta, reason, requested_by) VALUES (?, ?, ?, ?, ?, ?)",
                id, tenantId.getValue(), inventoryId, delta, reason.trim(), actor);
        return id;
    }

    @Transactional
    public void approve(TenantId tenantId, UUID requestId, String note, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        Row r = lock(tenantId, requestId);
        requirePending(r);
        boolean sole = actor.equals(r.requestedBy) && duties.otherHolders(tenantId, PERMISSION, actor) == 0;
        duties.require(tenantId, PERMISSION, r.requestedBy, actor,
                "The person who asked for this adjustment cannot approve it. Ask another warehouse administrator.",
                "WHSE_ADJUSTMENT", requestId, "approved their own stock adjustment");
        // Same request can never post twice: the key is the request id.
        inventoryService.adjust(tenantId, r.inventoryId, r.delta,
                r.reason + " (request " + requestId + " by " + r.requestedBy + ")", actor, "adjreq:" + requestId);
        jdbc.update("UPDATE whse_adjustment_requests SET status = 'APPLIED', decided_by = ?, decided_at = now(), decision_note = ?, sole_exception = ?, version = version + 1 WHERE id = ?",
                actor, blank(note), sole, requestId);
    }

    @Transactional
    public void reject(TenantId tenantId, UUID requestId, String note, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (note == null || note.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason for declining (at least " + MIN_REASON + " characters).");
        Row r = lock(tenantId, requestId);
        requirePending(r);
        jdbc.update("UPDATE whse_adjustment_requests SET status = 'REJECTED', decided_by = ?, decided_at = now(), decision_note = ?, version = version + 1 WHERE id = ?",
                actor, note.trim(), requestId);
    }

    @Transactional
    public void cancel(TenantId tenantId, UUID requestId, UUID actor) {
        Row r = lock(tenantId, requestId);
        requirePending(r);
        if (actor == null || !actor.equals(r.requestedBy)) throw new AccessDeniedException("Only the person who asked for the adjustment can cancel it.");
        jdbc.update("UPDATE whse_adjustment_requests SET status = 'CANCELLED', decided_by = ?, decided_at = now(), version = version + 1 WHERE id = ?", actor, requestId);
    }

    @Transactional(readOnly = true)
    public List<Item> pending(TenantId tenantId) {
        return jdbc.query("""
                SELECT r.id, r.inventory_id, r.delta, r.reason, r.status, r.requested_by, r.requested_at,
                       trim(coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '')) AS requested_by_name
                  FROM whse_adjustment_requests r LEFT JOIN users u ON u.id = r.requested_by
                 WHERE r.tenant_id = ? AND r.status = 'PENDING' ORDER BY r.requested_at""",
                (rs, i) -> new Item(rs.getObject("id", UUID.class), rs.getObject("inventory_id", UUID.class), rs.getBigDecimal("delta"),
                        rs.getString("reason"), rs.getString("status"), rs.getObject("requested_by", UUID.class),
                        rs.getString("requested_by_name"), rs.getTimestamp("requested_at").toInstant()),
                tenantId.getValue());
    }

    /** Sets or clears the threshold. Raising it or switching it off weakens a control, so a reason is required and recorded. */
    @Transactional
    public void setThreshold(TenantId tenantId, BigDecimal qty, String reason, UUID actor) {
        if (actor == null) throw new AccessDeniedException("Your user could not be identified, so this step cannot be recorded.");
        if (reason == null || reason.trim().length() < MIN_REASON) throw new IllegalArgumentException("Give a reason for the change (at least " + MIN_REASON + " characters).");
        if (qty != null && qty.signum() < 0) throw new IllegalArgumentException("The threshold cannot be negative.");
        BigDecimal before = threshold(tenantId);
        List<UUID> profile = jdbc.query("SELECT id FROM whse_profile WHERE tenant_id = ?", (rs, i) -> rs.getObject(1, UUID.class), tenantId.getValue());
        if (profile.isEmpty()) throw new IllegalStateException("Set up the warehouse profile first.");
        jdbc.update("UPDATE whse_profile SET adjustment_approval_threshold_qty = ?, updated_at = now() WHERE tenant_id = ?", qty, tenantId.getValue());
        String name = null;
        try { name = za.co.handyflow.platform.shared.TenantContext.getCurrentUserName(); } catch (RuntimeException ignored) { /* no named user */ }
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, after_json)
                VALUES (?, 'STAFF', ?, ?, 'WHSE_ADJUSTMENT_THRESHOLD_CHANGED', 'WHSE_PROFILE', ?, ?::jsonb)""",
                tenantId.getValue(), actor, name, profile.get(0),
                "{\"before\":" + (before == null ? "null" : before.toPlainString()) + ",\"after\":" + (qty == null ? "null" : qty.toPlainString())
                        + ",\"reason\":" + quote(reason.trim()) + "}");
        log.warn("[Warehousing] Adjustment approval threshold changed tenant={} by={} {} -> {}", tenantId.getValue(), actor, before, qty);
    }

    // ── internals ───────────────────────────────────────────────────────────

    record Row(UUID inventoryId, BigDecimal delta, String reason, String status, UUID requestedBy) {}

    private Row lock(TenantId tenantId, UUID id) {
        List<Row> rows = jdbc.query("SELECT inventory_id, delta, reason, status, requested_by FROM whse_adjustment_requests WHERE tenant_id = ? AND id = ? FOR UPDATE",
                (rs, i) -> new Row(rs.getObject("inventory_id", UUID.class), rs.getBigDecimal("delta"), rs.getString("reason"),
                        rs.getString("status"), rs.getObject("requested_by", UUID.class)),
                tenantId.getValue(), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Adjustment request", id.toString());
        return rows.get(0);
    }

    private static void requirePending(Row r) {
        if (!"PENDING".equals(r.status)) throw new HandyFlowException("This request has already been decided.", HttpStatus.CONFLICT, "ADJUSTMENT_DECIDED");
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }
}
