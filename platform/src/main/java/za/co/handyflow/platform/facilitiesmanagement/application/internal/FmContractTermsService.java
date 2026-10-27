package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmBusinessCalendar;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmBusinessCalendarRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmServiceAgreementRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which contract a piece of work falls under, and stamps the terms and SLA clock onto the work order.
 * <p>
 * <b>Resolution:</b> among the client's ACTIVE agreements in force on the date, keep those covering the site and
 * the category; if more than one remains (legacy data — new overlaps are rejected), the most specific wins
 * (site-specific over all-sites, category-specific over all-categories), then the most recently started.
 * <p>
 * <b>Stamping</b> (done once, when the work order is created): agreement id, coverage (INCLUDED for a retainer,
 * otherwise CHARGEABLE), the labour rate and parts markup, and — if the agreement has an SLA target for the
 * work order's priority — the response and resolution due times. Because the terms live on the work order, later
 * edits to the agreement never rewrite work already raised. (If the priority is raised at approval, the SLA is
 * re-derived from the agreement's current target for the new priority.)
 * <p>
 * <b>Pause:</b> while a work order's SLA is paused (see {@link #resumeSla}), elapsed SLA time is measured on the
 * same clock as the target and added to the resolution deadline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmContractTermsService {

    private final FmServiceAgreementRepository agreementRepository;
    private final FmBusinessCalendarRepository calendarRepository;

    public Optional<FmServiceAgreement> resolveAgreement(TenantId tenantId, UUID clientId, UUID siteId,
                                                         String category, LocalDate onDate) {
        return agreementRepository.findActiveAsOfDate(tenantId, clientId, onDate).stream()
                .filter(a -> a.coversSite(siteId) && a.coversCategory(category))
                .max(Comparator.comparingInt(FmServiceAgreement::specificity).thenComparing(FmServiceAgreement::getStartDate));
    }

    /** Stamp agreement, coverage, rate card and SLA onto a newly created work order. */
    public void applyTerms(TenantId tenantId, FmWorkOrder wo) {
        LocalDate date = wo.getScheduledDate() != null ? wo.getScheduledDate() : LocalDate.now();
        Optional<FmServiceAgreement> resolved = resolveAgreement(tenantId, wo.getClientId(), wo.getSiteId(), wo.getCategory(), date);
        if (resolved.isEmpty()) {
            wo.applyContract(null, "CHARGEABLE", null, null);
            return;
        }
        FmServiceAgreement agreement = resolved.get();
        wo.applyContract(agreement.getId(), agreement.isRetainer() ? "INCLUDED" : "CHARGEABLE",
                agreement.getHourlyRate(), agreement.getPartsMarkupPercent());
        applySlaFrom(tenantId, wo, agreement);
    }

    /** Re-derive the SLA after the work order's priority changed (e.g. raised at approval). */
    public void reapplySla(TenantId tenantId, FmWorkOrder wo) {
        if (wo.getAgreementId() == null) return;
        agreementRepository.findActiveById(tenantId, wo.getAgreementId()).ifPresent(a -> applySlaFrom(tenantId, wo, a));
    }

    private void applySlaFrom(TenantId tenantId, FmWorkOrder wo, FmServiceAgreement agreement) {
        agreement.slaTargetFor(wo.getPriority()).ifPresent(target -> {
            FmSlaClock.Calendar cal = calendarFor(tenantId);
            Instant created = wo.getCreatedAt();
            Instant responseDue = FmSlaClock.addMinutes(created, target.getResponseMinutes(), target.getClock(), cal);
            Instant resolutionDue = FmSlaClock.addMinutes(created,
                    (long) target.getResolutionMinutes() + wo.getSlaPausedMinutes(), target.getClock(), cal);
            wo.applySla(target.getResponseMinutes(), target.getResolutionMinutes(), target.getClock(), responseDue, resolutionDue);
        });
    }

    /**
     * Restart a paused resolution clock: measure the paused interval in SLA minutes, add it to the total paused
     * time and move the resolution deadline out by that much.
     */
    public void resumeSla(TenantId tenantId, FmWorkOrder wo, Instant pausedAt, Instant now) {
        if (wo.getSlaClock() == null || wo.getResolutionTargetMinutes() == null) {
            wo.clearSlaPause();
            return;
        }
        FmSlaClock.Calendar cal = calendarFor(tenantId);
        long pausedMinutes = FmSlaClock.minutesBetween(pausedAt, now, wo.getSlaClock(), cal);
        long totalPaused = wo.getSlaPausedMinutes() + pausedMinutes;
        Instant newDue = FmSlaClock.addMinutes(wo.getCreatedAt(), (long) wo.getResolutionTargetMinutes() + totalPaused,
                wo.getSlaClock(), cal);
        wo.resumeSla(pausedMinutes, newDue);
    }

    public FmSlaClock.Calendar calendarFor(TenantId tenantId) {
        return calendarRepository.findByTenant(tenantId)
                .map(FmBusinessCalendar::toClockCalendar)
                .orElseGet(FmSlaClock.Calendar::defaults);
    }
}