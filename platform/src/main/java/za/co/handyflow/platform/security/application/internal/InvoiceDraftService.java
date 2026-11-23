package za.co.handyflow.platform.security.application.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Saved copies of the billing preview that a second person approves. Not invoices: no number, nothing is sent. The copy is frozen;
 * to refresh it, void the draft and make a new one.
 */
@Service
@RequiredArgsConstructor
public class InvoiceDraftService {

    public record Row(UUID id, UUID siteId, String month, String basis, String currency, String status, long totalCents, boolean inProgress,
                      double unpricedHours, boolean mixedVat, String note, UUID createdBy, OffsetDateTime createdAt, UUID approvedBy,
                      OffsetDateTime approvedAt, UUID voidedBy, OffsetDateTime voidedAt, String voidReason) {}

    public record Detail(Row draft, JsonNode snapshot) {}

    /** The approver permission for the second-person rule: someone else with this permission must approve what you saved. */
    static final String APPROVE_PERMISSION = "SECURITY_ADMIN";

    private static final String COLS = "id, site_id, month, basis, currency, status, total_cents, in_progress, unpriced_hours, mixed_vat, note, created_by, created_at, "
            + "approved_by, approved_at, voided_by, voided_at, void_reason";

    private final JdbcTemplate jdbc;
    private final BillingPreviewService billing;
    private final ObjectMapper mapper;

    /** Optional so a service built by hand still works. Same rule as payroll: a sole operator may approve their own draft, and it is logged. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private za.co.handyflow.platform.shared.SeparationOfDutiesChecker separationOfDuties;

    private static Row row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class), rs.getString("month"), rs.getString("basis"), rs.getString("currency").trim(),
                rs.getString("status"), rs.getLong("total_cents"), rs.getBoolean("in_progress"), rs.getDouble("unpriced_hours"), rs.getBoolean("mixed_vat"), rs.getString("note"),
                rs.getObject("created_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("approved_by", UUID.class),
                rs.getObject("approved_at", OffsetDateTime.class), rs.getObject("voided_by", UUID.class), rs.getObject("voided_at", OffsetDateTime.class), rs.getString("void_reason"));
    }

    @Transactional(readOnly = true)
    public List<Row> list(TenantId tenantId, UUID siteId) {
        return jdbc.query("SELECT " + COLS + " FROM security_invoice_drafts WHERE tenant_id = ? AND site_id = ? ORDER BY month DESC, created_at DESC",
                InvoiceDraftService::row, tenantId.getValue(), siteId);
    }

    @Transactional(readOnly = true)
    public Detail get(TenantId tenantId, UUID siteId, UUID id) {
        List<Row> rows = jdbc.query("SELECT " + COLS + " FROM security_invoice_drafts WHERE id = ? AND tenant_id = ? AND site_id = ?", InvoiceDraftService::row, id, tenantId.getValue(), siteId);
        if (rows.isEmpty()) throw notFound();
        String json = jdbc.queryForObject("SELECT snapshot::text FROM security_invoice_drafts WHERE id = ? AND tenant_id = ?", String.class, id, tenantId.getValue());
        try { return new Detail(rows.get(0), mapper.readTree(json)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Stored invoice draft could not be read", e); }
    }

    @Transactional
    public Row create(TenantId tenantId, UUID actor, UUID siteId, String monthAsked, String basisAsked, String noteAsked) {
        UUID t = tenantId.getValue();
        String month = InvoiceDraftRules.requireMonth(monthAsked);
        String note = InvoiceDraftRules.cleanNote(noteAsked);
        List<Integer> site = jdbc.queryForList("SELECT 1 FROM security_sites WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL FOR UPDATE", Integer.class, siteId, t);
        if (site.isEmpty()) throw new HandyFlowException("Site not found", HttpStatus.NOT_FOUND, "SITE_NOT_FOUND");
        InvoiceDraftRules.requireNoLiveDraft(jdbc.queryForList("SELECT status FROM security_invoice_drafts WHERE tenant_id = ? AND site_id = ? AND month = ?", String.class, t, siteId, month));

        BillingPreviewService.Result p = billing.preview(tenantId, siteId, month, basisAsked); // validates month and basis, refuses a future month
        String snapshot;
        try { snapshot = mapper.writeValueAsString(p); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("The billing preview could not be saved", e); }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO security_invoice_drafts (id, tenant_id, site_id, month, basis, currency, status, total_cents, in_progress, unpriced_hours, mixed_vat, snapshot, note, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?::jsonb, ?, ?)""",
                id, t, siteId, month, p.basis(), p.currency(), p.totalCents(), p.inProgress(), p.unpricedHours(), p.mixedVat(), snapshot, note, actor);
        audit(t, actor, id, "DRAFT_CREATED", "{\"siteId\":\"" + siteId + "\",\"month\":\"" + month + "\",\"basis\":\"" + p.basis() + "\",\"totalCents\":" + p.totalCents() + "}");
        return rowOf(t, id);
    }

    @Transactional
    public Row approve(TenantId tenantId, UUID actor, UUID siteId, UUID id, boolean acknowledged) {
        UUID t = tenantId.getValue();
        Row r = lock(t, siteId, id);
        InvoiceDraftRules.requireApprovable(r.status(), r.inProgress(), r.totalCents(), r.unpricedHours(), r.mixedVat(), acknowledged);
        if (separationOfDuties != null) {
            separationOfDuties.require(tenantId, APPROVE_PERMISSION, r.createdBy(), actor, "You saved this invoice draft, so someone else must approve it",
                    "SECURITY_INVOICE_DRAFT", id, "Saved and approved the same invoice draft");
        }
        jdbc.update("UPDATE security_invoice_drafts SET status = 'APPROVED', approved_by = ?, approved_at = now() WHERE id = ? AND tenant_id = ? AND status = 'DRAFT'", actor, id, t);
        audit(t, actor, id, "DRAFT_APPROVED", "{\"siteId\":\"" + siteId + "\",\"month\":\"" + r.month() + "\",\"totalCents\":" + r.totalCents() + ",\"acknowledgedWarnings\":" + acknowledged + "}");
        return rowOf(t, id);
    }

    @Transactional
    public Row voidDraft(TenantId tenantId, UUID actor, UUID siteId, UUID id, String reasonAsked) {
        UUID t = tenantId.getValue();
        Row r = lock(t, siteId, id);
        String reason = InvoiceDraftRules.requireVoidable(r.status(), reasonAsked);
        jdbc.update("UPDATE security_invoice_drafts SET status = 'VOID', voided_by = ?, voided_at = now(), void_reason = ? WHERE id = ? AND tenant_id = ?", actor, reason, id, t);
        audit(t, actor, id, "DRAFT_VOIDED", "{\"siteId\":\"" + siteId + "\",\"month\":\"" + r.month() + "\",\"wasStatus\":\"" + r.status() + "\"}");
        return rowOf(t, id);
    }

    private Row lock(UUID tenant, UUID siteId, UUID id) {
        List<Row> rows = jdbc.query("SELECT " + COLS + " FROM security_invoice_drafts WHERE id = ? AND tenant_id = ? AND site_id = ? FOR UPDATE", InvoiceDraftService::row, id, tenant, siteId);
        if (rows.isEmpty()) throw notFound();
        return rows.get(0);
    }

    private Row rowOf(UUID tenant, UUID id) {
        return jdbc.queryForObject("SELECT " + COLS + " FROM security_invoice_drafts WHERE id = ? AND tenant_id = ?", InvoiceDraftService::row, id, tenant);
    }

    private static HandyFlowException notFound() { return new HandyFlowException("Draft not found", HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND"); }

    private void audit(UUID tenant, UUID actor, UUID entity, String action, String json) {
        jdbc.update("""
                INSERT INTO security_audit_log (id, tenant_id, actor_id, actor_type, entity_type, entity_id, action, metadata, occurred_at)
                VALUES (gen_random_uuid(), ?, ?, 'USER', 'INVOICE_DRAFT', ?, ?, ?::jsonb, now())""", tenant, actor, entity, action, json);
    }
}
