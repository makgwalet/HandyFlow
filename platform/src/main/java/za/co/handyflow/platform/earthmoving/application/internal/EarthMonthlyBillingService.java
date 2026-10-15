package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * The monthly billing run. For a finished month it works out, for every deployment on automatic billing, the charge for the days
 * it was on hire; shows what is ready and what needs a person (no rate, no readings, a disputed reading, a part-charged month);
 * and, when run, raises the charges and, if asked, puts each customer's on one DRAFT invoice. Nothing is issued or collected.
 * Running a month twice is safe: days already charged are skipped and charges already invoiced are not invoiced again.
 * Deliberately not transactional as a whole: every charge and every invoice is its own transaction, so one failure never undoes the rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthMonthlyBillingService {

    private static final int MAX_CHARGES_PER_INVOICE = 100;

    private final EarthDeploymentRepository deployments;
    private final EarthAssetRepository assets;
    private final EarthHireChargeRepository charges;
    private final EarthBillingRunRepository runs;
    private final EarthSettingsRepository settings;
    private final EarthHireService hire;
    private final CrmFacade crm;
    private final EarthmovingAccess access;
    private final AuditTrailFacade trail;

    // ── looking ──────────────────────────────────────────────────────────────

    public BillingRunResponse preview(TenantId tenantId, String month) {
        access.require(EarthmovingRules.ADMIN, "preview monthly billing");
        return preview(tenantId, BillingRules.month(month), EarthHireService.today());
    }

    /** The preview with no permission check: the caller has checked it. */
    public BillingRunResponse preview(TenantId tenantId, YearMonth month, LocalDate today) {
        return response(month, false, lines(tenantId, month, today), 0, List.of());
    }

    public BillingHistoryResponse history(TenantId tenantId) {
        access.require(EarthmovingRules.READ, "view billing runs");
        boolean on = settings.findById(tenantId.getValue()).map(EarthSettings::isAutoBillingEnabled).orElse(false);
        return new BillingHistoryResponse(on, runs.findRecent(tenantId, PageRequest.of(0, 24)).stream()
                .map(r -> new BillingHistoryResponse.Run(r.getId(), YearMonth.from(r.getPeriodMonth()).toString(), r.getTriggerSource(),
                        r.isDraftsRequested(), r.getChargesCreated(), r.getAlreadyBilled(), r.getExceptions(), r.getInvoicesCreated(),
                        r.getCreatedBy(), r.getCreatedAt())).toList());
    }

    // ── running ──────────────────────────────────────────────────────────────

    public BillingRunResponse run(TenantId tenantId, BillingRunRequest req, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "run monthly billing");
        return run(tenantId, BillingRules.month(req.month()), req.createDrafts(), actor, EarthHireService.today());
    }

    /** The run with no permission check: the caller has checked it. */
    public BillingRunResponse run(TenantId tenantId, YearMonth month, boolean drafts, UUID actor, LocalDate today) {
        return execute(tenantId, month, drafts, actor, EarthBillingRun.MANUAL, null, today);
    }

    /** The scheduled run for a company and month. Returns null when that month already ran on schedule (or another server is running it). */
    public BillingRunResponse runScheduled(TenantId tenantId, YearMonth month, LocalDate today) {
        if (runs.scheduledRunExists(tenantId, month.atDay(1))) return null;
        EarthBillingRun marker = EarthBillingRun.of(tenantId, month, EarthBillingRun.SCHEDULED, true, 0, 0, 0, 0, null);
        try { runs.saveAndFlush(marker); }
        catch (DataIntegrityViolationException e) { return null; }
        return execute(tenantId, month, true, null, EarthBillingRun.SCHEDULED, marker, today);
    }

    private BillingRunResponse execute(TenantId tenantId, YearMonth month, boolean drafts, UUID actor, String trigger, EarthBillingRun marker, LocalDate today) {
        List<BillingLine> found = lines(tenantId, month, today);
        List<BillingLine> done = new ArrayList<>();
        int created = 0;
        for (BillingLine l : found) {
            if (!BillingRules.READY.equals(l.state())) { done.add(l); continue; }
            try {
                hire.raise(tenantId, new HireChargeRequest(l.deploymentId(), l.basis(), l.periodFrom(), l.periodTo(), null, null, null, null), actor, today);
                created++;
                done.add(l);
            } catch (RuntimeException e) {
                done.add(withState(l, BillingRules.EXCEPTION, e.getMessage()));
            }
        }
        List<DraftInvoiceResponse> invoices = new ArrayList<>();
        if (drafts) draftInvoices(tenantId, month, actor, invoices, done);

        BillingRunResponse out = response(month, true, done, created, invoices);
        if (marker != null) { marker.finish(created, out.alreadyBilled(), out.exceptions(), invoices.size()); runs.save(marker); }
        else runs.save(EarthBillingRun.of(tenantId, month, trigger, drafts, created, out.alreadyBilled(), out.exceptions(), invoices.size(), actor));
        trail.record(tenantId, "EARTHMOVING_BILLING_RUN", "EARTHMOVING_BILLING", tenantId.getValue(), null,
                Map.of("month", month.toString(), "trigger", trigger, "chargesCreated", String.valueOf(created),
                        "alreadyBilled", String.valueOf(out.alreadyBilled()), "exceptions", String.valueOf(out.exceptions()),
                        "draftInvoices", String.valueOf(invoices.size())));
        return out;
    }

    /** Every waiting (pending, not on hold) charge of the month's automatic deployments, one draft invoice per customer. */
    private void draftInvoices(TenantId tenantId, YearMonth month, UUID actor, List<DraftInvoiceResponse> invoices, List<BillingLine> lines) {
        Set<UUID> auto = new HashSet<>();
        for (BillingLine l : lines) if (l.deploymentId() != null) auto.add(l.deploymentId());
        Map<UUID, List<UUID>> byCustomer = new LinkedHashMap<>();
        for (EarthHireCharge c : charges.findPendingStartingBetween(tenantId, month.atDay(1), month.atEndOfMonth()))
            if (auto.contains(c.getDeploymentId()) && !c.needsAttention()) byCustomer.computeIfAbsent(c.getCustomerId(), k -> new ArrayList<>()).add(c.getId());
        for (Map.Entry<UUID, List<UUID>> e : byCustomer.entrySet()) {
            List<UUID> ids = e.getValue();
            for (int i = 0; i < ids.size(); i += MAX_CHARGES_PER_INVOICE) {
                List<UUID> chunk = ids.subList(i, Math.min(ids.size(), i + MAX_CHARGES_PER_INVOICE));
                try {
                    invoices.add(hire.draftFrom(tenantId, new DraftFromChargesRequest(new ArrayList<>(chunk), BillingRules.title(month), null), actor));
                } catch (RuntimeException ex) {
                    log.warn("[Earthmoving] Draft invoice for {} failed: {}", month, ex.getMessage());
                    lines.add(new BillingLine(null, null, null, customerName(tenantId, e.getKey()), null, null, month.atDay(1), month.atEndOfMonth(),
                            BillingRules.EXCEPTION, "The draft invoice could not be made: " + ex.getMessage(), null, null, null, null, null));
                }
            }
        }
    }

    // ── what is on the run ───────────────────────────────────────────────────

    private List<BillingLine> lines(TenantId tenantId, YearMonth month, LocalDate today) {
        BillingRules.requireEnded(month, today);
        Instant from = month.atDay(1).atStartOfDay(EarthHireService.ZONE).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(EarthHireService.ZONE).toInstant();
        List<BillingLine> out = new ArrayList<>();
        for (EarthDeployment d : deployments.findAutoBilled(tenantId, from, to)) {
            LocalDate deployedOn = d.getDeployedAt().atZone(EarthHireService.ZONE).toLocalDate();
            LocalDate returnedOn = d.getReturnedAt() == null ? null : d.getReturnedAt().atZone(EarthHireService.ZONE).toLocalDate();
            BillingRules.Span span = BillingRules.span(month, deployedOn, returnedOn);
            if (span == null) continue;
            String machine = assets.findActiveById(tenantId, d.getAssetId()).map(EarthMonthlyBillingService::label).orElse("A removed machine");
            String customer = customerName(tenantId, d.getCustomerId());
            BillingLine base = new BillingLine(d.getId(), d.getAssetId(), machine, customer, d.getSiteName(), d.getAutoBillingBasis(),
                    span.from(), span.to(), BillingRules.EXCEPTION, null, null, null, null, null, null);
            List<BillingRules.Span> charged = charges.findLiveOverlapping(tenantId, d.getId(), span.from(), span.to()).stream()
                    .map(c -> new BillingRules.Span(c.getPeriodFrom(), c.getPeriodTo())).toList();
            switch (BillingRules.cover(span, charged)) {
                case FULL -> out.add(withState(base, BillingRules.BILLED, "Already charged."));
                case PARTIAL -> out.add(withState(base, BillingRules.EXCEPTION,
                        "Part of this month is already charged for this deployment. Void that charge, or charge the rest by hand."));
                default -> {
                    try {
                        HireChargeResponse q = hire.quote(tenantId, new HireChargeRequest(d.getId(), d.getAutoBillingBasis(), span.from(), span.to(), null, null, null, null), today);
                        out.add(new BillingLine(base.deploymentId(), base.assetId(), machine, customer, base.siteName(), base.basis(), span.from(), span.to(),
                                BillingRules.READY, null, q.quantity(), q.unitRate(), q.amount(), q.total(), q.rateSource()));
                    } catch (RuntimeException e) {
                        out.add(withState(base, BillingRules.EXCEPTION, e.getMessage()));
                    }
                }
            }
        }
        return out;
    }

    private static BillingLine withState(BillingLine l, String state, String message) {
        return new BillingLine(l.deploymentId(), l.assetId(), l.machine(), l.customerName(), l.siteName(), l.basis(), l.periodFrom(), l.periodTo(),
                state, message, l.quantity(), l.unitRate(), l.amount(), l.total(), l.rateSource());
    }

    private static BillingRunResponse response(YearMonth month, boolean applied, List<BillingLine> lines, int created, List<DraftInvoiceResponse> invoices) {
        int ready = 0, billed = 0, exc = 0;
        for (BillingLine l : lines) {
            if (BillingRules.READY.equals(l.state())) ready++;
            else if (BillingRules.BILLED.equals(l.state())) billed++;
            else exc++;
        }
        return new BillingRunResponse(month.toString(), applied, lines, ready, billed, exc, created, invoices);
    }

    private String customerName(TenantId tenantId, UUID customerId) {
        return customerId == null ? null : crm.findCustomerById(tenantId, customerId).map(CustomerSummary::name).orElse(null);
    }

    private static String label(EarthAsset a) {
        return a.getFleetNumber() != null && !a.getFleetNumber().isBlank() ? a.getFleetNumber() + " " + a.getName() : a.getName();
    }

    // ── set-up ───────────────────────────────────────────────────────────────

    public AutoBillingResponse deploymentBasis(TenantId tenantId, UUID deploymentId) {
        access.require(EarthmovingRules.READ, "view automatic billing");
        return new AutoBillingResponse(deploymentId, deployment(tenantId, deploymentId).getAutoBillingBasis());
    }

    public AutoBillingResponse setDeploymentBasis(TenantId tenantId, UUID deploymentId, String basis, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "change automatic billing");
        EarthDeployment d = deployment(tenantId, deploymentId);
        String before = d.getAutoBillingBasis();
        d.setAutoBilling(basis);
        deployments.save(d);
        trail.record(tenantId, "EARTHMOVING_AUTO_BILLING_SET", "EARTHMOVING_DEPLOYMENT", deploymentId,
                Map.of("basis", before == null ? "off" : before), Map.of("basis", d.getAutoBillingBasis() == null ? "off" : d.getAutoBillingBasis()));
        return new AutoBillingResponse(deploymentId, d.getAutoBillingBasis());
    }

    public BillingHistoryResponse switchScheduled(TenantId tenantId, boolean enabled, UUID actor) {
        access.require(EarthmovingRules.ADMIN, "switch the scheduled billing run");
        EarthSettings s = settings.findById(tenantId.getValue()).orElseGet(() -> EarthSettings.forTenant(tenantId));
        boolean before = s.isAutoBillingEnabled();
        s.setAutoBillingEnabled(enabled, actor);
        settings.save(s);
        trail.record(tenantId, "EARTHMOVING_AUTO_BILLING_SWITCHED", "EARTHMOVING_SETTINGS", tenantId.getValue(),
                Map.of("enabled", String.valueOf(before)), Map.of("enabled", String.valueOf(enabled)));
        return history(tenantId);
    }

    private EarthDeployment deployment(TenantId tenantId, UUID id) {
        return deployments.findByTenantAndId(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Deployment", id.toString()));
    }
}
