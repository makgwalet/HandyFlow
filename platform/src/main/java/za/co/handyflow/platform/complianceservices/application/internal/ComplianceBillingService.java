package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.BillingDtos.*;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Billing events: the scheduled bills of active agreements. A person raises one as a DRAFT invoice in Invoicing, where
 * someone reviews and issues it. Nothing here issues an invoice, and an event can hold only one invoice at a time.
 */
@Service
@RequiredArgsConstructor
public class ComplianceBillingService {

    private final ComplianceClientRepository clients;
    private final ComplianceAgreementRepository agreements;
    private final ComplianceBillingEventRepository events;
    private final InvoicingFacade invoicing;

    /** Makes any missing events for an active agreement, as far ahead as the horizon allows. Running it again adds only what is new. */
    @Transactional
    public Generated generate(TenantId tenantId, UUID clientId, UUID agreementId, LocalDate today) {
        ComplianceAgreement a = findAgreement(tenantId, clientId, agreementId);
        if (!AgreementRules.ACTIVE.equals(a.getStatus())) {
            throw new HandyFlowException("Only an active agreement has bills. This one is " + a.getStatus().toLowerCase(), HttpStatus.CONFLICT, "BILLING_AGREEMENT_NOT_ACTIVE");
        }
        Set<LocalDate> have = new HashSet<>();
        for (ComplianceBillingEvent e : events.findForAgreement(tenantId, agreementId)) have.add(e.getDueDate());
        List<LocalDate> schedule = AgreementRules.schedule(a.getFirstBillingDate(), a.getEndDate(), a.getBillingFrequency());
        List<ComplianceBillingEvent> made = new ArrayList<>();
        for (LocalDate d : BillingRules.datesToGenerate(schedule, have, today)) made.add(ComplianceBillingEvent.schedule(tenantId, a, d));
        events.saveAll(made);
        return new Generated(made.size(), forAgreement(tenantId, clientId, agreementId, today));
    }

    @Transactional(readOnly = true)
    public List<EventView> forAgreement(TenantId tenantId, UUID clientId, UUID agreementId, LocalDate today) {
        findAgreement(tenantId, clientId, agreementId);
        return views(tenantId, events.findForAgreement(tenantId, agreementId), today);
    }

    /** Across all clients. The filter is DUE (to raise now or soon), UPCOMING, INVOICED, SKIPPED, or blank for all. */
    @Transactional(readOnly = true)
    public List<EventView> listAll(TenantId tenantId, String filter, LocalDate today) {
        String want = filter == null || filter.isBlank() ? null : filter.trim().toUpperCase();
        List<ComplianceBillingEvent> found = new ArrayList<>();
        for (ComplianceBillingEvent e : events.findAllForTenant(tenantId)) {
            String stage = BillingRules.stage(e.getStatus(), e.getDueDate(), today);
            boolean due = "DUE".equals(stage) || "OVERDUE_TO_RAISE".equals(stage);
            if (want == null || want.equals(e.getStatus()) || want.equals(stage) || ("DUE".equals(want) && due)) found.add(e);
        }
        return views(tenantId, found, today);
    }

    @Transactional
    public EventView raise(TenantId tenantId, UUID clientId, UUID eventId, UUID by, LocalDate today) {
        ComplianceBillingEvent e = findEvent(tenantId, clientId, eventId);
        ComplianceAgreement a = agreements.findOne(tenantId, e.getAgreementId()).orElseThrow(() -> new ResourceNotFoundException("ComplianceAgreement", e.getAgreementId().toString()));
        ComplianceClient c = clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
        String problem = BillingRules.raiseProblem(e.getStatus(), e.getDueDate(), a.getStatus(), a.getEndDate(), c.getCrmCustomerId() != null, today);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "BILLING_CANNOT_RAISE");
        InvoicingFacade.VatTreatment vat = invoicing.vatTreatment(tenantId, e.getDueDate());
        BigDecimal rate = vat.registered() && vat.ratePercent() != null ? vat.ratePercent() : BigDecimal.ZERO;
        InvoicingFacade.DraftInvoiceLine line = new InvoicingFacade.DraftInvoiceLine(e.getDescription(), "each", BigDecimal.ONE, e.getAmount(), rate);
        InvoicingFacade.DraftInvoiceResult made;
        try {
            made = invoicing.createDraftInvoice(tenantId, new InvoicingFacade.DraftInvoice(c.getCrmCustomerId(), a.getTitle(), a.getAgreementNumber(), null, List.of(line)));
        } catch (IllegalArgumentException ex) {
            throw new HandyFlowException(ex.getMessage(), HttpStatus.BAD_REQUEST, "BILLING_INVOICE_REFUSED");
        }
        guard(() -> { e.invoiced(by, made.id(), made.invoiceNumber()); return null; });
        events.save(e);
        return views(tenantId, List.of(e), today).get(0);
    }

    @Transactional
    public EventView skip(TenantId tenantId, UUID clientId, UUID eventId, String reason, LocalDate today) {
        ComplianceBillingEvent e = findEvent(tenantId, clientId, eventId);
        guard(() -> { e.skip(reason); return null; });
        events.save(e);
        return views(tenantId, List.of(e), today).get(0);
    }

    @Transactional
    public EventView restore(TenantId tenantId, UUID clientId, UUID eventId, LocalDate today) {
        ComplianceBillingEvent e = findEvent(tenantId, clientId, eventId);
        guard(() -> { e.restore(); return null; });
        events.save(e);
        return views(tenantId, List.of(e), today).get(0);
    }

    /** The invoice must be cancelled, or gone, in Invoicing. This never cancels an invoice. */
    @Transactional
    public EventView release(TenantId tenantId, UUID clientId, UUID eventId, String reason, LocalDate today) {
        ComplianceBillingEvent e = findEvent(tenantId, clientId, eventId);
        String status = e.getInvoiceId() == null ? null : invoicing.findInvoiceStatus(tenantId, e.getInvoiceId()).orElse(null);
        guard(() -> { e.release(status, reason); return null; });
        events.save(e);
        return views(tenantId, List.of(e), today).get(0);
    }

    // ---- helpers ----

    private List<EventView> views(TenantId tenantId, List<ComplianceBillingEvent> found, LocalDate today) {
        List<UUID> invoiceIds = new ArrayList<>();
        for (ComplianceBillingEvent e : found) if (e.getInvoiceId() != null) invoiceIds.add(e.getInvoiceId());
        Map<UUID, InvoicingFacade.InvoiceSummary> byInvoice = new HashMap<>();
        if (!invoiceIds.isEmpty()) {
            for (InvoicingFacade.InvoiceSummary s : invoicing.findInvoiceSummaries(tenantId, invoiceIds)) byInvoice.put(s.id(), s);
        }
        Map<UUID, ComplianceAgreement> agreementCache = new HashMap<>();
        Map<UUID, ComplianceClient> clientCache = new HashMap<>();
        List<EventView> out = new ArrayList<>();
        for (ComplianceBillingEvent e : found) {
            ComplianceAgreement a = agreementCache.computeIfAbsent(e.getAgreementId(), id -> agreements.findOne(tenantId, id).orElse(null));
            ComplianceClient c = clientCache.computeIfAbsent(e.getClientId(), id -> clients.findByIdForTenant(tenantId, id).orElse(null));
            InvoicingFacade.InvoiceSummary s = e.getInvoiceId() == null ? null : byInvoice.get(e.getInvoiceId());
            InvoiceInfo info = s == null ? null : new InvoiceInfo(s.status(), s.total(), s.amountPaid(), s.creditedAmount());
            String blocked = null;
            if (BillingRules.SCHEDULED.equals(e.getStatus()) && a != null) {
                blocked = BillingRules.raiseProblem(e.getStatus(), e.getDueDate(), a.getStatus(), a.getEndDate(), c != null && c.getCrmCustomerId() != null, today);
            }
            List<String> actions = new ArrayList<>(BillingRules.actionsFor(e.getStatus()));
            if (blocked != null) actions.remove("raise");
            out.add(new EventView(e.getId(), e.getAgreementId(), a == null ? null : a.getAgreementNumber(), e.getClientId(), c == null ? null : c.getName(), e.getDueDate(),
                    e.getAmount(), e.getDescription(), e.getStatus(), BillingRules.stage(e.getStatus(), e.getDueDate(), today),
                    BillingRules.SCHEDULED.equals(e.getStatus()) ? BillingRules.daysLate(e.getDueDate(), today) : 0, e.getInvoiceId(), e.getInvoiceNumber(), info,
                    e.getRaisedAt(), e.getSkipReason(), e.getReleaseNote(), actions, blocked));
        }
        return out;
    }

    private ComplianceAgreement findAgreement(TenantId tenantId, UUID clientId, UUID agreementId) {
        ComplianceAgreement a = agreements.findOne(tenantId, agreementId).orElseThrow(() -> new ResourceNotFoundException("ComplianceAgreement", agreementId.toString()));
        if (!a.getClientId().equals(clientId)) throw new ResourceNotFoundException("ComplianceAgreement", agreementId.toString());
        return a;
    }

    private ComplianceBillingEvent findEvent(TenantId tenantId, UUID clientId, UUID eventId) {
        ComplianceBillingEvent e = events.findOne(tenantId, eventId).orElseThrow(() -> new ResourceNotFoundException("ComplianceBillingEvent", eventId.toString()));
        if (!e.getClientId().equals(clientId)) throw new ResourceNotFoundException("ComplianceBillingEvent", eventId.toString());
        return e;
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "BILLING_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "BILLING_INVALID");
        }
    }
}
