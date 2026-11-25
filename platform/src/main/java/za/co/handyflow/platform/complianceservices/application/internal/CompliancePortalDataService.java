package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PortalDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * What a signed-in client contact sees. A portal login is not tied to a tenant, so the tenant and client come from the
 * caller's active grants and a client id they hold no grant for is refused, never trusted. Every query below is
 * scoped to that tenant and client. Read only: nothing here changes a quote, agreement or invoice.
 */
@Service
@RequiredArgsConstructor
public class CompliancePortalDataService {

    private static final int NEXT_BILLS = 6;

    private final CompliancePortalGrantRepository grants;
    private final ComplianceClientRepository clients;
    private final ComplianceQuoteRepository quotes;
    private final ComplianceQuoteLineRepository quoteLines;
    private final ComplianceAgreementRepository agreements;
    private final ComplianceAgreementLineRepository agreementLines;
    private final ComplianceBillingEventRepository billing;
    private final InvoicingFacade invoicing;

    public record Scope(TenantId tenantId, UUID clientId) {}

    @Transactional(readOnly = true)
    public List<ClientRef> myClients(UUID portalUserId) {
        List<ClientRef> out = new ArrayList<>();
        for (Scope s : distinct(portalUserId)) {
            String name = clients.findByIdForTenant(s.tenantId(), s.clientId()).map(ComplianceClient::getName).orElse("Client");
            out.add(new ClientRef(s.clientId(), name));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Scope resolveScope(UUID portalUserId, UUID selectedClientId) {
        List<Scope> mine = distinct(portalUserId);
        if (mine.isEmpty()) throw new HandyFlowException("No active client access found for this portal account", HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS");
        if (selectedClientId != null) {
            for (Scope s : mine) if (s.clientId().equals(selectedClientId)) return s;
            throw new HandyFlowException("You do not have access to that client", HttpStatus.FORBIDDEN, "NO_CLIENT_ACCESS");
        }
        if (mine.size() == 1) return mine.get(0);
        throw new HandyFlowException("You have access to several clients. Say which one with clientId", HttpStatus.BAD_REQUEST, "CLIENT_SELECTION_REQUIRED");
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID portalUserId, UUID selectedClientId, LocalDate today) {
        Scope s = resolveScope(portalUserId, selectedClientId);
        String name = clients.findByIdForTenant(s.tenantId(), s.clientId()).map(ComplianceClient::getName).orElse("Client");
        List<Invoice> invoices = invoices(s, today);
        BigDecimal owing = BigDecimal.ZERO;
        for (Invoice i : invoices) owing = owing.add(i.outstanding());
        return new Overview(new ClientRef(s.clientId(), name), quotes(s, today), agreements(s, today), invoices, owing);
    }

    @Transactional(readOnly = true)
    public List<Quote> quotes(UUID portalUserId, UUID selectedClientId, LocalDate today) {
        return quotes(resolveScope(portalUserId, selectedClientId), today);
    }

    @Transactional(readOnly = true)
    public List<Agreement> agreements(UUID portalUserId, UUID selectedClientId, LocalDate today) {
        return agreements(resolveScope(portalUserId, selectedClientId), today);
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoices(UUID portalUserId, UUID selectedClientId, LocalDate today) {
        return invoices(resolveScope(portalUserId, selectedClientId), today);
    }

    // ---- views ----

    private List<Quote> quotes(Scope s, LocalDate today) {
        List<ComplianceQuote> visible = new ArrayList<>();
        for (ComplianceQuote q : quotes.findForClient(s.tenantId(), s.clientId())) if (PortalRules.quoteVisible(q.getStatus())) visible.add(q);
        List<UUID> ids = new ArrayList<>();
        for (ComplianceQuote q : visible) ids.add(q.getId());
        Map<UUID, List<QuoteLine>> byQuote = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ComplianceQuoteLine l : quoteLines.findForQuotes(s.tenantId(), ids)) {
                byQuote.computeIfAbsent(l.getQuoteId(), k -> new ArrayList<>()).add(new QuoteLine(l.getServiceCode(), l.getServiceName(), l.getDescription(),
                        l.getBeforeDiscount(), l.getDiscount(), l.getProfessional(), l.getDisbursements(), l.getVat(), l.getTotal()));
            }
        }
        List<Quote> out = new ArrayList<>();
        for (ComplianceQuote q : visible) {
            out.add(new Quote(q.getId(), q.getQuoteNumber(), q.getTitle(), q.effectiveStatus(today), q.getValidUntil(), q.getSentAt(), q.getCurrency(), q.getProfessional(),
                    q.getDiscountTotal(), q.getDisbursements(), q.getVat(), q.getVatRatePct(), q.getTotal(), q.getNotes(), q.getTerms(),
                    byQuote.getOrDefault(q.getId(), List.of())));
        }
        return out;
    }

    private List<Agreement> agreements(Scope s, LocalDate today) {
        List<ComplianceAgreement> visible = new ArrayList<>();
        for (ComplianceAgreement a : agreements.findForClient(s.tenantId(), s.clientId())) if (PortalRules.agreementVisible(a.getStatus())) visible.add(a);
        List<UUID> ids = new ArrayList<>();
        for (ComplianceAgreement a : visible) ids.add(a.getId());
        Map<UUID, List<AgreementLine>> scope = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ComplianceAgreementLine l : agreementLines.findForAgreements(s.tenantId(), ids)) {
                scope.computeIfAbsent(l.getAgreementId(), k -> new ArrayList<>()).add(new AgreementLine(l.getServiceCode(), l.getServiceName(), l.getDescription()));
            }
        }
        List<Agreement> out = new ArrayList<>();
        for (ComplianceAgreement a : visible) {
            List<Billing> next = new ArrayList<>();
            if (AgreementRules.ACTIVE.equals(a.getStatus())) {
                for (LocalDate d : AgreementRules.schedule(a.getFirstBillingDate(), a.getEndDate(), a.getBillingFrequency())) {
                    if (PortalRules.upcoming(d, today) && next.size() < NEXT_BILLS) next.add(new Billing(d, a.getBillingAmount()));
                }
            }
            out.add(new Agreement(a.getId(), a.getAgreementNumber(), a.getTitle(), a.getStatus(), a.getStartDate(), a.getEndDate(), a.getBillingFrequency(), a.getBillingAmount(),
                    a.isAutoRenew(), a.getNoticeDays(), a.getTerms(), a.getCurrency(), scope.getOrDefault(a.getId(), List.of()), next));
        }
        return out;
    }

    /** Invoices raised from this client's billing events, once they are past draft and not cancelled. */
    private List<Invoice> invoices(Scope s, LocalDate today) {
        List<ComplianceBillingEvent> raised = new ArrayList<>();
        for (ComplianceAgreement a : agreements.findForClient(s.tenantId(), s.clientId())) {
            for (ComplianceBillingEvent e : billing.findForAgreement(s.tenantId(), a.getId())) {
                if (BillingRules.INVOICED.equals(e.getStatus()) && e.getInvoiceId() != null) raised.add(e);
            }
        }
        List<UUID> ids = new ArrayList<>();
        for (ComplianceBillingEvent e : raised) ids.add(e.getInvoiceId());
        Map<UUID, InvoicingFacade.InvoiceSummary> found = new HashMap<>();
        if (!ids.isEmpty()) for (InvoicingFacade.InvoiceSummary i : invoicing.findInvoiceSummaries(s.tenantId(), ids)) found.put(i.id(), i);
        List<Invoice> out = new ArrayList<>();
        for (ComplianceBillingEvent e : raised) {
            InvoicingFacade.InvoiceSummary i = found.get(e.getInvoiceId());
            if (i == null || !PortalRules.invoiceVisible(i.status())) continue;
            out.add(new Invoice(i.invoiceNumber(), i.status(), e.getDueDate(), e.getDescription(), i.total(), i.amountPaid() == null ? BigDecimal.ZERO : i.amountPaid(),
                    i.creditedAmount() == null ? BigDecimal.ZERO : i.creditedAmount(), PortalRules.outstanding(i.total(), i.amountPaid(), i.creditedAmount())));
        }
        out.sort(Comparator.comparing(Invoice::billingDate).reversed());
        return out;
    }

    /** One entry per tenant and client, even if a person was invited twice. */
    private List<Scope> distinct(UUID portalUserId) {
        Map<String, Scope> seen = new LinkedHashMap<>();
        for (CompliancePortalGrant g : grants.findActiveForUser(portalUserId)) {
            seen.putIfAbsent(g.getTenantId() + ":" + g.getClientId(), new Scope(TenantId.of(g.getTenantId()), g.getClientId()));
        }
        return new ArrayList<>(seen.values());
    }
}
