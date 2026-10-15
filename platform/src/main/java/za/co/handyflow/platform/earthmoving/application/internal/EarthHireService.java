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
import za.co.handyflow.platform.earthmoving.domain.model.EarthContractRate;
import za.co.handyflow.platform.earthmoving.domain.model.EarthMeterDispute;
import za.co.handyflow.platform.earthmoving.domain.model.MeterRules;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules;
import za.co.handyflow.platform.earthmoving.domain.model.HireRules;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthContractRateRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMeterDisputeRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDeploymentRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthHireChargeRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthMeterReadingRepository;
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

    /** Today in the company's time zone. */
    static LocalDate today() { return LocalDate.now(ZONE); }
    private static final int MAX_CHARGES_PER_INVOICE = 100;

    private final EarthDeploymentRepository deployments;
    private final EarthAssetRepository assets;
    private final EarthHireChargeRepository charges;
    private final OperatorLogRepository logs;
    private final EarthMeterReadingRepository meters;
    private final EarthContractRateRepository contractRates;
    private final EarthMeterDisputeRepository disputes;
    private final CrmFacade crm;
    private final InvoicingFacade invoicing;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    private record Quote(EarthDeployment deployment, EarthAsset asset, String machine, String basis, BigDecimal quantity,
                         BigDecimal rate, BigDecimal vat, String description, String rateNote, String rateSource,
                         BigDecimal includedKm, MeterRules.Standing start, MeterRules.Standing end) {}

    /** What a re-count of a machine's kilometre charges did: charges worked out again, charges held back, invoiced charges now out of step. */
    public record Refresh(int recalculated, int held, int invoiced) {}

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
        return quote(tenantId, req, today);
    }

    /** What a charge would be, with no permission check: for the monthly billing run, whose caller has checked it (or is the schedule). */
    @Transactional(readOnly = true)
    public HireChargeResponse quote(TenantId tenantId, HireChargeRequest req, LocalDate today) {
        Quote q = checked(tenantId, req, today);
        EarthHireCharge c = build(tenantId, q, req, null);
        return new HireChargeResponse(null, c.getDeploymentId(), c.getAssetId(), q.machine(), c.getCustomerId(), customerName(tenantId, c.getCustomerId()),
                q.deployment().getSiteName(), c.getPeriodFrom(), c.getPeriodTo(), c.getBasis(), c.getQuantity(), c.getUnitRate(), c.getVatRate(),
                c.getAmount(), c.getVatAmount(), c.getTotal(), c.getDescription(), "PREVIEW", null, null, null, null,
                q.rateSource(), q.includedKm(), null, null);
    }

    @Transactional
    public HireChargeResponse create(TenantId tenantId, HireChargeRequest req, UUID actor) {
        return create(tenantId, req, actor, LocalDate.now(ZONE));
    }

    @Transactional
    public HireChargeResponse create(TenantId tenantId, HireChargeRequest req, UUID actor, LocalDate today) {
        access.require(EarthmovingRules.ADMIN, "raise a hire charge");
        return raise(tenantId, req, actor, today);
    }

    /** Raises a charge with no permission check: for the monthly billing run, whose caller has checked it (or is the schedule). */
    @Transactional
    public HireChargeResponse raise(TenantId tenantId, HireChargeRequest req, UUID actor, LocalDate today) {
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
        return draftFrom(tenantId, req, actor);
    }

    /** The draft invoice with no permission check: for the monthly billing run, whose caller has checked it (or is the schedule). */
    @Transactional
    public DraftInvoiceResponse draftFrom(TenantId tenantId, DraftFromChargesRequest req, UUID actor) {
        List<UUID> ids = req.chargeIds() == null ? List.of() : req.chargeIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) throw new IllegalArgumentException("Choose at least one charge.");
        if (ids.size() > MAX_CHARGES_PER_INVOICE) throw new IllegalArgumentException("Put at most " + MAX_CHARGES_PER_INVOICE + " charges on one invoice.");
        List<EarthHireCharge> picked = new ArrayList<>(charges.findByTenantAndIds(tenantId, ids));
        if (picked.size() != ids.size()) throw new IllegalArgumentException("One or more of those charges was not found.");
        for (EarthHireCharge c : picked) {
            if (!c.isPending()) throw new IllegalStateException("A charge for " + c.getPeriodFrom() + " to " + c.getPeriodTo() + " is already " + c.getStatus().toLowerCase() + ".");
            if (c.needsAttention()) throw new IllegalStateException("The charge for " + c.getPeriodFrom() + " to " + c.getPeriodTo() + " is on hold: " + c.getAttention());
        }
        UUID customer = picked.get(0).getCustomerId();
        if (picked.stream().anyMatch(c -> !c.getCustomerId().equals(customer)))
            throw new IllegalArgumentException("One invoice is for one customer. Choose charges for the same customer.");
        picked.sort(Comparator.comparing(EarthHireCharge::getPeriodFrom).thenComparing(EarthHireCharge::getCreatedAt));

        List<InvoicingFacade.DraftInvoiceLine> lines = picked.stream().map(c -> new InvoicingFacade.DraftInvoiceLine(
                c.getDescription(), HireRules.unit(c.getBasis()), c.getQuantity(), c.getUnitRate(), c.getVatRate())).toList();
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

    // ── readings changed ─────────────────────────────────────────────────────

    /**
     * Works a machine's kilometre charges out again from its readings as they stand now. Called after a reading was corrected,
     * disputed or confirmed; the caller has already checked permission. A pending charge whose readings changed is recalculated
     * (same rate and VAT, new quantity); one whose readings are disputed, or no longer add up, is held back from invoicing with
     * the reason; a held one that is fine again is released. An invoiced charge is never touched, only counted.
     */
    @Transactional
    public Refresh refreshKilometreCharges(TenantId tenantId, UUID assetId, UUID actor, String trigger) {
        List<EarthHireCharge> live = charges.findLiveKilometreByAsset(tenantId, assetId);
        if (live.isEmpty()) return new Refresh(0, 0, 0);
        EarthAsset asset = assets.findActiveById(tenantId, assetId).orElse(null);
        if (asset == null) return new Refresh(0, 0, 0);
        List<MeterRules.Point> points = points(tenantId, assetId);
        int recalculated = 0, held = 0, invoiced = 0;
        for (EarthHireCharge c : live) {
            if (c.getStartReadingId() == null) continue; // raised before readings were kept: its allowance is unknown, so it is left as it is
            MeterRules.Standing start = MeterRules.standingBefore(points, MeterRules.KILOMETRES, c.getPeriodFrom().atStartOfDay(ZONE).toInstant());
            MeterRules.Standing end = MeterRules.standingBefore(points, MeterRules.KILOMETRES, c.getPeriodTo().plusDays(1).atStartOfDay(ZONE).toInstant());
            String problem = null;
            BigDecimal quantity = null;
            List<UUID> ids = java.util.stream.Stream.of(start, end).filter(Objects::nonNull).map(MeterRules.Standing::id).toList();
            List<EarthMeterDispute> open = ids.isEmpty() ? List.of() : disputes.findOpenForReadings(tenantId, ids);
            if (!open.isEmpty()) problem = "An odometer reading is disputed: " + open.get(0).getReason();
            else try {
                quantity = HireRules.kilometres(start == null ? null : start.reading(), end == null ? null : end.reading(), c.getIncludedKm());
            } catch (IllegalArgumentException e) { problem = e.getMessage(); }

            if (!c.isPending()) {
                if (problem != null || quantity.compareTo(c.getQuantity()) != 0) invoiced++;
                continue;
            }
            Map<String, Object> before = snapshot(c);
            if (problem != null) {
                if (problem.equals(c.getAttention())) continue;
                c.hold(problem);
                charges.save(c);
                held++;
                trail.record(tenantId, "EARTHMOVING_HIRE_CHARGE_HELD", "EARTHMOVING_HIRE_CHARGE", c.getId(), before, snapshotWith(c, "trigger", trigger));
                continue;
            }
            boolean same = quantity.compareTo(c.getQuantity()) == 0 && Objects.equals(start.id(), c.getStartReadingId()) && Objects.equals(end.id(), c.getEndReadingId());
            if (same && !c.needsAttention()) continue;
            if (same) {
                c.release();
                charges.save(c);
                trail.record(tenantId, "EARTHMOVING_HIRE_CHARGE_RELEASED", "EARTHMOVING_HIRE_CHARGE", c.getId(), before, snapshotWith(c, "trigger", trigger));
                continue;
            }
            String site = deployments.findByTenantAndId(tenantId, c.getDeploymentId()).map(EarthDeployment::getSiteName).orElse(null);
            c.recalculate(quantity, HireRules.describeKm(label(asset), site, c.getPeriodFrom(), c.getPeriodTo(), start.reading(), end.reading(),
                    c.getIncludedKm(), quantity, c.getUnitRate()), start.id(), start.reading(), end.id(), end.reading());
            charges.save(c);
            recalculated++;
            trail.record(tenantId, "EARTHMOVING_HIRE_CHARGE_RECALCULATED", "EARTHMOVING_HIRE_CHARGE", c.getId(), before, snapshotWith(c, "trigger", trigger));
        }
        return new Refresh(recalculated, held, invoiced);
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
        EarthContractRate held = d.getContractId() == null ? null : contractRates.findByContractAndBasis(tenantId, d.getContractId(), basis).orElse(null);
        HireRules.Rate chosen = HireRules.choose(basis, req.unitRate(), req.rateReason(), held == null ? null : held.getUnitRate(),
                asset.getDailyRate(), asset.getHourlyRate());
        BigDecimal rate = chosen.value();
        String rateNote = req.unitRate() == null ? null : "Rate agreed for this charge: " + req.rateReason().trim();
        BigDecimal quantity;
        String describedAs = null;
        BigDecimal included = null;
        MeterRules.Standing start = null, end = null;
        if (HireRules.KILOMETRE.equals(basis)) {
            if (!MeterRules.KILOMETRES.equals(asset.getMeterType()))
                throw new IllegalArgumentException("This machine's meter counts hours, not kilometres, so it cannot be billed by kilometre.");
            included = req.includedKm() != null ? req.includedKm() : held == null ? null : held.getIncludedKm();
            List<MeterRules.Point> points = points(tenantId, asset.getId());
            start = MeterRules.standingBefore(points, MeterRules.KILOMETRES, req.periodFrom().atStartOfDay(ZONE).toInstant());
            end = MeterRules.standingBefore(points, MeterRules.KILOMETRES, req.periodTo().plusDays(1).atStartOfDay(ZONE).toInstant());
            refuseDisputed(tenantId, start, end);
            quantity = HireRules.kilometres(start == null ? null : start.reading(), end == null ? null : end.reading(), included);
            describedAs = HireRules.describeKm(label(asset), d.getSiteName(), req.periodFrom(), req.periodTo(), start.reading(), end.reading(), included, quantity, rate);
        } else if (HireRules.DAILY.equals(basis)) quantity = BigDecimal.valueOf(HireRules.days(req.periodFrom(), req.periodTo())).setScale(2);
        else if (HireRules.WEEKLY.equals(basis)) quantity = BigDecimal.valueOf(HireRules.weeks(req.periodFrom(), req.periodTo())).setScale(2);
        else if (HireRules.MONTHLY.equals(basis)) quantity = BigDecimal.valueOf(HireRules.months(req.periodFrom(), req.periodTo())).setScale(2);
        else quantity = HireRules.hours(logs.sumHoursBetween(asset.getId(),
                    req.periodFrom().atStartOfDay(ZONE).toInstant(), req.periodTo().plusDays(1).atStartOfDay(ZONE).toInstant()));
        List<EarthHireCharge> overlapping = charges.findLiveOverlapping(tenantId, d.getId(), req.periodFrom(), req.periodTo());
        if (!overlapping.isEmpty()) {
            EarthHireCharge o = overlapping.get(0);
            throw new IllegalStateException("Part of that period is already charged for this deployment (" + o.getPeriodFrom() + " to " + o.getPeriodTo()
                    + ", " + o.getStatus().toLowerCase() + "). Void that charge first if it is wrong.");
        }
        String machine = label(asset);
        return new Quote(d, asset, machine, basis, quantity, rate, HireRules.vatRate(req.vatRate()),
                describedAs != null ? describedAs : HireRules.describe(machine, d.getSiteName(), basis, req.periodFrom(), req.periodTo(), quantity, rate), rateNote,
                chosen.source(), included, start, end);
    }

    private List<MeterRules.Point> points(TenantId tenantId, UUID assetId) {
        return meters.findByAsset(tenantId, assetId, org.springframework.data.domain.PageRequest.of(0, 5000)).stream()
                .map(r -> r.point()).toList();
    }

    /** A reading someone has disputed cannot be billed on until the dispute is closed. */
    private void refuseDisputed(TenantId tenantId, MeterRules.Standing... readings) {
        List<UUID> ids = java.util.Arrays.stream(readings).filter(Objects::nonNull).map(MeterRules.Standing::id).toList();
        if (ids.isEmpty()) return;
        List<EarthMeterDispute> open = disputes.findOpenForReadings(tenantId, ids);
        if (!open.isEmpty())
            throw new IllegalStateException("An odometer reading this charge depends on is disputed (" + open.get(0).getReason()
                    + "). Confirm or correct the reading under the machine's meter history first.");
    }

    private EarthHireCharge build(TenantId tenantId, Quote q, HireChargeRequest req, UUID actor) {
        EarthHireCharge c = EarthHireCharge.create(tenantId, q.deployment().getId(), q.asset().getId(), q.deployment().getCustomerId(),
                req.periodFrom(), req.periodTo(), q.basis(), q.quantity(), q.rate(), q.vat(), q.description(), actor);
        c.noteRateOverride(q.rateNote());
        c.recordRate(q.rateSource());
        if (HireRules.KILOMETRE.equals(q.basis()))
            c.recordKilometres(q.includedKm(), q.start().id(), q.start().reading(), q.end().id(), q.end().reading());
        return c;
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
        if (c.getRateSource() != null) m.put("rateSource", c.getRateSource());
        if (c.isKilometre()) m.put("readings", c.getStartReading() + " to " + c.getEndReading());
        if (c.getAttention() != null) m.put("attention", c.getAttention());
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
                c.getTotal(), c.getDescription(), c.getStatus(), c.getInvoiceId(), c.getInvoiceNumber(), c.getNote(), c.getCreatedAt(),
                c.getRateSource(), c.getIncludedKm(), c.getAttention(), c.getRecalculatedAt());
    }
}
