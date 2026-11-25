package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.EnrolmentRules;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.CoverageView;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Applications and learner enrolments. A person records the entry check and the system never decides
 * eligibility. Enrolling into an accredited programme uses the accreditation cover check for that site on
 * the start date and records its answer; if there is no cover only an administrator can go ahead, in writing.
 * Enrolments are frozen to the programme version and site; nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class TrainProvEnrolmentService {

    private final TrainProvLearnerRepository learners;
    private final TrainProvLearnerApplicationRepository applications;
    private final TrainProvLearnerApplicationEventRepository applicationEvents;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerEnrolmentEventRepository enrolmentEvents;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvProgrammeOfferingRepository offerings;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvClientRepository clients;
    private final TrainProvAccreditationService accreditation;
    private final TrainProvNumberGenerator numbers;

    // ---- applications --------------------------------------------------------

    public List<ApplicationView> applications(TenantId t, String status, UUID learnerId, UUID programmeId) {
        return applications.search(t, status, learnerId, programmeId).stream().<ApplicationView>map(a -> applicationView(t, a)).toList();
    }

    public ApplicationDetail application(TenantId t, UUID id) {
        TrainProvLearnerApplication a = loadApplication(t, id);
        List<EventView> history = applicationEvents.findForApplication(t, id).stream()
                .<EventView>map(e -> new EventView(e.getId(), e.getFromStatus(), e.getToStatus(), e.getReason(), e.getOverrideReason(), e.getActedBy(), e.getActedAt()))
                .toList();
        return new ApplicationDetail(applicationView(t, a), history);
    }

    @Transactional
    public ApplicationView apply(TenantId t, ApplicationRequest r, UUID by) {
        TrainProvLearner learner = learner(t, r.learnerId());
        if (!"ACTIVE".equals(learner.getStatus())) {
            throw new HandyFlowException("This learner is archived", HttpStatus.CONFLICT, "TP_ENROLMENT_STATE");
        }
        TrainProvProgramme p = programme(t, r.programmeId());
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new HandyFlowException("Applications can only be made to an active programme", HttpStatus.CONFLICT, "TP_ENROLMENT_STATE");
        }
        if (r.siteId() != null) {
            site(t, r.siteId());
        }
        if (applications.openExists(t, r.learnerId(), r.programmeId())) {
            throw new HandyFlowException("This learner already has an open application for this programme", HttpStatus.CONFLICT, "TP_ENROLMENT_STATE");
        }
        TrainProvLearnerApplication a = TrainProvLearnerApplication.create(t, r.learnerId(), r.programmeId(), r.siteId(), r.preferredStart(), r.sourceNote(), by);
        applications.save(a);
        applicationEvents.save(TrainProvLearnerApplicationEvent.of(t, a.getId(), null, EnrolmentRules.RECEIVED, null, null, by));
        return applicationView(t, a);
    }

    @Transactional
    public ApplicationView transitionApplication(TenantId t, UUID id, ApplicationTransitionRequest r, UUID by, boolean admin) {
        TrainProvLearnerApplication a = loadApplication(t, id);
        if (EnrolmentRules.ACCEPTED.equals(r.toStatus()) && Boolean.FALSE.equals(r.entryRequirementsMet()) && !admin) {
            throw new HandyFlowException("Only an administrator can accept a learner who does not meet the entry requirements", HttpStatus.FORBIDDEN, "TP_ENTRY_ADMIN_ONLY");
        }
        String from = a.getStatus();
        guard(() -> { a.moveTo(r.toStatus(), r.reason(), r.entryRequirementsMet(), r.entryCheckNote(), r.overrideReason(), by); return null; });
        applications.save(a);
        boolean overridden = EnrolmentRules.ACCEPTED.equals(r.toStatus()) && Boolean.FALSE.equals(r.entryRequirementsMet());
        applicationEvents.save(TrainProvLearnerApplicationEvent.of(t, id, from, r.toStatus(), r.reason(), overridden ? r.overrideReason() : null, by));
        return applicationView(t, a);
    }

    // ---- enrolments ----------------------------------------------------------

    public List<EnrolmentView> enrolments(TenantId t, String status, UUID learnerId, UUID programmeId) {
        return enrolments.search(t, status, learnerId, programmeId).stream().<EnrolmentView>map(e -> enrolmentView(t, e)).toList();
    }

    public EnrolmentDetail enrolment(TenantId t, UUID id) {
        TrainProvLearnerEnrolment e = loadEnrolment(t, id);
        List<EventView> history = enrolmentEvents.findForEnrolment(t, id).stream()
                .<EventView>map(h -> new EventView(h.getId(), h.getFromStatus(), h.getToStatus(), h.getReason(), null, h.getActedBy(), h.getActedAt()))
                .toList();
        return new EnrolmentDetail(enrolmentView(t, e), history);
    }

    @Transactional
    public EnrolmentView enrol(TenantId t, EnrolRequest r, UUID by, boolean admin, LocalDate today) {
        TrainProvLearner learner = learner(t, r.learnerId());
        TrainProvProgramme p = programme(t, r.programmeId());
        TrainProvProgrammeVersion version = versions.findApproved(t, p.getId()).orElse(null);
        TrainProvDeliverySite site = site(t, r.siteId());
        boolean offered = offerings.liveExists(t, p.getId(), r.siteId());
        String outcome = null;
        if ("ACCREDITED".equals(p.getTrainingActivity()) && offered) {
            CoverageView c = accreditation.programmeCoverage(t, p.getId(), p.getCourseId(), r.siteId(), r.startDate());
            outcome = c.outcome();
        }
        String problem = EnrolmentRules.enrolProblem("ACTIVE".equals(learner.getStatus()), p.getStatus(), version != null, offered,
                p.getTrainingActivity(), outcome, admin, r.overrideReason(), r.startDate(), r.expectedEndDate());
        if (problem != null) {
            throw new HandyFlowException(problem, problem.contains("administrator") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, "TP_ENROLMENT_INVALID");
        }
        if (enrolments.liveExists(t, r.learnerId(), r.programmeId())) {
            throw new HandyFlowException("This learner already has a live enrolment on this programme", HttpStatus.CONFLICT, "TP_ENROLMENT_STATE");
        }
        TrainProvLearnerApplication app = null;
        if (r.applicationId() != null) {
            app = loadApplication(t, r.applicationId());
            if (!app.getLearnerId().equals(r.learnerId()) || !app.getProgrammeId().equals(r.programmeId())) {
                throw new HandyFlowException("That application belongs to a different learner or programme", HttpStatus.BAD_REQUEST, "TP_ENROLMENT_INVALID");
            }
        }
        UUID clientId = r.clientId() != null ? r.clientId() : learner.getClientId();
        if (clientId != null) {
            clients.findActiveById(t, clientId).orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", r.clientId().toString()));
        }
        TrainProvLearnerEnrolment e = TrainProvLearnerEnrolment.create(t, numbers.nextLearnerEnrolmentNumber(t), r.learnerId(), p.getId(), version.getId(),
                version.getQualificationVersionId(), r.siteId(), r.applicationId(), clientId, r.startDate(), r.expectedEndDate(),
                p.getTrainingActivity(), outcome, today, r.overrideReason(), by);
        if (app != null) {
            TrainProvLearnerApplication toMark = app;
            guard(() -> { toMark.markEnrolled(); return null; });
            applications.save(toMark);
            applicationEvents.save(TrainProvLearnerApplicationEvent.of(t, toMark.getId(), EnrolmentRules.ACCEPTED, EnrolmentRules.ENROLLED, "Enrolled as " + e.getEnrolmentNo(), null, by));
        }
        enrolments.save(e);
        enrolmentEvents.save(TrainProvLearnerEnrolmentEvent.of(t, e.getId(), null, EnrolmentRules.ENROLLED, site.getName(), by));
        return enrolmentView(t, e);
    }

    @Transactional
    public EnrolmentView transitionEnrolment(TenantId t, UUID id, EnrolmentTransitionRequest r, UUID by) {
        TrainProvLearnerEnrolment e = loadEnrolment(t, id);
        String from = e.getStatus();
        guard(() -> { e.moveTo(r.toStatus(), r.reason()); return null; });
        enrolments.save(e);
        enrolmentEvents.save(TrainProvLearnerEnrolmentEvent.of(t, id, from, r.toStatus(), r.reason(), by));
        return enrolmentView(t, e);
    }

    // ---- views for the learner screen ------------------------------------------

    public List<ApplicationView> applicationsFor(TenantId t, UUID learnerId) {
        return applications(t, null, learnerId, null);
    }

    public List<EnrolmentView> enrolmentsFor(TenantId t, UUID learnerId) {
        return enrolments(t, null, learnerId, null);
    }

    // ---- helpers ---------------------------------------------------------------

    private ApplicationView applicationView(TenantId t, TrainProvLearnerApplication a) {
        String learnerName = learners.findForTenant(t, a.getLearnerId()).map(TrainProvLearner::fullName).orElse("");
        String programmeTitle = programmes.findForTenant(t, a.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse("");
        String siteName = a.getSiteId() == null ? null : sites.findForTenant(t, a.getSiteId()).map(TrainProvDeliverySite::getName).orElse(null);
        return new ApplicationView(a.getId(), a.getLearnerId(), learnerName, a.getProgrammeId(), programmeTitle, a.getSiteId(), siteName,
                a.getPreferredStart(), a.getSourceNote(), a.getStatus(), a.getStatusReason(), a.getEntryRequirementsMet(), a.getEntryCheckNote(),
                a.getEntryOverrideReason(), EnrolmentRules.applicationNext(a.getStatus()).stream().sorted().toList(), a.getCreatedAt());
    }

    private EnrolmentView enrolmentView(TenantId t, TrainProvLearnerEnrolment e) {
        String learnerName = learners.findForTenant(t, e.getLearnerId()).map(TrainProvLearner::fullName).orElse("");
        String programmeTitle = programmes.findForTenant(t, e.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse("");
        Integer versionNo = versions.findForTenant(t, e.getProgrammeVersionId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null);
        String siteName = sites.findForTenant(t, e.getSiteId()).map(TrainProvDeliverySite::getName).orElse("");
        return new EnrolmentView(e.getId(), e.getEnrolmentNo(), e.getLearnerId(), learnerName, e.getProgrammeId(), programmeTitle,
                e.getProgrammeVersionId(), versionNo, e.getSiteId(), siteName, e.getApplicationId(), e.getClientId(), e.getStartDate(),
                e.getExpectedEndDate(), e.getStatus(), e.getStatusReason(), e.getTrainingActivity(), e.getCoverOutcome(), e.getCoverCheckedOn(),
                e.getCoverOverrideReason(), EnrolmentRules.enrolmentNext(e.getStatus()).stream().sorted().toList(), e.getCreatedAt());
    }

    private TrainProvLearner learner(TenantId t, UUID id) {
        return learners.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", id.toString()));
    }

    private TrainProvProgramme programme(TenantId t, UUID id) {
        return programmes.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", id.toString()));
    }

    private TrainProvDeliverySite site(TenantId t, UUID id) {
        return sites.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", id.toString()));
    }

    private TrainProvLearnerApplication loadApplication(TenantId t, UUID id) {
        return applications.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerApplication", id.toString()));
    }

    private TrainProvLearnerEnrolment loadEnrolment(TenantId t, UUID id) {
        return enrolments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_ENROLMENT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_ENROLMENT_INVALID");
        }
    }
}
