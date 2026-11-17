package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelGaugeReading;
import za.co.handyflow.platform.fuel.domain.model.FuelImportAutoAccept;
import za.co.handyflow.platform.fuel.domain.model.FuelPosImport;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftAutoAcceptLog;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftAutoAcceptLogRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelGaugeReadingRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelImportAutoAcceptRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.dto.AutoAcceptDtos.Setting;
import za.co.handyflow.platform.fuel.dto.GaugeImportDtos;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.AcceptRequest;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IngestResult;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Accepts a clean import straight away when the company has switched that on. It runs after the import was staged and committed, and
 * accepts through the same code a person uses, so every normal rule still applies. Any refusal leaves the import pending for a person.
 * Nothing is accepted for a company that has not switched it on. An automatic acceptance is recorded with no deciding user and a note.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelImportAutoAcceptService {

    static final String NOTE = "Accepted automatically";

    private final FuelImportAutoAcceptRepository settings;
    private final FuelPosImportRepository posImports;
    private final FuelGaugeReadingRepository gaugeReadings;
    private final FuelPosImportService pos;
    private final FuelGaugeImportService gauges;
    private final FuelShiftRepository shiftRepo;
    private final FuelShiftAutoAcceptLogRepository acceptLog;
    private final FuelShiftAlertService alerts;
    private final FuelToleranceService tolerances;

    @Transactional(readOnly = true)
    public Setting get(TenantId tenant) {
        return settings.findForTenant(tenant).map(a -> new Setting(a.isShiftsEnabled(), a.isGaugesEnabled(), a.getGaugeMaxDifferenceLitres()))
                .orElse(new Setting(false, false, null));
    }

    @Transactional
    public Setting save(TenantId tenant, boolean shifts, boolean gauges, BigDecimal maxDifference, UUID by) {
        FuelImportAutoAccept a = settings.findForTenant(tenant).orElseGet(() -> FuelImportAutoAccept.forTenant(tenant));
        a.update(shifts, gauges, maxDifference, by);
        settings.save(a);
        return new Setting(a.isShiftsEnabled(), a.isGaugesEnabled(), a.getGaugeMaxDifferenceLitres());
    }

    /**
     * Called after a shift was staged. Returns the result to send back: updated to ACCEPTED when it was accepted here, otherwise as given.
     * It is accepted only through the normal close, which refuses a variance outside the tolerance in force, so only a shift within tolerance
     * is accepted. Each acceptance is logged with its variance and tolerance and the supervisor is told; a refusal tells the exception recipients.
     */
    public IngestResult afterShift(IngestResult r) {
        if (r == null || r.duplicate() || !"PENDING".equals(r.status()) || (r.problems() != null && !r.problems().isEmpty())) return r;
        FuelPosImport imp = null;
        try {
            imp = posImports.findById(r.importId()).orElse(null);
            if (imp == null || !imp.isPending()) return r;
            Setting s = get(imp.getTenantId());
            if (!s.shiftsEnabled()) return r;
            var accepted = pos.accept(imp.getTenantId(), imp.getId(), new AcceptRequest(null, NOTE), null);
            recordAcceptance(imp, accepted == null ? null : accepted.shiftId());
            return new IngestResult(r.importId(), "ACCEPTED", false, r.problems());
        } catch (RuntimeException ex) {
            log.info("Fuel shift import {} left for review: {}", r.importId(), ex.getMessage());
            if (imp != null) alerts.notAccepted(imp.getTenantId(), imp.getExternalId(), ex.getMessage());
            return r;
        }
    }

    /** The acceptance stands even if the log or the notice fails. */
    private void recordAcceptance(FuelPosImport imp, UUID shiftId) {
        try {
            if (shiftId == null) return;
            var shift = shiftRepo.findByTenantAndId(imp.getTenantId(), shiftId).orElse(null);
            if (shift == null) return;
            acceptLog.save(FuelShiftAutoAcceptLog.of(imp.getTenantId(), imp.getId(), shift));
            alerts.autoAccepted(imp.getTenantId(), shift);
        } catch (RuntimeException ex) {
            log.warn("Shift import {} was accepted but its record or notice failed: {}", imp.getId(), ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<za.co.handyflow.platform.fuel.dto.ShiftControlDtos.AutoAcceptLogView> log(TenantId tenant, int limit) {
        return acceptLog.findRecent(tenant, org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 200)))).stream()
                .map(l -> new za.co.handyflow.platform.fuel.dto.ShiftControlDtos.AutoAcceptLogView(l.getId(), l.getShiftId(), l.getShiftNumber(), l.getAttendantName(),
                        l.getVarianceAmount(), l.getToleranceApplied(), l.getActor(), l.getAcceptedAt())).toList();
    }

    /** Called after a gauge reading was staged. Accepted only within the company's own difference limit from the book level. */
    public IngestResult afterGauge(IngestResult r) {
        if (r == null || r.duplicate() || !"PENDING".equals(r.status()) || (r.problems() != null && !r.problems().isEmpty())) return r;
        try {
            FuelGaugeReading g = gaugeReadings.findById(r.importId()).orElse(null);
            if (g == null || !g.isPending()) return r;
            Setting s = get(g.getTenantId());
            if (!s.gaugesEnabled() || s.gaugeMaxDifferenceLitres() == null) return r;
            BigDecimal diff = gauges.differenceFromBook(g.getTenantId(), g.getId());
            BigDecimal limit = tolerances.gaugeLimitFor(g.getTenantId(), gauges.fuelTypeOf(g.getTenantId(), g.getId()), s.gaugeMaxDifferenceLitres());
            if (diff == null || limit == null || diff.compareTo(limit) > 0) return r;
            gauges.accept(g.getTenantId(), g.getId(), new GaugeImportDtos.AcceptRequest(NOTE), null);
            return new IngestResult(r.importId(), "ACCEPTED", false, r.problems());
        } catch (RuntimeException ex) {
            log.info("Fuel gauge reading {} left for review: {}", r.importId(), ex.getMessage());
            return r;
        }
    }
}
