package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgramme;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvWorkplaceLog;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvWorkplacePlacement;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvWorkplaceLogRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvWorkplacePlacementRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.WorkplaceRules;
import za.co.handyflow.platform.trainingprovider.dto.WorkplaceDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Workplace placements and logbooks. Hours are totalled, split into confirmed and unconfirmed, and never compared with a threshold. A mentor's
 * confirmation is recorded by staff and names the mentor. Logged hours are not a competency decision, and nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvWorkplaceService {

    private final TrainProvWorkplacePlacementRepository placements;
    private final TrainProvWorkplaceLogRepository logs;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvNumberGenerator numbers;

    public List<PlacementView> list(TenantId t, UUID enrolmentId, String status) {
        return placements.search(t, enrolmentId, blank(status)).stream().<PlacementView>map(p -> view(t, p)).toList();
    }

    public PlacementDetail get(TenantId t, UUID id) {
        TrainProvWorkplacePlacement p = placement(t, id);
        return new PlacementDetail(view(t, p), logs.forPlacement(t, p.getId()).stream().<LogView>map(TrainProvWorkplaceService::logView).toList());
    }

    @Transactional
    public PlacementView create(TenantId t, PlacementRequest r, UUID by) {
        TrainProvLearnerEnrolment e = enrolment(t, r.enrolmentId());
        String problem = WorkplaceRules.placementProblem(e.getStatus(), r.hostName(), r.mentorName(), r.startDate(), r.endDate(), e.getStartDate());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_PLACEMENT_STATE");
        }
        TrainProvWorkplacePlacement p = guard(() -> TrainProvWorkplacePlacement.create(t, numbers.nextPlacementNumber(t), e.getId(), r.hostName(), r.hostContact(),
                r.mentorName(), r.mentorContact(), r.agreementRef(), r.startDate(), r.endDate(), by));
        placements.save(p);
        return view(t, p);
    }

    @Transactional
    public PlacementView update(TenantId t, UUID id, PlacementUpdate r) {
        TrainProvWorkplacePlacement p = placement(t, id);
        TrainProvLearnerEnrolment e = enrolment(t, p.getEnrolmentId());
        guard(() -> {
            if (e.getStartDate() != null && r.startDate() != null && r.startDate().isBefore(e.getStartDate())) {
                throw new IllegalArgumentException("The placement cannot start before the enrolment started");
            }
            p.change(r.hostName(), r.hostContact(), r.mentorName(), r.mentorContact(), r.agreementRef(), r.startDate(), r.endDate());
            return null;
        });
        placements.save(p);
        return view(t, p);
    }

    @Transactional
    public PlacementView start(TenantId t, UUID id, LocalDate on, LocalDate today) {
        TrainProvWorkplacePlacement p = placement(t, id);
        TrainProvLearnerEnrolment e = enrolment(t, p.getEnrolmentId());
        if (placements.activeExists(t, e.getId())) {
            throw new HandyFlowException("This enrolment already has an active placement. Complete or cancel it first", HttpStatus.CONFLICT, "TP_PLACEMENT_STATE");
        }
        guard(() -> { p.start(e.getStatus(), on, today); return null; });
        placements.save(p);
        return view(t, p);
    }

    @Transactional
    public PlacementView complete(TenantId t, UUID id, LocalDate on, LocalDate today) {
        TrainProvWorkplacePlacement p = placement(t, id);
        guard(() -> { p.complete(on, today); return null; });
        placements.save(p);
        return view(t, p);
    }

    @Transactional
    public PlacementView cancel(TenantId t, UUID id, String reason) {
        TrainProvWorkplacePlacement p = placement(t, id);
        guard(() -> { p.cancel(reason); return null; });
        placements.save(p);
        return view(t, p);
    }

    @Transactional
    public LogView addLog(TenantId t, UUID placementId, LogRequest r, UUID by, LocalDate today) {
        TrainProvWorkplacePlacement p = placement(t, placementId);
        String problem = WorkplaceRules.logProblem(p.getStatus(), r.logDate(), p.getStartedOn(), p.getEndDate(), today, r.hours(), r.activity(),
                r.logDate() != null && logs.dayTaken(t, p.getId(), r.logDate()));
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_LOG_STATE");
        }
        TrainProvWorkplaceLog l = TrainProvWorkplaceLog.record(t, p.getId(), r.logDate(), r.hours(), r.activity(), by);
        logs.save(l);
        return logView(l);
    }

    @Transactional
    public LogView confirm(TenantId t, UUID logId, ConfirmRequest r, UUID by, LocalDate today) {
        TrainProvWorkplaceLog l = log(t, logId);
        guard(() -> { l.confirm(r.confirmedOn(), r.mentorName(), by, today); return null; });
        logs.save(l);
        return logView(l);
    }

    @Transactional
    public LogView voidLog(TenantId t, UUID logId, String reason, UUID by) {
        TrainProvWorkplaceLog l = log(t, logId);
        guard(() -> { l.voidIt(reason, by); return null; });
        logs.save(l);
        return logView(l);
    }

    private static LogView logView(TrainProvWorkplaceLog l) {
        return new LogView(l.getId(), l.getPlacementId(), l.getLogDate(), l.getHours(), l.getActivity(), l.getStatus(), l.getVoidReason(), l.getConfirmedOn(), l.getConfirmedByName());
    }

    private PlacementView view(TenantId t, TrainProvWorkplacePlacement p) {
        TrainProvLearnerEnrolment e = enrolment(t, p.getEnrolmentId());
        BigDecimal confirmed = BigDecimal.ZERO;
        BigDecimal unconfirmed = BigDecimal.ZERO;
        for (TrainProvWorkplaceLog l : logs.forPlacement(t, p.getId())) {
            if (!WorkplaceRules.RECORDED.equals(l.getStatus())) {
                continue;
            }
            if (l.isConfirmed()) {
                confirmed = confirmed.add(l.getHours());
            } else {
                unconfirmed = unconfirmed.add(l.getHours());
            }
        }
        String learner = learners.findForTenant(t, e.getLearnerId()).map(x -> x.getFirstNames() + " " + x.getSurname()).orElse(null);
        String programme = programmes.findForTenant(t, e.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse(null);
        return new PlacementView(p.getId(), p.getPlacementNo(), e.getId(), e.getEnrolmentNo(), learner, programme, p.getHostName(), p.getHostContact(), p.getMentorName(),
                p.getMentorContact(), p.getAgreementRef(), p.getStartDate(), p.getEndDate(), p.getStatus(), p.getStartedOn(), p.getEndedOn(), p.getCancelReason(),
                List.copyOf(WorkplaceRules.nextStatuses(p.getStatus())), confirmed, unconfirmed, WorkplaceRules.summary(confirmed, unconfirmed));
    }

    private TrainProvWorkplacePlacement placement(TenantId t, UUID id) {
        return placements.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvWorkplacePlacement", id.toString()));
    }

    private TrainProvWorkplaceLog log(TenantId t, UUID id) {
        return logs.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvWorkplaceLog", id.toString()));
    }

    private TrainProvLearnerEnrolment enrolment(TenantId t, UUID id) {
        return enrolments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_PLACEMENT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_PLACEMENT_INVALID");
        }
    }
}
