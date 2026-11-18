package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.PortalUser;
import za.co.handyflow.platform.shared.PortalUserRepository;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.QuotationStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.QuotationRequest;
import za.co.handyflow.platform.supplychain.dto.QuotationResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.*;

import java.time.Instant;
import java.util.*;

/**
 * What a logged-in supplier can do: see the RFQs it was invited to, read the specification, submit or revise its own quotation,
 * attach its quote document, withdraw or decline, while the RFQ is open.
 * <p>
 * Safety rules, all enforced here and not in the browser:
 * <ul>
 *   <li>The supplier is taken from the person's active grant. A request body can never name one.</li>
 *   <li>An RFQ is reachable only through an invitation held by one of the person's suppliers.</li>
 *   <li>The buyer's company must still have Supply Chain in its plan.</li>
 *   <li>Only this supplier's own quotation is ever returned: no other supplier's price, rank or score, and none of the buyer's estimates or weights.</li>
 *   <li>The writes go through QuotationService, so every existing rule (open RFQ, closing time, line checks, revision history, audit trail) applies unchanged.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierPortalService {

    private final ScSupplierPortalGrantRepository grantRepo;
    private final PortalUserRepository portalUserRepo;
    private final QuotationService quotationService;
    private final ScRfqRepository rfqRepo;
    private final ScRfqLineRepository rfqLineRepo;
    private final ScRfqInvitationRepository inviteRepo;
    private final ScQuotationRepository quoteRepo;
    private final ScRfqEventRepository eventRepo;
    private final FeatureGuard featureGuard;
    private final JdbcTemplate jdbc;

    /** One person's standing at one supplier of one company. */
    record Access(UUID tenantId, UUID supplierId, String supplierName, String buyerName) {}

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RfqSummary> rfqs(UUID portalUserId) {
        List<RfqSummary> out = new ArrayList<>();
        for (Access a : accessFor(portalUserId)) {
            out.addAll(jdbc.query("""
                    SELECT r.id, r.rfq_number, r.title, r.status, r.closes_at, r.currency, r.required_by_date,
                           i.status AS inv_status, q.status AS q_status, q.landed_cost, q.received_at
                      FROM sc_rfq_invitations i
                      JOIN sc_rfqs r ON r.id = i.rfq_id AND r.tenant_id = i.tenant_id
                      LEFT JOIN sc_quotations q ON q.rfq_id = r.id AND q.tenant_id = i.tenant_id AND q.supplier_id = i.supplier_id
                     WHERE i.tenant_id = ? AND i.supplier_id = ? AND r.status <> 'DRAFT'
                     ORDER BY (r.status = 'OPEN') DESC, r.closes_at NULLS LAST, r.created_at DESC""",
                    (rs, n) -> {
                        Instant closes = rs.getTimestamp("closes_at") == null ? null : rs.getTimestamp("closes_at").toInstant();
                        boolean open = "OPEN".equals(rs.getString("status")) && (closes == null || closes.isAfter(Instant.now()));
                        String qStatus = rs.getString("q_status");
                        return new RfqSummary((UUID) rs.getObject("id"), a.buyerName(), a.supplierName(), rs.getString("rfq_number"),
                                rs.getString("title"), rs.getString("status"), closes, open, rs.getString("currency"),
                                rs.getDate("required_by_date") == null ? null : rs.getDate("required_by_date").toLocalDate(),
                                rs.getString("inv_status"), qStatus, qStatus == null ? null : rs.getBigDecimal("landed_cost"),
                                rs.getTimestamp("received_at") == null ? null : rs.getTimestamp("received_at").toInstant());
                    }, a.tenantId(), a.supplierId()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public RfqDetail rfq(UUID portalUserId, UUID rfqId) {
        Invited inv = invited(portalUserId, rfqId);
        UUID tid = inv.access().tenantId();
        ScRfq rfq = rfqRepo.findByTenantAndId(tid, rfqId).orElseThrow(this::notFound);
        List<RfqLine> lines = rfqLineRepo.findByRfq(tid, rfqId).stream()
                .map(l -> new RfqLine(l.getId(), l.getLineNumber(), l.getItemName(), l.getDescription(), l.getQuantity())).toList();
        ScQuotation q = quoteRepo.findByRfqAndSupplier(tid, rfqId, inv.access().supplierId()).orElse(null);
        return new RfqDetail(summary(inv, rfq, q), rfq.getDescription(), lines, q == null ? null : quotationService.toResponse(q),
                inv.invitation().getDeclineReason());
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public QuotationResponse submit(UUID portalUserId, UUID rfqId, QuoteRequest body) {
        if (body == null) throw new HandyFlowException("A quotation is required", HttpStatus.BAD_REQUEST, "INVALID_QUOTATION");
        Invited inv = invited(portalUserId, rfqId);
        QuotationRequest req = new QuotationRequest(inv.access().supplierId(), body.quoteReference(), body.validUntil(), body.leadTimeDays(),
                body.warrantyMonths(), body.paymentTermsDays(), body.deliveryCost(), body.otherCharges(), body.notes(), body.lines());
        log.info("[SCM] Supplier portal quotation: rfq={} supplier={} by portal user {}", rfqId, inv.access().supplierName(), portalUserId);
        return quotationService.submit(TenantId.of(inv.access().tenantId()), rfqId, portalUserId, actorName(portalUserId, inv.access()), req);
    }

    @Transactional
    public QuotationResponse attachFile(UUID portalUserId, UUID rfqId, MultipartFile file) {
        Invited inv = invited(portalUserId, rfqId);
        requireOpen(inv.access().tenantId(), rfqId);
        ScQuotation q = quoteRepo.findByRfqAndSupplier(inv.access().tenantId(), rfqId, inv.access().supplierId())
                .orElseThrow(() -> new HandyFlowException("Submit your quotation first, then attach the document", HttpStatus.CONFLICT, "NO_QUOTATION"));
        return quotationService.attachFile(TenantId.of(inv.access().tenantId()), rfqId, q.getId(), file, portalUserId, actorName(portalUserId, inv.access()));
    }

    @Transactional
    public QuotationResponse withdraw(UUID portalUserId, UUID rfqId, String reason) {
        Invited inv = invited(portalUserId, rfqId);
        requireOpen(inv.access().tenantId(), rfqId);
        ScQuotation q = quoteRepo.findByRfqAndSupplier(inv.access().tenantId(), rfqId, inv.access().supplierId())
                .orElseThrow(() -> new HandyFlowException("You have no quotation on this RFQ", HttpStatus.CONFLICT, "NO_QUOTATION"));
        if (q.getStatus() != QuotationStatus.SUBMITTED)
            throw new HandyFlowException("Your quotation is " + q.getStatus() + " and cannot be withdrawn", HttpStatus.CONFLICT, "INVALID_QUOTATION_STATE");
        return quotationService.withdraw(TenantId.of(inv.access().tenantId()), rfqId, q.getId(), reason, portalUserId, actorName(portalUserId, inv.access()));
    }

    @Transactional
    public RfqDetail decline(UUID portalUserId, UUID rfqId, String reason) {
        Invited inv = invited(portalUserId, rfqId);
        UUID tid = inv.access().tenantId();
        requireOpen(tid, rfqId);
        if (reason == null || reason.isBlank()) throw new HandyFlowException("Tell the buyer why you are declining", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        boolean hasLiveQuote = quoteRepo.findByRfqAndSupplier(tid, rfqId, inv.access().supplierId())
                .filter(q -> q.getStatus() == QuotationStatus.SUBMITTED).isPresent();
        if (hasLiveQuote) throw new HandyFlowException("You have a quotation on this RFQ: withdraw it first", HttpStatus.CONFLICT, "HAS_QUOTATION");
        ScRfqInvitation i = inv.invitation();
        i.decline(reason.trim());
        inviteRepo.save(i);
        eventRepo.save(ScRfqEvent.of(tid, rfqId, "QUOTE_DECLINED", inv.access().supplierName() + ": " + reason.trim(), portalUserId,
                actorName(portalUserId, inv.access())));
        return rfq(portalUserId, rfqId);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private record Invited(Access access, ScRfqInvitation invitation) {}

    /** The person's own supplier invited to this RFQ. 404 (not 403) otherwise, so RFQ ids cannot be probed. */
    private Invited invited(UUID portalUserId, UUID rfqId) {
        List<Invited> hits = new ArrayList<>();
        for (Access a : accessFor(portalUserId))
            inviteRepo.findByRfqAndSupplier(a.tenantId(), rfqId, a.supplierId()).ifPresent(i -> hits.add(new Invited(a, i)));
        if (hits.isEmpty()) throw notFound();
        if (hits.size() > 1)
            throw new HandyFlowException("You represent more than one supplier invited to this RFQ; ask the buyer to remove the duplicate access",
                    HttpStatus.CONFLICT, "AMBIGUOUS_SUPPLIER");
        return hits.get(0);
    }

    List<Access> accessFor(UUID portalUserId) {
        List<Access> out = new ArrayList<>();
        for (ScSupplierPortalGrant g : grantRepo.findActiveForPortalUser(portalUserId)) {
            if (!featureGuard.hasModuleFor(TenantId.of(g.getTenantId()), "supply_chain")) continue;
            String supplier = jdbc.query("SELECT name FROM sc_suppliers WHERE id = ? AND tenant_id = ?", (rs, n) -> rs.getString(1),
                    g.getSupplierId(), g.getTenantId()).stream().findFirst().orElse(null);
            if (supplier == null) continue;
            String buyer = jdbc.query("SELECT name FROM tenants WHERE id = ?", (rs, n) -> rs.getString(1), g.getTenantId())
                    .stream().findFirst().orElse("Buyer");
            out.add(new Access(g.getTenantId(), g.getSupplierId(), supplier, buyer));
        }
        return out;
    }

    private void requireOpen(UUID tid, UUID rfqId) {
        ScRfq rfq = rfqRepo.findByTenantAndId(tid, rfqId).orElseThrow(this::notFound);
        if (!rfq.isOpenForQuotes(Instant.now()))
            throw new HandyFlowException("This RFQ is no longer open for changes", HttpStatus.CONFLICT, "INVALID_RFQ_STATE");
    }

    private RfqSummary summary(Invited inv, ScRfq rfq, ScQuotation q) {
        return new RfqSummary(rfq.getId(), inv.access().buyerName(), inv.access().supplierName(), rfq.getRfqNumber(), rfq.getTitle(),
                rfq.getStatus().name(), rfq.getClosesAt(), rfq.isOpenForQuotes(Instant.now()), rfq.getCurrency(), rfq.getRequiredByDate(),
                inv.invitation().getStatus(), q == null ? null : q.getStatus().name(), q == null ? null : q.getLandedCost(),
                q == null ? null : q.getReceivedAt());
    }

    private String actorName(UUID portalUserId, Access a) {
        String name = portalUserRepo.findById(portalUserId).map(PortalUser::getFullName).orElse("Supplier user");
        return name + " (" + a.supplierName() + ", supplier portal)";
    }

    private HandyFlowException notFound() { return new HandyFlowException("RFQ not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
}
