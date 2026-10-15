package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDeployment;
import za.co.handyflow.platform.earthmoving.domain.model.EarthHireCharge;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.HireRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthHireChargeRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.OperatorLogRepository;
import za.co.handyflow.platform.earthmoving.dto.DraftFromChargesRequest;
import za.co.handyflow.platform.earthmoving.dto.DraftInvoiceResponse;
import za.co.handyflow.platform.earthmoving.dto.HireChargeRequest;
import za.co.handyflow.platform.earthmoving.dto.HireChargeResponse;
import za.co.handyflow.platform.invoicing.application.InvoicingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Hire charges: what a customer owes for a machine on hire, one billing period at a time, and the draft invoice they
 * go onto. Nothing here issues an invoice. A period of a deployment can be charged once; a voided charge frees it.
 * Raising, voiding and invoicing need EARTHMOVING_ADMIN; reading needs EARTHMOVING_READ.
 */
@Service
@RequiredArgsConstructor
public class EarthHireService {

    static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final int MAX_CHARGES_PER_INVOICE = 100;

    private final EarthDeploymentRepository deployments;
    private final EarthAssetRepository assets;
    private final EarthHireChargeRepository charges;
    private final OperatorLogRepository logs;
    private final CrmFacade crm;
    private final InvoicingFacade invoicing;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    private record Quote(EarthDeployment deployment, EarthAsset asset, String machine, String basis, BigDecimal quantity,
                         BigDecimal rate, BigDecimal vat, String description) {}

    // ── reading ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HireChargeResponse> list(TenantId tenantId, String status) {
        access.require(EarthmovingRules.READ, "view hire charges");
        String s = status == null || status.isBlank() ? null : EarthmovingRules.oneOf(status, Set.of(HireRules.PENDING, HireRules.INVOICED, HireRules.VOIDED), "Status");
        Map<UUID, String> customerNames = new HashMap<>();
        Map<UUID, String> machines = new HashMap<>();
        Map<UUID, String> sites = new HashMap<>();
        List<HireChargeResponse> out = new ArrayList<>();
        for (EarthHireCharge c : charges.findForTenant(tenantId, s)) {
            String customer = customerNames.computeIfAbsent(c.getCustomerId(), id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse(null));
            String machine = machines.computeIfAbsent(c.getAssetId(), id -> assets.findActiveById(tenantId, id).map(EarthHireService::label).orElse("A removed machine"));
            String site = sites.computeIfAbsent(c.getDeploymentId(), id -> deployments.findByTenantAndId(tenantId, id).map(EarthDeployment::getSiteName).orElse(null));
            out.add(toResponse(c, machine, customer, site));
        }
        return out;
    }

    // ── raising ──────────────────────────────────────────────────────────────

    /** What the charge would be, with the same checks as raising it. Saves nothing. */
    @Transactional(readOnly = true)
    public HireChargeResponse preview(TenantId tenantId, HireChargeRequest req) {
        return preview(tenantId, req, LocalDate.now(ZONE));
    }

    public HireChargeResponse preview(TenantId tenantId, HireChargeRequest req, LocalDate today) {
        access.require(EarthmovingRules.ADMIN, "raise a hire charge");
        Quote q = checked(tenantId, req, today);
        EarthHireCharge c = build(tenantId, q, req, null);
        return new HireChargeResponse(null, c.getDeploymentId(), c.getAssetId(), q.machine(), c.getCustomerId(), customerName(tenantId, c.getCustomerId()),
                q.deployment().getSiteName(), c.getPeriodFrom(), c.getPeriodTo(), c.getBasis(), c.getQuantity(), c.getUnitRate(), c.getVatRate(),
                c.getAmount(), c.getVatAmount(), c.getTotal(), c.getDescription(), "PREVIEW", null, null, null, null);
    }

    @Transactional
    public HireChargeResponse create(TenantId tenantId, HireChargeRequest req, UUID actor) {
        return create(tenantId, req, actor, LocalDate.now(ZONE));
    }

    @Transactional
    public HireChargeResponse create(TenantId tenantId, HireChargeRequest req, UUID actor, LocalDate today) {
        access.require(EarthmovingRules.ADMIN, "raise a hire charge");
        Quote q = checked(tenantId, req, today);
        EarthHireCharge c = build(tenantId, q, req, actor);
        try {
            charges.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            // The database also refuses overlapping live periods, which catches two people raising the same period at once.
            throw new IllegalStateException("Part of that period is already charged for this deployment.");
        }
        trail.record(tenantId, "EARTHMOVING_HIRE_CHARGE_CREATED", "EARTHMOVING_HIRE_CHARGE", c.getId(), null, snapshot(c));
        return toResponse(c, q.machine(), customerName(tenantId, c.getCustomerId()), q.deployment().getSiteName());
    }

    @Transactional
    public HireChargeResponse voidCharge(TenantId tenantId, UUID id, String reason, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "void a hire charge");
        EarthHireCharge c = find(tenantId, id);
        Map<String, Object> before = snapshot(c);
        c.voidCharge(reason);
        charges.save(c);
        trail.record(tenantId, "EARTHMOVING_HIRE_CHARGE_VOIDED", "EARTHMOVING_HIRE_CHARGE", id, before, snapshotWith(c, "reason", c.getNote()));
        return respond(tenantId, c);
    }

    /** Back to PENDING, allowed only when the draft invoice it went onto is gone or cancelled, so it cannot be billed twice. */
    @Transactional
    public HireChargeResponse reopen(TenantId tenantId, UUID id, String reason, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "reopen a hire charge");
        EarthHireCharge c = find(tenantId, id);
        if (!HireRules.INVOICED.equals(c.getStatus())) throw new IllegalStateException("Only an invoiced charge can be reopened.");
        Optional<String> status = invoicing.findInvoiceStatus(tenantId, c.getInvoiceId());
        if (status.isPresent() && !"CANCELLED".equals(status.get()))
            throw new IllegalStateException("Invoice " + c.getInvoiceNumber() + " still exists (" + status.get().toLowerCase()
                    + "). Delete or cancel it in Invoicing first, then reopen this charge.");
        Map<String, Object> before = snapshot(c);
        c.reopen(reason);
        charges.save(c);
        trail.record(tenantId, "EARTHMOVING_HIRE_CHARGE_REOPENED", "EARTHMOVING_HIRE_CHARGE", id, before, snapshotWith(c, "reason", c.getNote()));
        return respond(tenantId, c);
    }

    // ── invoicing ────────────────────────────────────────────────────────────

    /** One DRAFT invoice for pending charges of a single customer. The invoice is not issued. */
    @Transactional
    public DraftInvoiceResponse draftInvoice(TenantId tenantId, DraftFromChargesRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "raise a draft invoice");
        List<UUID> ids = req.chargeIds() == null ? List.of() : req.chargeIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("Choose at least one charge.");
        if (ids.size() > MAX_CHARGES_PER_INVOICE) throw new IllegalArgumentException("Put at most " + MAX_CHARGES_PER_INVOICE + " charges on one invoice.");
        List<EarthHireCharge> picked = new ArrayList<>(charges.findByTenantAndIds(tenantId, ids));
        if (picked.size() != ids.size()) throw new IllegalArgumentException("One or more of those charges was not found.");
        for (EarthHireCharge c : picked)
            if (!c.isPending()) throw new IllegalStateException("A charge for " + c.getPeriodFrom() + " to " + c.getPeriodTo() + " is already " + c.getStatus().toLowerCase() + ".");
        UUID customer = picked.get(0).getCustomerId();
        if (picked.stream().anyMatch(c -> !c.getCustomerId().equals(customer)))
            throw new IllegalArgumentException("One invoice is for one customer. Choose charges for the same customer.");
        picked.sort(Comparator.comparing(EarthHireCharge::getPeriodFrom).thenComparing(EarthHireCharge::getCreatedAt));

        List<InvoicingFacade.DraftInvoiceLine> lines = picked.stream().map(c -> new InvoicingFacade.DraftInvoiceLine(
                c.getDescription(), HireRules.DAILY.equals(c.getBasis()) ? "day" : "hour", c.getQuantity(), c.getUnitRate(), c.getVatRate())).toList();
        String title = req.title() == null || req.title().isBlank() ? "Plant hire" : req.title().trim();
        InvoicingFacade.DraftInvoiceResult invoice = invoicing.createDraftInvoice(tenantId,
                new InvoicingFacade.DraftInvoice(customer, title, "Raised from hire charges in HandyFlow Earthmoving.", req.dueDate(), lines));

        for (EarthHireCharge c : picked) c.markInvoiced(invoice.id(), invoice.invoiceNumber());
        charges.saveAll(picked);
        trail.record(tenantId, "EARTHMOVING_HIRE_INVOICED", "EARTHMOVING_HIRE_CHARGE", picked.get(0).getId(), null,
                Map.of("invoice", invoice.invoiceNumber(), "invoiceId", invoice.id().toString(), "charges", String.valueOf(picked.size()),
                        "total", invoice.total().toPlainString()));
        return new DraftInvoiceResponse(invoice.id(), invoice.invoiceNumber(), invoice.subtotal(), invoice.vatTotal(), invoice.total(), picked.size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Quote checked(TenantId tenantId, HireChargeRequest req, LocalDate today) {
        EarthDeployment d = deployments.findByTenantAndId(tenantId, req.deploymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Deployment", String.valueOf(req.deploymentId())));
        if (d.getCustomerId() == null)
            throw new IllegalArgumentException("This deployment is not linked to a customer yet. Link it first (Sites > Links to review).");
        if (!crm.customerExists(tenantId, d.getCustomerId())) throw new IllegalArgumentException("The customer on this deployment no longer exists.");
        EarthAsset asset = assets.findActiveById(tenantId, d.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset", String.valueOf(d.getAssetId())));
        String basis = HireRules.basis(req.basis());
        LocalDate deployedOn = d.getDeployedAt().atZone(ZONE).toLocalDate();
        LocalDate returnedOn = d.getReturnedAt() == null ? null : d.getReturnedAt().atZone(ZONE).toLocalDate();
        HireRules.checkPeriod(req.periodFrom(), req.periodTo(), deployedOn, returnedOn, today);
        BigDecimal rate = HireRules.rateFor(basis, asset.getDailyRate(), asset.getHourlyRate());
        BigDecimal quantity = HireRules.DAILY.equals(basis)
                ? BigDecimal.valueOf(HireRules.days(req.periodFrom(), req.periodTo())).setScale(2)
                : HireRules.hours(logs.sumHoursBetween(asset.getId(),
                        req.periodFrom().atStartOfDay(ZONE).toInstant(), req.periodTo().plusDays(1).atStartOfDay(ZONE).toInstant()));
        List<EarthHireCharge> overlapping = charges.findLiveOverlapping(tenantId, d.getId(), req.periodFrom(), req.periodTo());
        if (!overlapping.isEmpty()) {
            EarthHireCharge o = overlapping.get(0);
            throw new IllegalStateException("Part of that period is already charged for this deployment (" + o.getPeriodFrom() + " to " + o.getPeriodTo()
                    + ", " + o.getStatus().toLowerCase() + "). Void that charge first if it is wrong.");
        }
        String machine = label(asset);
        return new Quote(d, asset, machine, basis, quantity, rate, HireRules.vatRate(req.vatRate()),
                HireRules.describe(machine, d.getSiteName(), basis, req.periodFrom(), req.periodTo(), quantity, rate));
    }

    private EarthHireCharge build(TenantId tenantId, Quote q, HireChargeRequest req, UUID actor) {
        return EarthHireCharge.create(tenantId, q.deployment().getId(), q.asset().getId(), q.deployment().getCustomerId(),
                req.periodFrom(), req.periodTo(), q.basis(), q.quantity(), q.rate(), q.vat(), q.description(), actor);
    }

    private EarthHireCharge find(TenantId tenantId, UUID id) {
        return charges.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("HireCharge", id.toString()));
    }

    private HireChargeResponse respond(TenantId tenantId, EarthHireCharge c) {
        String machine = assets.findActiveById(tenantId, c.getAssetId()).map(EarthHireService::label).orElse("A removed machine");
        String site = deployments.findByTenantAndId(tenantId, c.getDeploymentId()).map(EarthDeployment::getSiteName).orElse(null);
        return toResponse(c, machine, customerName(tenantId, c.getCustomerId()), site);
    }

    private String customerName(TenantId tenantId, UUID customerId) {
        return customerId == null ? null : crm.findCustomerById(tenantId, customerId).map(CustomerSummary::name).orElse(null);
    }

    private static String label(EarthAsset a) {
        return a.getFleetNumber() != null && !a.getFleetNumber().isBlank() ? a.getFleetNumber() + " " + a.getName() : a.getName();
    }

    private static Map<String, Object> snapshot(EarthHireCharge c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deploymentId", String.valueOf(c.getDeploymentId()));
        m.put("period", c.getPeriodFrom() + " to " + c.getPeriodTo());
        m.put("basis", c.getBasis());
        m.put("quantity", c.getQuantity().toPlainString());
        m.put("rate", c.getUnitRate().toPlainString());
        m.put("amount", c.getAmount().toPlainString());
        m.put("status", c.getStatus());
        return m;
    }

    private static Map<String, Object> snapshotWith(EarthHireCharge c, String key, String value) {
        Map<String, Object> m = snapshot(c);
        m.put(key, value == null ? "" : value);
        return m;
    }

    private static HireChargeResponse toResponse(EarthHireCharge c, String machine, String customerName, String siteName) {
        return new HireChargeResponse(c.getId(), c.getDeploymentId(), c.getAssetId(), machine, c.getCustomerId(), customerName, siteName,
                c.getPeriodFrom(), c.getPeriodTo(), c.getBasis(), c.getQuantity(), c.getUnitRate(), c.getVatRate(), c.getAmount(), c.getVatAmount(),
                c.getTotal(), c.getDescription(), c.getStatus(), c.getInvoiceId(), c.getInvoiceNumber(), c.getNote(), c.getCreatedAt());
    }
}
