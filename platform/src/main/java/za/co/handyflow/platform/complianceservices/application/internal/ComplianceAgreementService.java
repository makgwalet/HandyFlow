package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.AgreementDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Agreements follow accepted quotes. The scope is copied from the quote so it stays as it was agreed. A person
 * activates an agreement and records how the client confirmed. The billing schedule is a preview only: nothing is
 * invoiced from here yet.
 */
@Service
@RequiredArgsConstructor
public class ComplianceAgreementService {

    private final ComplianceClientRepository clients;
    private final ComplianceQuoteRepository quotes;
    private final ComplianceQuoteLineRepository quoteLines;
    private final ComplianceAgreementRepository agreements;
    private final ComplianceAgreementLineRepository lines;
    private final TenantSequenceService sequences;

    @Transactional(readOnly = true)
    public List<Summary> listForClient(TenantId tenantId, UUID clientId, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        return summaries(tenantId, agreements.findForClient(tenantId, clientId), Map.of(clientId, c.getName()), today);
    }

    /** Across all clients, optionally only those with a given status. */
    @Transactional(readOnly = true)
    public List<Summary> listAll(TenantId tenantId, String status, LocalDate today) {
        String want = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<ComplianceAgreement> found = new ArrayList<>();
        for (ComplianceAgreement a : agreements.findAllForTenant(tenantId)) {
            if (want == null || want.equals(a.getStatus())) found.add(a);
        }
        Map<UUID, String> names = new HashMap<>();
        for (ComplianceAgreement a : found) {
            if (names.containsKey(a.getClientId())) continue;
            names.put(a.getClientId(), clients.findByIdForTenant(tenantId, a.getClientId()).map(ComplianceClient::getName).orElse(null));
        }
        return summaries(tenantId, found, names, today);
    }

    /** Accepted quotes for this client that no live agreement has used yet. */
    @Transactional(readOnly = true)
    public List<QuoteOption> availableQuotes(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        Set<UUID> used = new HashSet<>(agreements.findLiveQuoteIds(tenantId, clientId));
        List<QuoteOption> out = new ArrayList<>();
        for (ComplianceQuote q : quotes.findForClient(tenantId, clientId)) {
            if (!QuoteRules.ACCEPTED.equals(q.getStatus()) || used.contains(q.getId())) continue;
            out.add(new QuoteOption(q.getId(), q.getQuoteNumber(), q.getTitle(), q.getProfessional(), q.getTotal(), q.getDecidedAt()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public View get(TenantId tenantId, UUID clientId, UUID id, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        return view(tenantId, find(tenantId, clientId, id), c.getName(), today);
    }

    @Transactional
    public View create(TenantId tenantId, UUID clientId, CreateRequest r, UUID by, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceQuote q = quotes.findOne(tenantId, r.quoteId()).orElseThrow(() -> new ResourceNotFoundException("ComplianceQuote", r.quoteId().toString()));
        if (!q.getClientId().equals(clientId)) throw new ResourceNotFoundException("ComplianceQuote", r.quoteId().toString());
        if (!QuoteRules.ACCEPTED.equals(q.getStatus())) {
            throw new HandyFlowException("Only an accepted quote can become an agreement. This one is " + q.getStatus().toLowerCase().replace('_', ' '), HttpStatus.CONFLICT, "AGREEMENT_QUOTE_NOT_ACCEPTED");
        }
        Optional<ComplianceAgreement> held = agreements.findLiveForQuote(tenantId, q.getId());
        if (held.isPresent()) {
            throw new HandyFlowException("This quote already has agreement " + held.get().getAgreementNumber() + ". Cancel that draft or end it first", HttpStatus.CONFLICT, "AGREEMENT_QUOTE_USED");
        }
        ComplianceAgreement.Terms terms = terms(r.terms(), q, today);
        String number = String.format("CA-%06d", sequences.nextValue(tenantId, "CS_AGREEMENT"));
        ComplianceAgreement a = guard(() -> ComplianceAgreement.draft(tenantId, clientId, q.getId(), number, q.getProfessional(), q.getTotal(), q.getCurrency(), terms, by));
        agreements.save(a);
        List<ComplianceAgreementLine> copied = new ArrayList<>();
        for (ComplianceQuoteLine ql : quoteLines.findForQuotes(tenantId, List.of(q.getId()))) copied.add(ComplianceAgreementLine.from(tenantId, a.getId(), ql));
        lines.saveAll(copied);
        return view(tenantId, a, c.getName(), today);
    }

    @Transactional
    public View save(TenantId tenantId, UUID clientId, UUID id, TermsRequest r, LocalDate today) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceAgreement a = find(tenantId, clientId, id);
        ComplianceQuote q = quotes.findOne(tenantId, a.getQuoteId()).orElseThrow(() -> new ResourceNotFoundException("ComplianceQuote", a.getQuoteId().toString()));
        ComplianceAgreement.Terms terms = terms(r, q, today);
        guard(() -> { a.edit(terms); return null; });
        agreements.save(a);
        return view(tenantId, a, c.getName(), today);
    }

    @Transactional
    public View activate(TenantId tenantId, UUID clientId, UUID id, String note, UUID by, LocalDate today) {
        return change(tenantId, clientId, id, today, a -> a.activate(by, note));
    }

    @Transactional
    public View suspend(TenantId tenantId, UUID clientId, UUID id, String reason, LocalDate today) {
        return change(tenantId, clientId, id, today, a -> a.suspend(reason));
    }

    @Transactional
    public View resume(TenantId tenantId, UUID clientId, UUID id, LocalDate today) {
        return change(tenantId, clientId, id, today, ComplianceAgreement::resume);
    }

    @Transactional
    public View end(TenantId tenantId, UUID clientId, UUID id, EndRequest r, UUID by, LocalDate today) {
        final LocalDate date = r == null || r.endDate() == null ? today : r.endDate();
        final String reason = r == null ? null : r.reason();
        return change(tenantId, clientId, id, today, a -> a.end(by, date, reason));
    }

    @Transactional
    public View cancel(TenantId tenantId, UUID clientId, UUID id, String reason, LocalDate today) {
        return change(tenantId, clientId, id, today, a -> a.cancel(reason));
    }

    // ---- helpers ----

    private View change(TenantId tenantId, UUID clientId, UUID id, LocalDate today, java.util.function.Consumer<ComplianceAgreement> action) {
        ComplianceClient c = requireClient(tenantId, clientId);
        ComplianceAgreement a = find(tenantId, clientId, id);
        guard(() -> { action.accept(a); return null; });
        agreements.save(a);
        return view(tenantId, a, c.getName(), today);
    }

    /** Fills anything left blank from the quote and today. */
    private static ComplianceAgreement.Terms terms(TermsRequest r, ComplianceQuote q, LocalDate today) {
        TermsRequest in = r == null ? new TermsRequest(null, null, null, null, null, null, null, null, null, null) : r;
        LocalDate start = in.startDate() == null ? today : in.startDate();
        String freq = in.billingFrequency() == null || in.billingFrequency().isBlank() ? AgreementRules.ONCE : in.billingFrequency();
        BigDecimal amount = in.billingAmount() == null ? q.getProfessional() : in.billingAmount();
        LocalDate first = in.firstBillingDate() == null ? start : in.firstBillingDate();
        String title = in.title() == null || in.title().isBlank() ? q.getTitle() : in.title();
        return new ComplianceAgreement.Terms(title, start, in.endDate(), freq, amount, first, Boolean.TRUE.equals(in.autoRenew()),
                in.noticeDays() == null ? 30 : in.noticeDays(), in.notes(), in.terms());
    }

    private List<Summary> summaries(TenantId tenantId, List<ComplianceAgreement> found, Map<UUID, String> names, LocalDate today) {
        Map<UUID, String> quoteNumbers = new HashMap<>();
        List<Summary> out = new ArrayList<>();
        for (ComplianceAgreement a : found) {
            if (!quoteNumbers.containsKey(a.getQuoteId())) {
                quoteNumbers.put(a.getQuoteId(), quotes.findOne(tenantId, a.getQuoteId()).map(ComplianceQuote::getQuoteNumber).orElse(null));
            }
            LocalDate next = AgreementRules.ACTIVE.equals(a.getStatus()) || AgreementRules.DRAFT.equals(a.getStatus())
                    ? AgreementRules.nextBilling(a.getFirstBillingDate(), a.getEndDate(), a.getBillingFrequency(), today) : null;
            out.add(new Summary(a.getId(), a.getClientId(), names.get(a.getClientId()), a.getQuoteId(), quoteNumbers.get(a.getQuoteId()), a.getAgreementNumber(),
                    a.getTitle(), a.getStatus(), a.getStartDate(), a.getEndDate(), a.getBillingFrequency(), a.getBillingAmount(), next,
                    AgreementRules.attention(a.getStatus(), a.getEndDate(), a.isAutoRenew(), a.getNoticeDays(), today)));
        }
        return out;
    }

    private View view(TenantId tenantId, ComplianceAgreement a, String clientName, LocalDate today) {
        List<LineView> lineViews = new ArrayList<>();
        for (ComplianceAgreementLine l : lines.findForAgreements(tenantId, List.of(a.getId()))) {
            lineViews.add(new LineView(l.getId(), l.getServiceCode(), l.getServiceName(), l.getServiceVersion(), l.getPricingModel(), l.getDescription(), l.getProfessional()));
        }
        String quoteNumber = quotes.findOne(tenantId, a.getQuoteId()).map(ComplianceQuote::getQuoteNumber).orElse(null);
        List<AgreementRules.Entry> entries = AgreementRules.entries(a.getFirstBillingDate(), a.getEndDate(), a.getBillingFrequency(), a.getBillingAmount());
        List<ScheduleEntry> schedule = new ArrayList<>();
        for (AgreementRules.Entry e : entries) schedule.add(new ScheduleEntry(e.date(), e.amount()));
        boolean truncated = a.getEndDate() == null && !AgreementRules.ONCE.equals(a.getBillingFrequency()) && entries.size() >= AgreementRules.MAX_OPEN_DATES;
        BigDecimal contract = AgreementRules.contractValue(a.getFirstBillingDate(), a.getEndDate(), a.getBillingFrequency(), a.getBillingAmount());
        LocalDate next = AgreementRules.nextBilling(a.getFirstBillingDate(), a.getEndDate(), a.getBillingFrequency(), today);
        return new View(a.getId(), a.getClientId(), clientName, a.getQuoteId(), quoteNumber, a.getAgreementNumber(), a.getTitle(), a.getStatus(),
                a.getStartDate(), a.getEndDate(), a.getBillingFrequency(), a.getBillingAmount(), a.getFirstBillingDate(), a.isAutoRenew(), a.getNoticeDays(),
                a.getNotes(), a.getTerms(), a.getQuotedProfessional(), a.getQuotedTotal(), a.getCurrency(), contract,
                AgreementRules.comparisonNote(contract, a.getQuotedProfessional()),
                AgreementRules.attention(a.getStatus(), a.getEndDate(), a.isAutoRenew(), a.getNoticeDays(), today), next, truncated, schedule, lineViews,
                AgreementRules.actionsFor(a.getStatus()), AgreementRules.canEdit(a.getStatus()), a.getActivatedBy(), a.getActivatedAt(), a.getActivationNote(),
                a.getSuspendedReason(), a.getEndedBy(), a.getEndedAt(), a.getEndReason(), a.getCreatedAt(), a.getVersion());
    }

    private ComplianceClient requireClient(TenantId tenantId, UUID clientId) {
        return clients.findByIdForTenant(tenantId, clientId).orElseThrow(() -> new ResourceNotFoundException("ComplianceClient", clientId.toString()));
    }

    private ComplianceAgreement find(TenantId tenantId, UUID clientId, UUID id) {
        ComplianceAgreement a = agreements.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceAgreement", id.toString()));
        if (!a.getClientId().equals(clientId)) throw new ResourceNotFoundException("ComplianceAgreement", id.toString());
        return a;
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "AGREEMENT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "AGREEMENT_INVALID");
        }
    }
}
