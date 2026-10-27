package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmPortalAccessGrant;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalClientResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalSummaryResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Client-facing reads — everything a logged-in portal user (acting for one client) may see: their own
 * sites, work orders, invoices and credit notes, never another client's.
 * <p>
 * <b>Who is asking, and for what client, comes only from the portal user's ACTIVE grants.</b> A portal token is
 * deliberately not tenant-scoped (see {@code PortalJwtService}), so there is no tenant in {@code TenantContext}
 * on these requests: the tenant and client are read off the grant. A person who is a contact for several clients
 * (or tenants) names the one they mean with {@code clientId}; with exactly one, it is implied. A {@code clientId}
 * the person holds no active grant for is refused — never trusted.
 * <p>
 * Visibility: DRAFT/APPROVED/CANCELLED invoices are never shown (only SENT, PARTIAL, PAID and fully CREDITED ones);
 * only approved credit notes; REQUESTED/REJECTED work orders only when the client raised them (shown as
 * SUBMITTED / DECLINED); PENDING_VERIFICATION is presented as IN_PROGRESS.
 */
@Service
@RequiredArgsConstructor
public class FmPortalDataService {

    /** An invoice is shown to a client only once it has been approved AND sent. */
    static final Set<String> CLIENT_VISIBLE_INVOICE_STATUSES = Set.of("SENT", "PARTIAL", "PAID", "CREDITED");

    private final FmPortalAccessGrantRepository grantRepo;
    private final FmClientRepository clientRepo;
    private final FmSiteRepository siteRepo;
    private final FmWorkOrderRepository workOrderRepo;
    private final FmInvoiceRepository invoiceRepo;
    private final FmCreditNoteRepository creditNoteRepo;

    /** The tenant and client a portal request is about, resolved from the caller's grants. */
    public record Scope(TenantId tenantId, UUID clientId) {}

    // ── Scope ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Scope resolveScope(UUID portalUserId, UUID selectedClientId) {
        Map<String, FmPortalAccessGrant> distinct = new LinkedHashMap<>();
        for (FmPortalAccessGrant g : grantRepo.findByPortalUserId(portalUserId)) {
            distinct.putIfAbsent(g.getTenantId() + ":" + g.getClientId(), g); // a re-invite creates a second grant for the same client
        }
        if (distinct.isEmpty()) {
            throw new HandyFlowException("No active client access found for this portal account",
                    HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS");
        }
        FmPortalAccessGrant chosen;
        if (selectedClientId != null) {
            chosen = distinct.values().stream().filter(g -> g.getClientId().equals(selectedClientId)).findFirst()
                    .orElseThrow(() -> new HandyFlowException("You do not have access to that client",
                            HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS"));
        } else if (distinct.size() == 1) {
            chosen = distinct.values().iterator().next();
        } else {
            throw new HandyFlowException("You have access to several clients — say which one with clientId",
                    HttpStatus.BAD_REQUEST, "CLIENT_SELECTION_REQUIRED");
        }
        return new Scope(TenantId.of(chosen.getTenantId()), chosen.getClientId());
    }

    @Transactional(readOnly = true)
    public List<FmPortalClientResponse> myClients(UUID portalUserId) {
        Map<String, FmPortalAccessGrant> distinct = new LinkedHashMap<>();
        for (FmPortalAccessGrant g : grantRepo.findByPortalUserId(portalUserId)) {
            distinct.putIfAbsent(g.getTenantId() + ":" + g.getClientId(), g);
        }
        List<FmPortalClientResponse> out = new ArrayList<>();
        for (FmPortalAccessGrant g : distinct.values()) {
            clientRepo.findActiveById(TenantId.of(g.getTenantId()), g.getClientId())
                    .ifPresent(c -> out.add(new FmPortalClientResponse(c.getId(), c.getTradingName())));
        }
        return out;
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FmSite> getMySites(UUID portalUserId, UUID selectedClientId, Pageable pageable) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        return siteRepo.findAllActiveForClient(s.tenantId(), s.clientId(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<FmWorkOrder> getMyWorkOrders(UUID portalUserId, UUID selectedClientId, String status, Pageable pageable) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        return workOrderRepo.findAllForClientPortal(s.tenantId(), s.clientId(), FmPortalStatus.filter(status), pageable);
    }

    @Transactional(readOnly = true)
    public FmPortalSummaryResponse getMySummary(UUID portalUserId, UUID selectedClientId) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : workOrderRepo.countByStatusForClientPortal(s.tenantId(), s.clientId())) {
            counts.merge((String) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        List<FmInvoice> payable = invoiceRepo.findAllForClientList(s.tenantId(), s.clientId()).stream()
                .filter(i -> Set.of("SENT", "PARTIAL").contains(i.getStatus())).toList();
        return FmPortalSummary.build(counts, workOrderRepo.findOpenWithSlaForClient(s.tenantId(), s.clientId()),
                payable, Instant.now(), LocalDate.now());
    }

    /** Only invoices the FM company has actually sent: SENT, PARTIAL, PAID or fully CREDITED. */
    @Transactional(readOnly = true)
    public List<FmInvoice> getMyInvoices(UUID portalUserId, UUID selectedClientId) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        return invoiceRepo.findAllForClientList(s.tenantId(), s.clientId()).stream()
                .filter(i -> CLIENT_VISIBLE_INVOICE_STATUSES.contains(i.getStatus()))
                .toList();
    }

    /** Approved credit notes, each paired with its invoice number. Credit notes against a not-yet-sent invoice are not shown. */
    @Transactional(readOnly = true)
    public Map<FmCreditNote, String> getMyCreditNotes(UUID portalUserId, UUID selectedClientId) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        Map<FmCreditNote, String> out = new LinkedHashMap<>();
        for (FmCreditNote c : creditNoteRepo.findApprovedForClient(s.tenantId(), s.clientId())) {
            invoiceRepo.findActiveById(s.tenantId(), c.getInvoiceId())
                    .filter(i -> CLIENT_VISIBLE_INVOICE_STATUSES.contains(i.getStatus()))
                    .ifPresent(i -> out.put(c, i.getInvoiceNumber()));
        }
        return out;
    }

    /** Validates the invoice is the caller's and visible to them; returns the scope for rendering its PDF. */
    @Transactional(readOnly = true)
    public Scope requireMyInvoice(UUID portalUserId, UUID selectedClientId, UUID invoiceId) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        invoiceRepo.findActiveById(s.tenantId(), invoiceId)
                .filter(i -> i.getClientId().equals(s.clientId()) && CLIENT_VISIBLE_INVOICE_STATUSES.contains(i.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("FmInvoice", invoiceId.toString()));
        return s;
    }

    @Transactional(readOnly = true)
    public Scope requireMyCreditNote(UUID portalUserId, UUID selectedClientId, UUID creditNoteId) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        FmCreditNote note = creditNoteRepo.findActiveById(s.tenantId(), creditNoteId)
                .filter(c -> c.getClientId().equals(s.clientId()) && "APPROVED".equals(c.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("FmCreditNote", creditNoteId.toString()));
        invoiceRepo.findActiveById(s.tenantId(), note.getInvoiceId())
                .filter(i -> CLIENT_VISIBLE_INVOICE_STATUSES.contains(i.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("FmCreditNote", creditNoteId.toString()));
        return s;
    }
}
