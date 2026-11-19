package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.domain.repository.EsgMetricRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.EsgReadingRepository;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.ReasonRequest;
import za.co.handyflow.platform.legalcompliance.dto.EsgDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ESG metrics and readings. Readings are never edited: a correction records a new reading for the period and marks the earlier one superseded (kept, with the reason).
 * Whether a reading met its target uses the target and direction copied onto that reading when it was recorded, so editing a metric's target later does not rewrite history.
 */
@Service
@RequiredArgsConstructor
public class EsgService {

    static final String METRIC = "ESG_METRIC", READING = "ESG_READING";

    private final EsgMetricRepository metrics;
    private final EsgReadingRepository readings;
    private final RegulatoryAuditService trail;

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    @Transactional
    public MetricDetail create(TenantId t, MetricRequest r, UUID uid, String uname) {
        EsgMetric m = guarded(() -> EsgMetric.create(t, String.format("ESG-%05d", metrics.nextRef()), r.name(), r.description(), r.category(), r.unit(), r.frequency(), r.direction(),
                r.targetValue(), r.ownerName(), r.frameworkRef()));
        metrics.save(m);
        trail.record(t.getValue(), METRIC, m.getId(), "CREATED", m.getReference() + " " + m.getName() + " (" + m.getUnit() + ", " + m.getFrequency().name().toLowerCase() + ")", uid, uname);
        return detail(t, m);
    }

    @Transactional
    public MetricDetail update(TenantId t, UUID id, MetricRequest r, UUID uid, String uname) {
        EsgMetric m = load(t, id);
        var oldTarget = m.getTargetValue();
        guarded(() -> { m.update(r.name(), r.description(), r.category(), r.unit(), r.frequency(), r.direction(), r.targetValue(), r.ownerName(), r.frameworkRef()); return null; });
        metrics.save(m);
        boolean targetChanged = !Objects.equals(oldTarget == null ? null : oldTarget.stripTrailingZeros(), m.getTargetValue() == null ? null : m.getTargetValue().stripTrailingZeros());
        trail.record(t.getValue(), METRIC, id, "UPDATED", targetChanged ? "Target " + oldTarget + " → " + m.getTargetValue() + " (earlier readings keep the target that applied to them)" : "Details changed", uid, uname);
        return detail(t, m);
    }

    @Transactional
    public MetricDetail retire(TenantId t, UUID id, ReasonRequest r, UUID uid, String uname) {
        EsgMetric m = load(t, id);
        guarded(() -> { m.retire(r.reason()); return null; });
        metrics.save(m);
        trail.record(t.getValue(), METRIC, id, "RETIRED", r.reason().trim(), uid, uname);
        return detail(t, m);
    }

    @Transactional
    public MetricDetail record(TenantId t, UUID metricId, ReadingRequest r, UUID uid, String uname) {
        EsgMetric m = load(t, metricId);
        if (readings.findCurrentForPeriod(t, metricId, r.periodEnd()).isPresent())
            throw new HandyFlowException("A reading already exists for the period ending " + r.periodEnd() + ". Correct it instead of recording another", HttpStatus.CONFLICT, "INVALID_STATE");
        EsgReading x = guarded(() -> EsgReading.record(t, m, r.periodStart(), r.periodEnd(), r.value(), r.notes(), null, uid, uname, today()));
        readings.save(x);
        trail.record(t.getValue(), READING, x.getId(), "RECORDED", m.getReference() + " " + m.getName() + ": " + x.getValue().stripTrailingZeros().toPlainString() + " " + m.getUnit() + " for period ending " + x.getPeriodEnd(), uid, uname);
        return detail(t, m);
    }

    @Transactional
    public MetricDetail correct(TenantId t, UUID readingId, CorrectionRequest r, UUID uid, String uname) {
        EsgReading old = readings.findForTenant(t, readingId).orElseThrow(() -> new ResourceNotFoundException("Reading", readingId.toString()));
        EsgMetric m = load(t, old.getMetricId());
        guarded(() -> { old.supersede(r.reason(), Instant.now()); return null; });
        readings.saveAndFlush(old);   // flushed first: the unique index allows one current reading per period
        EsgReading replacement = guarded(() -> EsgReading.record(t, m, r.periodStart(), r.periodEnd(), r.value(), r.notes(), old.getId(), uid, uname, today()));
        readings.save(replacement);
        trail.record(t.getValue(), READING, old.getId(), "SUPERSEDED", "Was " + old.getValue().stripTrailingZeros().toPlainString() + " " + m.getUnit() + "; now "
                + replacement.getValue().stripTrailingZeros().toPlainString() + ". " + r.reason().trim(), uid, uname);
        return detail(t, m);
    }

    @Transactional(readOnly = true)
    public List<MetricResponse> list(TenantId t, boolean includeRetired) {
        Map<UUID, List<EsgReading>> byMetric = readings.findCurrentForTenant(t).stream().collect(Collectors.groupingBy(EsgReading::getMetricId));
        return metrics.search(t, includeRetired).stream().map(m -> response(m, byMetric.getOrDefault(m.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public MetricDetail get(TenantId t, UUID id) { return detail(t, load(t, id)); }

    @Transactional(readOnly = true)
    public EsgSummary summary(TenantId t) {
        List<MetricResponse> active = list(t, false);
        return new EsgSummary(active.size(), count(active, EsgCategory.ENVIRONMENTAL), count(active, EsgCategory.SOCIAL), count(active, EsgCategory.GOVERNANCE),
                active.stream().filter(MetricResponse::readingOverdue).count(), active.stream().filter(m -> "MISSED".equals(m.latestTargetStatus())).count(),
                active.stream().filter(m -> m.readingCount() == 0).count());
    }

    private static long count(List<MetricResponse> l, EsgCategory c) { return l.stream().filter(m -> m.category() == c).count(); }

    private MetricDetail detail(TenantId t, EsgMetric m) {
        List<EsgReading> all = readings.findForMetric(t, m.getId());
        List<EsgReading> current = all.stream().filter(EsgReading::current).toList();
        return new MetricDetail(response(m, current), all.stream().map(EsgService::reading).toList());
    }

    private MetricResponse response(EsgMetric m, List<EsgReading> current) {
        EsgReading latest = current.stream().max(Comparator.comparing(EsgReading::getPeriodEnd)).orElse(null);
        LocalDate recordedOn = m.getCreatedAt().atZone(ZoneId.of("Africa/Johannesburg")).toLocalDate();
        boolean active = m.getStatus() == EsgMetricStatus.ACTIVE;
        return new MetricResponse(m.getId(), m.getReference(), m.getName(), m.getDescription(), m.getCategory(), m.getUnit(), m.getFrequency(), m.getDirection(), m.getTargetValue(), m.getOwnerName(),
                m.getFrameworkRef(), m.getStatus(), latest == null ? null : latest.getPeriodEnd(), latest == null ? null : latest.getValue(),
                latest == null ? null : EsgRules.targetStatus(latest.getDirectionSnapshot(), latest.getTargetSnapshot(), latest.getValue()).name(),
                active && EsgRules.readingOverdue(m.getFrequency(), latest == null ? null : latest.getPeriodEnd(), recordedOn, today()), current.size(), m.getRetiredReason());
    }

    private static ReadingResponse reading(EsgReading x) {
        return new ReadingResponse(x.getId(), x.getPeriodStart(), x.getPeriodEnd(), x.getValue(), x.getNotes(), x.getTargetSnapshot(), x.getDirectionSnapshot(),
                EsgRules.targetStatus(x.getDirectionSnapshot(), x.getTargetSnapshot(), x.getValue()).name(), x.getRecordedByName(), x.getCorrectsId(), !x.current(), x.getSupersededAt(),
                x.getSupersedeReason(), x.getCreatedAt());
    }

    private EsgMetric load(TenantId t, UUID id) { return metrics.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Metric", id.toString())); }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
