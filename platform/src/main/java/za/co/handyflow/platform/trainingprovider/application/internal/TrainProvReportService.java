package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAuthority;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgramme;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvReportObligation;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvReportPeriod;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvAuthorityRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvReportObligationRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvReportPeriodRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.ReportRules;
import za.co.handyflow.platform.trainingprovider.dto.ReportDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Regulatory reporting. Obligations are set up by the provider with a written source note and nothing is built in. A submission is a staff record
 * and an acknowledgement is recorded separately. Overdue is worked out from the due day when asked, never stored, and nothing here says "compliant".
 */
@Service
@RequiredArgsConstructor
public class TrainProvReportService {

    private final TrainProvReportObligationRepository obligations;
    private final TrainProvReportPeriodRepository periods;
    private final TrainProvAuthorityRepository authorities;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvNumberGenerator numbers;

    public List<ObligationView> listObligations(TenantId t, UUID authorityId, String status) {
        return obligations.search(t, authorityId, blank(status)).stream().<ObligationView>map(o -> view(t, o)).toList();
    }

    @Transactional
    public ObligationView createObligation(TenantId t, ObligationRequest r, UUID by) {
        TrainProvAuthority a = authorities.findForTenant(t, r.authorityId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuthority", r.authorityId().toString()));
        if (r.programmeId() != null) {
            programmes.findForTenant(t, r.programmeId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", r.programmeId().toString()));
        }
        checkName(t, a.getId(), r.programmeId(), r.name(), null);
        TrainProvReportObligation o = guard(() -> TrainProvReportObligation.create(t, numbers.nextObligationNumber(t), a.getId(), r.programmeId(), r.name(), r.cadence(),
                r.dueDaysAfterEnd(), r.sourceNote(), by));
        obligations.save(o);
        return view(t, o);
    }

    @Transactional
    public ObligationView updateObligation(TenantId t, UUID id, ObligationUpdate r) {
        TrainProvReportObligation o = obligation(t, id);
        checkName(t, o.getAuthorityId(), o.getProgrammeId(), r.name(), o.getId());
        guard(() -> { o.change(r.name(), r.cadence(), r.dueDaysAfterEnd(), r.sourceNote()); return null; });
        obligations.save(o);
        return view(t, o);
    }

    @Transactional
    public ObligationView retireObligation(TenantId t, UUID id, String reason, UUID by) {
        TrainProvReportObligation o = obligation(t, id);
        guard(() -> { o.retire(reason, by); return null; });
        obligations.save(o);
        return view(t, o);
    }

    public List<PeriodView> listPeriods(TenantId t, UUID obligationId, String status, String state, LocalDate today, Integer soonDays) {
        int soon = soonDays == null ? ReportRules.DEFAULT_SOON_DAYS : soonDays;
        return periods.search(t, obligationId, blank(status)).stream().<PeriodView>map(p -> view(t, p, today, soon))
                .filter(v -> blank(state) == null || v.state().equals(state.trim())).toList();
    }

    @Transactional
    public PeriodView createPeriod(TenantId t, PeriodRequest r, UUID by, LocalDate today) {
        TrainProvReportObligation o = obligation(t, r.obligationId());
        LocalDate computed = r.periodEnd() == null ? null : ReportRules.computedDue(r.periodEnd(), o.getDueDaysAfterEnd());
        LocalDate due = r.dueOn() == null ? computed : r.dueOn();
        String problem = ReportRules.periodProblem(o.isActive(), periods.windowTaken(t, o.getId(), r.periodStart(), r.periodEnd()), r.periodStart(), r.periodEnd(), computed, due,
                r.dueOverrideReason());
        if (problem != null) {
            boolean taken = problem.contains("already exists") || problem.contains("retired");
            throw new HandyFlowException(problem, taken ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, taken ? "TP_REPORT_STATE" : "TP_REPORT_INVALID");
        }
        TrainProvReportPeriod p = TrainProvReportPeriod.create(t, numbers.nextReportPeriodNumber(t), o.getId(), r.periodStart(), r.periodEnd(), due,
                due.equals(computed) ? null : r.dueOverrideReason(), by);
        periods.save(p);
        return view(t, p, today, ReportRules.DEFAULT_SOON_DAYS);
    }

    @Transactional
    public PeriodView submit(TenantId t, UUID id, SubmitRequest r, UUID by, LocalDate today) {
        TrainProvReportPeriod p = period(t, id);
        guard(() -> { p.submit(r.submittedOn(), r.submissionRef(), r.note(), by, today); return null; });
        periods.save(p);
        return view(t, p, today, ReportRules.DEFAULT_SOON_DAYS);
    }

    @Transactional
    public PeriodView acknowledge(TenantId t, UUID id, AcknowledgeRequest r, LocalDate today) {
        TrainProvReportPeriod p = period(t, id);
        guard(() -> { p.acknowledge(r.acknowledgedOn(), r.acknowledgementRef(), today); return null; });
        periods.save(p);
        return view(t, p, today, ReportRules.DEFAULT_SOON_DAYS);
    }

    @Transactional
    public PeriodView waive(TenantId t, UUID id, String reason, UUID by, LocalDate today) {
        TrainProvReportPeriod p = period(t, id);
        guard(() -> { p.waive(reason, by); return null; });
        periods.save(p);
        return view(t, p, today, ReportRules.DEFAULT_SOON_DAYS);
    }

    @Transactional
    public PeriodView voidPeriod(TenantId t, UUID id, String reason, UUID by, LocalDate today) {
        TrainProvReportPeriod p = period(t, id);
        guard(() -> { p.voidIt(reason, by); return null; });
        periods.save(p);
        return view(t, p, today, ReportRules.DEFAULT_SOON_DAYS);
    }

    private void checkName(TenantId t, UUID authorityId, UUID programmeId, String name, UUID excludeId) {
        if (name != null && obligations.liveNameTaken(t, authorityId, programmeId, name.trim(), excludeId)) {
            throw new HandyFlowException("There is already an active obligation with that name for this authority and programme", HttpStatus.CONFLICT, "TP_REPORT_DUPLICATE");
        }
    }

    private ObligationView view(TenantId t, TrainProvReportObligation o) {
        String authority = authorities.findForTenant(t, o.getAuthorityId()).map(TrainProvAuthority::getName).orElse(null);
        String programme = o.getProgrammeId() == null ? null : programmes.findForTenant(t, o.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        return new ObligationView(o.getId(), o.getObligationNo(), o.getAuthorityId(), authority, o.getProgrammeId(), programme, o.getName(), o.getCadence(), o.getDueDaysAfterEnd(),
                o.getSourceNote(), o.getStatus(), o.getRetiredReason());
    }

    private PeriodView view(TenantId t, TrainProvReportPeriod p, LocalDate today, int soonDays) {
        TrainProvReportObligation o = obligations.findForTenant(t, p.getObligationId()).orElse(null);
        String authority = o == null ? null : authorities.findForTenant(t, o.getAuthorityId()).map(TrainProvAuthority::getName).orElse(null);
        String programme = o == null || o.getProgrammeId() == null ? null : programmes.findForTenant(t, o.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        return new PeriodView(p.getId(), p.getPeriodNo(), p.getObligationId(), o == null ? null : o.getObligationNo(), o == null ? null : o.getName(), authority, programme,
                p.getPeriodStart(), p.getPeriodEnd(), p.getDueOn(), p.getDueOverrideReason(), p.getStatus(), ReportRules.dueState(p.getStatus(), p.getDueOn(), today, soonDays),
                p.getSubmittedOn(), p.getSubmissionRef(), p.getAcknowledgedOn(), p.getAcknowledgementRef(), p.getWaivedReason(), p.getVoidReason(), p.getNote());
    }

    private TrainProvReportObligation obligation(TenantId t, UUID id) {
        return obligations.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvReportObligation", id.toString()));
    }

    private TrainProvReportPeriod period(TenantId t, UUID id) {
        return periods.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvReportPeriod", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_REPORT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_REPORT_INVALID");
        }
    }
}
