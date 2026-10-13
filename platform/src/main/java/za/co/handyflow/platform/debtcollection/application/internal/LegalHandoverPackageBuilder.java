package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.debtcollection.domain.model.CollectionContactLog;
import za.co.handyflow.platform.debtcollection.domain.model.Communication;
import za.co.handyflow.platform.debtcollection.domain.model.DebtCollectionCase;
import za.co.handyflow.platform.debtcollection.domain.model.LegalHandover;
import za.co.handyflow.platform.debtcollection.domain.model.LegalNotice;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentPlan;
import za.co.handyflow.platform.debtcollection.domain.model.PromiseToPay;
import za.co.handyflow.platform.debtcollection.domain.repository.CollectionContactLogRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.CommunicationRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.LegalNoticeRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PaymentPlanRepository;
import za.co.handyflow.platform.debtcollection.domain.repository.PromiseToPayRepository;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade.OutstandingInvoiceSummary;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the frozen transfer package: everything Legal Practice needs so nothing is re-captured, with the original
 * ids kept as provenance. The package is a plain JSON snapshot (all values rendered as strings/numbers/booleans,
 * insertion-ordered keys) plus its SHA-256, so what was reviewed is exactly what is transferred and any later
 * difference is detectable.
 * <p>
 * Deliberately NOT included: message bodies and attachments' contents — only metadata, template code/version,
 * tracking references and evidence IDs (data minimisation; Legal Practice pulls the documents it needs by id).
 * Balances are as reported by invoicing at build time; an invoice that has left invoicing's outstanding list is
 * listed as NOT_OUTSTANDING_PER_INVOICING rather than dropped.
 */
@Component
@RequiredArgsConstructor
public class LegalHandoverPackageBuilder {

    public static final String SCHEMA = "handyflow.debtcollection.handover/1";

    public record InvoiceView(List<Map<String, Object>> lines, BigDecimal totalOutstanding, LocalDate oldestDueDate) {}

    public record Built(String json, String hash) {}

    private final InvoicingFacade invoicingFacade;
    private final PromiseToPayRepository ptpRepository;
    private final PaymentPlanRepository planRepository;
    private final CollectionContactLogRepository contactLogRepository;
    private final CommunicationRepository communicationRepository;
    private final LegalNoticeRepository noticeRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    public InvoiceView invoices(TenantId tenantId, DebtCollectionCase c) {
        Map<UUID, OutstandingInvoiceSummary> byId = invoicingFacade.findOutstandingInvoices(tenantId).stream()
                .collect(Collectors.toMap(OutstandingInvoiceSummary::id, Function.identity(), (a, b) -> a));
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        LocalDate oldest = null;
        for (UUID id : c.getLinkedInvoiceIds()) {
            OutstandingInvoiceSummary inv = byId.get(id);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("invoiceId", id.toString());
            if (inv == null) {
                line.put("status", "NOT_OUTSTANDING_PER_INVOICING");
            } else {
                BigDecimal paid = inv.amountPaid() != null ? inv.amountPaid() : BigDecimal.ZERO;
                BigDecimal open = inv.total().subtract(paid);
                line.put("status", "OUTSTANDING");
                line.put("invoiceNumber", inv.invoiceNumber());
                line.put("dueDate", str(inv.dueDate()));
                line.put("total", money(inv.total()));
                line.put("amountPaid", money(paid));
                line.put("outstanding", money(open));
                if (open.signum() > 0) {
                    total = total.add(open);
                    if (inv.dueDate() != null && (oldest == null || inv.dueDate().isBefore(oldest))) oldest = inv.dueDate();
                }
            }
            lines.add(line);
        }
        return new InvoiceView(lines, total, oldest);
    }

    public Built build(TenantId tenantId, LegalHandover h, DebtCollectionCase c, int packageVersion, String builtByName) {
        InvoiceView inv = invoices(tenantId, c);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", SCHEMA);
        root.put("packageVersion", packageVersion);
        root.put("builtAt", str(Instant.now()));
        root.put("builtBy", builtByName);

        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("source", "handyflow-debtcollection");
        prov.put("handoverId", h.getId().toString());
        prov.put("caseId", c.getId().toString());
        prov.put("caseNumber", c.getCaseNumber());
        prov.put("caseOpenedDate", str(c.getOpenedDate()));
        prov.put("caseStatus", str(c.getStatus()));
        root.put("provenance", prov);

        Map<String, Object> debtor = new LinkedHashMap<>();
        debtor.put("customerId", str(c.getCustomerId()));
        debtor.put("name", c.getDebtorName());
        debtor.put("email", c.getDebtorEmail());
        debtor.put("phone", c.getDebtorPhone());
        root.put("debtor", debtor);

        Map<String, Object> authority = new LinkedHashMap<>();
        authority.put("note", h.getCreditorAuthorityNote());
        authority.put("evidenceIds", h.getEvidenceIds().stream().map(UUID::toString).sorted().toList());
        root.put("creditorAuthority", authority);

        Map<String, Object> agreement = new LinkedHashMap<>();
        agreement.put("type", str(h.getAgreementType()));
        agreement.put("linkedContractId", str(c.getLinkedContractId()));
        root.put("agreement", agreement);

        Map<String, Object> prescription = new LinkedHashMap<>();
        prescription.put("reviewed", h.isPrescriptionReviewed());
        prescription.put("note", h.getPrescriptionReviewNote());
        prescription.put("reviewedAt", str(h.getPrescriptionReviewedAt()));
        prescription.put("reviewedBy", str(h.getPrescriptionReviewedBy()));
        prescription.put("oldestOutstandingInvoiceDueDate", str(inv.oldestDueDate()));
        root.put("prescriptionReview", prescription);

        Map<String, Object> balance = new LinkedHashMap<>();
        balance.put("asOf", str(Instant.now()));
        balance.put("totalOutstandingPerInvoicing", money(inv.totalOutstanding()));
        balance.put("caseSnapshotOutstanding", money(c.getTotalOutstanding()));
        balance.put("invoices", inv.lines());
        root.put("balance", balance);

        List<Map<String, Object>> plans = new ArrayList<>();
        for (PaymentPlan p : planRepository.findByCaseId(tenantId, c.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(p.getId())); m.put("status", str(p.getStatus()));
            m.put("totalAgreedAmount", money(p.getTotalAgreedAmount())); m.put("installmentAmount", money(p.getInstallmentAmount()));
            m.put("frequency", str(p.getFrequency())); m.put("startDate", str(p.getStartDate()));
            m.put("nextDueDate", str(p.getNextDueDate())); m.put("installmentsPaid", p.getInstallmentsPaid());
            m.put("numberOfInstallments", p.getNumberOfInstallments()); m.put("notes", p.getNotes());
            plans.add(m);
        }
        root.put("paymentPlans", plans);

        List<Map<String, Object>> ptps = new ArrayList<>();
        for (PromiseToPay p : ptpRepository.findByCaseId(tenantId, c.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(p.getId())); m.put("seriesId", str(p.getSeriesId())); m.put("status", str(p.getStatus()));
            m.put("amount", money(p.getAmount())); m.put("dueDate", str(p.getDueDate())); m.put("graceEnd", str(p.graceEnd()));
            m.put("amountReceived", money(p.getAmountReceived())); m.put("resolvedDate", str(p.getResolvedDate()));
            m.put("resolutionNote", p.getResolutionNote());
            ptps.add(m);
        }
        root.put("promisesToPay", ptps);

        List<Map<String, Object>> contacts = new ArrayList<>();
        List<String> disputeContactIds = new ArrayList<>();
        for (CollectionContactLog l : contactLogRepository.findByCaseId(tenantId, c.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(l.getId())); m.put("date", str(l.getContactDate())); m.put("method", str(l.getContactMethod()));
            m.put("outcome", str(l.getOutcome())); m.put("notes", l.getNotes());
            m.put("promisedPaymentDate", str(l.getPromisedPaymentDate()));
            m.put("promisedPaymentAmount", money(l.getPromisedPaymentAmount()));
            m.put("recordedBy", l.getRecordedByUserName());
            contacts.add(m);
            if ("DISPUTED".equals(str(l.getOutcome()))) disputeContactIds.add(l.getId().toString());
        }
        root.put("contactHistory", contacts);
        root.put("disputeContactIds", disputeContactIds);

        List<Map<String, Object>> comms = new ArrayList<>();
        for (Communication x : communicationRepository.findByCaseId(tenantId, c.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(x.getId())); m.put("channel", str(x.getChannel())); m.put("direction", str(x.getDirection()));
            m.put("purpose", str(x.getPurpose())); m.put("status", str(x.getStatus())); m.put("recipient", x.getRecipient());
            m.put("subject", x.getSubject()); m.put("templateCode", x.getTemplateCode());
            m.put("templateVersion", x.getTemplateVersion()); m.put("sentAt", str(x.getSentAt()));
            m.put("deliveredAt", str(x.getDeliveredAt())); m.put("receivedAt", str(x.getReceivedAt()));
            m.put("dispatchMethod", str(x.getDispatchMethod())); m.put("trackingReference", x.getTrackingReference());
            m.put("evidenceId", str(x.getEvidenceId()));
            comms.add(m);
        }
        root.put("communications", comms);
        root.put("communicationBodiesIncluded", false);

        List<Map<String, Object>> notices = new ArrayList<>();
        for (LegalNotice n : noticeRepository.findByCaseId(tenantId, c.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", str(n.getId())); m.put("type", str(n.getNoticeType())); m.put("noticeVersion", n.getNoticeVersion());
            m.put("addressUsed", n.getAddressUsed()); m.put("addressSource", n.getAddressSource());
            m.put("dispatchMethod", str(n.getDispatchMethod())); m.put("dispatchedDate", str(n.getDispatchedDate()));
            m.put("trackingReference", n.getTrackingReference()); m.put("postOfficeBranch", n.getPostOfficeBranch());
            m.put("collectionNoticeReference", n.getCollectionNoticeReference());
            m.put("deliveryStatus", str(n.getDeliveryStatus())); m.put("deliveredDate", str(n.getDeliveredDate()));
            m.put("deliveryDetail", n.getDeliveryDetail()); m.put("defaultDate", str(n.getDefaultDate()));
            m.put("communicationId", str(n.getCommunicationId())); m.put("evidenceId", str(n.getEvidenceId()));
            m.put("reviewStatus", str(n.getReviewStatus())); m.put("reviewNote", n.getReviewNote());
            m.put("reviewedBy", n.getReviewedByName()); m.put("reviewedAt", str(n.getReviewedAt()));
            notices.add(m);
        }
        root.put("notices", notices);
        root.put("handoverNotes", h.getNotes());

        try {
            String json = mapper.writeValueAsString(root);
            return new Built(json, sha256(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the handover package", e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String money(BigDecimal b) { return b == null ? null : b.toPlainString(); }
}
