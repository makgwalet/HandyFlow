package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierPortalGrant;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierPortalGrantRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.GrantRow;

import java.util.List;
import java.util.UUID;

/** Staff side of supplier portal access: invite a supplier's person by email, list who has access, revoke it. Audited. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierPortalAdminService {

    private final ScSupplierPortalGrantRepository grantRepo;
    private final ScSupplierRepository supplierRepo;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public GrantRow invite(TenantId tenantId, UUID supplierId, String email, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        ScSupplier supplier = supplierRepo.findByTenantIdAndId(tid, supplierId)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!supplier.isOrderable())
            throw new HandyFlowException(supplier.getName() + " is " + supplier.getStatus() + " and cannot be given portal access",
                    HttpStatus.CONFLICT, "SUPPLIER_NOT_ACTIVE");
        String clean = email == null ? "" : email.trim().toLowerCase();
        if (!clean.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new HandyFlowException("A valid email address is required", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        boolean already = grantRepo.findBySupplier(tid, supplierId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(clean) && !ScSupplierPortalGrant.REVOKED.equals(g.getStatus()));
        if (already) throw new HandyFlowException("That email already has a pending or active invite for this supplier", HttpStatus.CONFLICT, "ALREADY_INVITED");

        ScSupplierPortalGrant grant = grantRepo.save(ScSupplierPortalGrant.createInvite(tid, supplierId, clean, userId));
        sendInvite(tid, supplier.getName(), grant);
        audit(tid, userId, userName, "SCM_SUPPLIER_PORTAL_INVITED", grant.getId(), supplier.getName() + ": " + clean);
        return row(grant);
    }

    /** Fresh link for an invite that is still pending (expired, lost, or the email never arrived). */
    @Transactional
    public GrantRow resend(TenantId tenantId, UUID grantId, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        ScSupplierPortalGrant g = grantRepo.findByTenantAndId(tid, grantId)
                .orElseThrow(() -> new HandyFlowException("Invite not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!ScSupplierPortalGrant.PENDING.equals(g.getStatus()))
            throw new HandyFlowException("Only a pending invite can be re-sent", HttpStatus.CONFLICT, "NOT_PENDING");
        g.reissue();
        grantRepo.save(g);
        String name = supplierRepo.findByTenantIdAndId(tid, g.getSupplierId()).map(ScSupplier::getName).orElse("your supplier account");
        sendInvite(tid, name, g);
        audit(tid, userId, userName, "SCM_SUPPLIER_PORTAL_RESENT", g.getId(), name + ": " + g.getInviteEmail());
        return row(g);
    }

    @Transactional(readOnly = true)
    public List<GrantRow> list(TenantId tenantId, UUID supplierId) {
        return grantRepo.findBySupplier(tenantId.getValue(), supplierId).stream().map(SupplierPortalAdminService::row).toList();
    }

    @Transactional
    public GrantRow revoke(TenantId tenantId, UUID grantId, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        ScSupplierPortalGrant g = grantRepo.findByTenantAndId(tid, grantId)
                .orElseThrow(() -> new HandyFlowException("Access not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (ScSupplierPortalGrant.REVOKED.equals(g.getStatus())) return row(g);
        g.revoke(userId);
        grantRepo.save(g);
        audit(tid, userId, userName, "SCM_SUPPLIER_PORTAL_REVOKED", g.getId(), g.getInviteEmail());
        return row(g);
    }

    private void sendInvite(UUID tid, String supplierName, ScSupplierPortalGrant grant) {
        String buyer = jdbc.query("SELECT name FROM tenants WHERE id = ?", (rs, n) -> rs.getString(1), tid).stream().findFirst().orElse("A customer");
        try {
            emailService.send(grant.getInviteEmail(), buyer + " has invited " + supplierName + " to quote online",
                    EmailTemplates.portalInvite(buyer, "Supplier Portal",
                            frontendUrl + "/supplier-portal/register?token=" + grant.getInviteToken()));
        } catch (Exception e) {
            log.error("[SCM] Supplier portal invite email failed to={} supplier={}: {}", grant.getInviteEmail(), grant.getSupplierId(), e.getMessage());
        }
    }

    private void audit(UUID tid, UUID userId, String userName, String action, UUID itemId, String detail) {
        jdbc.update("""
                INSERT INTO audit_events (tenant_id, actor_type, actor_id, actor_name, action, item_type, item_id, before_json, after_json)
                VALUES (?, 'STAFF', ?, ?, ?, 'ScSupplierPortalGrant', ?, NULL, ?::jsonb)""",
                tid, userId, userName, action, itemId,
                "{\"detail\":\"" + detail.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }

    static GrantRow row(ScSupplierPortalGrant g) {
        return new GrantRow(g.getId(), g.getSupplierId(), g.getInviteEmail(), g.getStatus(), g.getInvitedAt(), g.getAcceptedAt(),
                ScSupplierPortalGrant.PENDING.equals(g.getStatus()) ? g.getInviteTokenExpiresAt() : null);
    }
}
