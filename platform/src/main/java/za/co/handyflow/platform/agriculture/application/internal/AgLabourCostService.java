package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.application.internal.AgFinanceSettingsService.Effective;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.agriculture.domain.model.AgInputApplication;
import za.co.handyflow.platform.agriculture.domain.repository.AgCostEntryRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgCropCycleRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgFarmRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgHarvestRecordRepository;
import za.co.handyflow.platform.agriculture.domain.repository.AgInputApplicationRepository;
import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.agriculture.domain.rules.AgLabourRules;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.CostEntryResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.CostLabourRequest;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.FinanceSettingsResponse;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourCandidate;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourItem;
import za.co.handyflow.platform.agriculture.dto.LabourDtos.LabourOverview;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Costs the labour recorded on crop work into the cost ledger (ADR-001, W3). Two record types carry labour hours: input applications and harvests.
 * <p>
 * The rate comes from HR (the worker's salary over the hours in their pay period, see {@link AgLabourRules}) or is typed in, which is how casual
 * workers with no HR record are costed. It is loaded with the tenant's on-cost and SNAPSHOTTED into the ledger entry, so a later raise never
 * rewrites a past cost. Costing is an explicit finance action, never a side effect of recording the work: the person who logs a spray does not
 * need to see what the sprayer is paid.
 * <p>
 * Privacy: this service never returns a salary, only the hourly rate derived from it, and it only reads HR when told the caller may
 * ({@code hrRatesAllowed}); otherwise only typed-in rates work.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgLabourCostService {

    public static final String WORK_INPUT = "INPUT_APPLICATION";
    public static final String WORK_HARVEST = "HARVEST";
    private static final int CANDIDATE_LIMIT = 300;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AgInputApplicationRepository inputRepository;
    private final AgHarvestRecordRepository harvestRepository;
    private final AgCropCycleRepository cropCycleRepository;
    private final AgCostEntryRepository costEntryRepository;
    private final AgFarmRepository farmRepository;
    private final AgCostEntryService costEntryService;
    private final AgFinanceSettingsService settingsService;
    private final HrFacade hrFacade;

    /** One piece of costable work, whichever record it came from. */
    private record Work(String type, UUID id, UUID cropCycleId, LocalDate date, String description, UUID workerId, String workerName, BigDecimal hours) {}

    private record RateInfo(BigDecimal base, String source, String note) {}

    /** The farm's labour that has not been costed yet, each with the rate HR would give (when the caller may see HR data). */
    @Transactional(readOnly = true)
    public LabourOverview overview(TenantId tenantId, UUID farmId, boolean hrRatesAllowed) {
        requireFarm(tenantId, farmId);
        Effective settings = settingsService.effective(tenantId);
        List<Work> work = new ArrayList<>();
        for (AgInputApplication a : inputRepository.findUncostedLabourForFarm(tenantId, farmId, PageRequest.of(0, CANDIDATE_LIMIT))) work.add(toWork(a));
        for (AgHarvestRecord h : harvestRepository.findUncostedLabourForFarm(tenantId, farmId, PageRequest.of(0, CANDIDATE_LIMIT))) work.add(toWork(h));
        work.sort(Comparator.comparing(Work::date).reversed());

        Map<UUID, Optional<EmployeeResponse>> employees = new HashMap<>();
        List<LabourCandidate> out = new ArrayList<>();
        for (Work w : work) {
            RateInfo rate = hrRate(tenantId, w, hrRatesAllowed, settings.hoursPerWeek(), employees);
            BigDecimal estimate = rate.base() == null ? null : AgLabourRules.amount(w.hours(), AgLabourRules.loadedRate(rate.base(), settings.onCostPercent()));
            out.add(new LabourCandidate(w.type(), w.id(), w.cropCycleId(), w.date(), w.description(), w.workerId(), w.workerName(), w.hours(),
                    rate.base(), rate.source(), rate.note(), estimate));
        }
        return new LabourOverview(new FinanceSettingsResponse(settings.hoursPerWeek(), settings.onCostPercent(), settings.configured()), hrRatesAllowed, out);
    }

    /**
     * Costs the chosen work into the ledger, one LABOUR entry per piece of work, against its crop cycle. All or nothing: if any item cannot be
     * costed, nothing is.
     */
    @Transactional
    public List<CostEntryResponse> cost(TenantId tenantId, UUID farmId, UUID userId, boolean hrRatesAllowed, CostLabourRequest req) {
        requireFarm(tenantId, farmId);
        if (req.items() == null || req.items().isEmpty()) throw new IllegalArgumentException("choose at least one piece of work to cost");
        Effective settings = settingsService.effective(tenantId);

        record Pending(Work work, BigDecimal baseRate, String rateSource, BigDecimal loadedRate, BigDecimal amount) {}
        Set<String> seen = new HashSet<>();
        Map<UUID, Optional<EmployeeResponse>> employees = new HashMap<>();
        List<Pending> pending = new ArrayList<>();
        for (LabourItem item : req.items()) {
            if (!seen.add(item.sourceType() + ":" + item.sourceId())) throw new IllegalArgumentException("the same piece of work is listed twice");
            Work w = loadWork(tenantId, farmId, item);
            if (costEntryRepository.countActiveBySource(tenantId, AgLabourRules.SOURCE_TYPE, w.id()) > 0) {
                throw new IllegalArgumentException("the work on " + w.date() + " is already costed; reverse its ledger entry first to cost it again");
            }
            BigDecimal base;
            String source;
            if (item.hourlyRate() != null) {
                base = item.hourlyRate();
                source = "MANUAL";
            } else {
                RateInfo hr = hrRate(tenantId, w, hrRatesAllowed, settings.hoursPerWeek(), employees);
                if (hr.base() == null) throw new IllegalArgumentException("no rate for the work on " + w.date() + ": " + hr.note() + ". Enter an hourly rate.");
                base = hr.base();
                source = "HR";
            }
            BigDecimal loaded = AgLabourRules.loadedRate(base, settings.onCostPercent());
            BigDecimal amount = AgLabourRules.amount(w.hours(), loaded);
            if (amount.signum() <= 0) throw new IllegalArgumentException("the cost of the work on " + w.date() + " rounds to nothing; check the hours and the rate");
            pending.add(new Pending(w, base, source, loaded, amount));
        }

        List<CostEntryResponse> out = new ArrayList<>();
        for (Pending p : pending) {
            String notes = "Rate R" + p.baseRate().stripTrailingZeros().toPlainString() + "/h " + ("HR".equals(p.rateSource()) ? "from HR" : "entered by hand")
                    + " + " + settings.onCostPercent().stripTrailingZeros().toPlainString() + "% on-cost";
            out.addAll(costEntryService.record(tenantId, farmId, userId, p.work().date(), "LABOUR", "Labour: " + p.work().description(),
                    AgLabourRules.SOURCE_TYPE, p.work().id(), p.work().hours(), "h", p.loadedRate(), p.amount(), notes,
                    List.of(new AgCostAllocation.Share(AgCostAllocation.CROP_CYCLE, p.work().cropCycleId(), HUNDRED))));
        }
        log.info("Labour costed items={} tenant={} farm={}", pending.size(), tenantId.getValue(), farmId);
        return out;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private Work loadWork(TenantId tenantId, UUID farmId, LabourItem item) {
        Work w;
        if (WORK_INPUT.equals(item.sourceType())) {
            w = toWork(inputRepository.findByTenantAndId(tenantId, item.sourceId()).orElseThrow(() -> new ResourceNotFoundException("InputApplication", item.sourceId().toString())));
        } else if (WORK_HARVEST.equals(item.sourceType())) {
            w = toWork(harvestRepository.findByTenantAndId(tenantId, item.sourceId()).orElseThrow(() -> new ResourceNotFoundException("HarvestRecord", item.sourceId().toString())));
        } else {
            throw new IllegalArgumentException("sourceType must be " + WORK_INPUT + " or " + WORK_HARVEST);
        }
        AgCropCycle cycle = cropCycleRepository.findActiveById(tenantId, w.cropCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("CropCycle", w.cropCycleId().toString()));
        if (!farmId.equals(cycle.getFarmId())) throw new IllegalArgumentException("that work does not belong to this farm");
        if (w.hours() == null || w.hours().signum() <= 0) throw new IllegalArgumentException("that work has no labour hours recorded");
        return w;
    }

    private RateInfo hrRate(TenantId tenantId, Work w, boolean hrRatesAllowed, BigDecimal hoursPerWeek, Map<UUID, Optional<EmployeeResponse>> employees) {
        if (w.workerId() == null) return new RateInfo(null, "NONE", "no HR employee is linked to this work" + (w.workerName() != null ? " (" + w.workerName() + ")" : ""));
        if (!hrRatesAllowed) return new RateInfo(null, "NONE", "HR access is needed to use a salary-based rate");
        Optional<EmployeeResponse> employee = employees.computeIfAbsent(w.workerId(), id -> hrFacade.findEmployeeById(tenantId, id));
        if (employee.isEmpty()) return new RateInfo(null, "NONE", "the linked employee was not found in HR");
        BigDecimal rate = AgLabourRules.hourlyRate(employee.get().grossSalary(), employee.get().payFrequency(), hoursPerWeek);
        if (rate == null) return new RateInfo(null, "NONE", "the employee has no usable salary or a pay frequency this cannot convert");
        return new RateInfo(rate, "HR", null);
    }

    private static Work toWork(AgInputApplication a) {
        String what = a.getProductUsed() != null && !a.getProductUsed().isBlank() ? a.getProductUsed() : a.getInputType();
        return new Work(WORK_INPUT, a.getId(), a.getCropCycleId(), a.getApplicationDate(), "application of " + trim(what), a.getAppliedBy(), a.getAppliedByName(), a.getLaborHours());
    }

    private static Work toWork(AgHarvestRecord h) {
        return new Work(WORK_HARVEST, h.getId(), h.getCropCycleId(), h.getHarvestDate(), "harvest", h.getHarvestedBy(), h.getHarvestedByName(), h.getLaborHours());
    }

    private static String trim(String s) {
        if (s == null) return "inputs";
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private void requireFarm(TenantId tenantId, UUID farmId) {
        farmRepository.findActiveById(tenantId, farmId).orElseThrow(() -> new ResourceNotFoundException("Farm", farmId.toString()));
    }
}
