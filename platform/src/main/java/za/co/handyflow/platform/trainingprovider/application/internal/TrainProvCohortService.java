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
import za.co.handyflow.platform.trainingprovider.domain.rules.CatalogueRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;
import za.co.handyflow.platform.trainingprovider.dto.CohortDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Venues, cohorts, cohort members and cohort facilitators. A cohort is frozen to the approved programme version and the site it was
 * opened on. It can only be opened, re-dated or started where the programme is offered at the site and, for an accredited programme, an
 * approval in force covers that site on its first and last day. Pending or lapsed evidence is not cover. A facilitator can only be assigned when their appointment for the programme is checked and in force on the day.
 * Nothing is deleted: people are removed and cohorts cancelled, each with a reason, and every change is logged.
 */
@Service
@RequiredArgsConstructor
public class TrainProvCohortService {

    private final TrainProvVenueRepository venues;
    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvCohortFacilitatorRepository facilitators;
    private final TrainProvCohortEventRepository events;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvProgrammeVersionRepository versions;
    private final TrainProvDeliverySiteRepository sites;
    private final TrainProvProgrammeOfferingRepository offerings;
    private final TrainProvAccreditationService accreditation;
    private final TrainProvLearnerEnrolmentRepository enrolments;
    private final TrainProvLearnerRepository learners;
    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvPractitionerAppointmentRepository appointments;
    private final TrainProvNumberGenerator numbers;

    // ---- venues ----

    public List<VenueView> venues(TenantId t, UUID siteId, String status) {
        return venues.search(t, siteId, blank(status)).stream().map(v -> venueView(t, v)).toList();
    }

    @Transactional
    public VenueView createVenue(TenantId t, VenueRequest r, UUID by) {
        TrainProvDeliverySite site = site(t, r.siteId());
        if (venues.nameTaken(t, site.getId(), r.name() == null ? "" : r.name().trim())) {
            throw new HandyFlowException("That site already has a venue with this name", HttpStatus.CONFLICT, "TP_COHORT_STATE");
        }
        TrainProvVenue v = guard(() -> TrainProvVenue.create(t, site.getId(), r.name(), r.capacity(), r.notes(), by));
        venues.save(v);
        return venueView(t, v);
    }

    @Transactional
    public VenueView updateVenue(TenantId t, UUID id, VenueRequest r) {
        TrainProvVenue v = venue(t, id);
        guard(() -> { v.update(r.name(), r.capacity(), r.notes()); return null; });
        venues.save(v);
        return venueView(t, v);
    }

    @Transactional
    public VenueView deactivateVenue(TenantId t, UUID id, String reason) {
        TrainProvVenue v = venue(t, id);
        guard(() -> { v.deactivate(reason); return null; });
        venues.save(v);
        return venueView(t, v);
    }

    @Transactional
    public VenueView reactivateVenue(TenantId t, UUID id) {
        TrainProvVenue v = venue(t, id);
        guard(() -> { v.reactivate(); return null; });
        venues.save(v);
        return venueView(t, v);
    }

    // ---- cohorts ----

    public List<CohortView> list(TenantId t, String status, UUID programmeId, UUID siteId) {
        return cohorts.search(t, blank(status), programmeId, siteId).stream().map(c -> cohortView(t, c)).toList();
    }

    public CohortDetail detail(TenantId t, UUID id, LocalDate today) {
        TrainProvCohort c = cohort(t, id);
        List<MemberView> ms = members.findForCohort(t, id).stream().map(m -> memberView(t, m)).toList();
        List<FacilitatorView> fs = facilitators.findForCohort(t, id).stream().map(f -> facilitatorView(t, f, today)).toList();
        List<EventView> history = events.findForCohort(t, id).stream()
                .map(e -> new EventView(e.getId(), e.getAction(), e.getNote(), e.getActedBy(), e.getActedAt())).toList();
        return new CohortDetail(cohortView(t, c), ms, fs, history);
    }

    @Transactional
    public CohortView create(TenantId t, CohortRequest r, UUID by) {
        TrainProvProgramme p = programme(t, r.programmeId());
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new HandyFlowException("Cohorts can only be opened on an active programme", HttpStatus.CONFLICT, "TP_COHORT_STATE");
        }
        TrainProvProgrammeVersion version = versions.findApproved(t, p.getId())
                .orElseThrow(() -> new HandyFlowException("The programme has no approved version yet", HttpStatus.CONFLICT, "TP_COHORT_STATE"));
        TrainProvDeliverySite site = site(t, r.siteId());
        if (!"ACTIVE".equals(site.getStatus())) {
            throw new HandyFlowException("That site is not active", HttpStatus.CONFLICT, "TP_COHORT_STATE");
        }
        String caveat = requireDeliverable(t, p, site, r.startDate(), r.endDate());
        TrainProvCohort c = guard(() -> TrainProvCohort.create(t, numbers.nextCohortNumber(t), r.name(), p.getId(), version.getId(), site.getId(),
                r.startDate(), r.endDate(), r.capacity(), r.notes(), by));
        cohorts.save(c);
        events.save(TrainProvCohortEvent.of(t, c.getId(), "CREATED", "Opened on version " + version.getVersionNo() + (caveat == null ? "" : ". Note: " + caveat), by));
        return cohortView(t, c);
    }

    @Transactional
    public CohortView update(TenantId t, UUID id, CohortUpdate r, UUID by) {
        TrainProvCohort c = cohort(t, id);
        String caveat = null;
        if (CohortRules.open(c.getStatus()) && (!Objects.equals(c.getStartDate(), r.startDate()) || !Objects.equals(c.getEndDate(), r.endDate()))) {
            caveat = requireDeliverable(t, programme(t, c.getProgrammeId()), site(t, c.getSiteId()), r.startDate(), r.endDate());
        }
        guard(() -> { c.update(r.name(), r.startDate(), r.endDate(), r.capacity(), r.notes(), members.countActive(t, id)); return null; });
        cohorts.save(c);
        events.save(TrainProvCohortEvent.of(t, id, "CHANGED", caveat == null ? null : "Note: " + caveat, by));
        return cohortView(t, c);
    }

    @Transactional
    public CohortView transition(TenantId t, UUID id, CohortTransition r, UUID by) {
        TrainProvCohort c = cohort(t, id);
        String from = c.getStatus();
        if (CohortRules.PLANNED.equals(from) && CohortRules.RUNNING.equals(r.toStatus())) {
            requireDeliverable(t, programme(t, c.getProgrammeId()), site(t, c.getSiteId()), c.getStartDate(), c.getEndDate());
        }
        guard(() -> { c.moveTo(r.toStatus(), r.reason()); return null; });
        cohorts.save(c);
        events.save(TrainProvCohortEvent.of(t, id, "STATUS", from + " to " + c.getStatus() + (r.reason() == null || r.reason().isBlank() ? "" : ": " + r.reason().trim()), by));
        return cohortView(t, c);
    }

    // ---- members ----

    @Transactional
    public CohortDetail addMember(TenantId t, UUID cohortId, AddMember r, UUID by, LocalDate today) {
        TrainProvCohort c = cohort(t, cohortId);
        TrainProvLearnerEnrolment e = enrolments.findForTenant(t, r.enrolmentId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerEnrolment", r.enrolmentId().toString()));
        String problem = CohortRules.memberProblem(c.getStatus(), c.getProgrammeVersionId(), c.getSiteId(), e.getStatus(), e.getProgrammeVersionId(),
                e.getSiteId(), members.liveForEnrolment(t, e.getId()), members.countActive(t, cohortId), c.getCapacity());
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_COHORT_STATE");
        }
        members.save(TrainProvCohortMember.create(t, cohortId, e.getId(), by));
        events.save(TrainProvCohortEvent.of(t, cohortId, "LEARNER_ADDED", e.getEnrolmentNo(), by));
        return detail(t, cohortId, today);
    }

    @Transactional
    public CohortDetail removeMember(TenantId t, UUID memberId, String reason, UUID by, LocalDate today) {
        TrainProvCohortMember m = members.findForTenant(t, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvCohortMember", memberId.toString()));
        guard(() -> { m.remove(reason, by); return null; });
        members.save(m);
        String no = enrolments.findForTenant(t, m.getEnrolmentId()).map(TrainProvLearnerEnrolment::getEnrolmentNo).orElse("");
        events.save(TrainProvCohortEvent.of(t, m.getCohortId(), "LEARNER_REMOVED", no + ": " + reason.trim(), by));
        return detail(t, m.getCohortId(), today);
    }

    // ---- facilitators ----

    @Transactional
    public CohortDetail assignFacilitator(TenantId t, UUID cohortId, AssignFacilitator r, UUID by, LocalDate today) {
        TrainProvCohort c = cohort(t, cohortId);
        TrainProvPractitioner p = practitioners.findForTenant(t, r.practitionerId())
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitioner", r.practitionerId().toString()));
        Optional<TrainProvPractitionerAppointment> appt = appointments.findLiveForProgramme(t, c.getProgrammeId(), "FACILITATOR").stream()
                .filter(a -> a.getPractitionerId().equals(p.getId())).findFirst();
        PractitionerRules.Standing standing = appt.map(a -> a.standingOn(today)).orElse(PractitionerRules.Standing.NOT_VERIFIED);
        String problem = CohortRules.facilitatorProblem(c.getStatus(), "ACTIVE".equals(p.getStatus()), standing, facilitators.liveExists(t, cohortId, p.getId()));
        if (problem != null) {
            throw new HandyFlowException(appt.isEmpty() && "ACTIVE".equals(p.getStatus()) && CohortRules.open(c.getStatus())
                    ? "This person has no facilitator appointment for the programme" : problem, HttpStatus.CONFLICT, "TP_COHORT_STATE");
        }
        facilitators.save(TrainProvCohortFacilitator.create(t, cohortId, p.getId(), appt.get().getId(), by));
        events.save(TrainProvCohortEvent.of(t, cohortId, "FACILITATOR_ASSIGNED", p.fullName(), by));
        return detail(t, cohortId, today);
    }

    @Transactional
    public CohortDetail removeFacilitator(TenantId t, UUID assignmentId, String reason, UUID by, LocalDate today) {
        TrainProvCohortFacilitator f = facilitators.findForTenant(t, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvCohortFacilitator", assignmentId.toString()));
        guard(() -> { f.remove(reason, by); return null; });
        facilitators.save(f);
        String name = practitioners.findForTenant(t, f.getPractitionerId()).map(TrainProvPractitioner::fullName).orElse("");
        events.save(TrainProvCohortEvent.of(t, f.getCohortId(), "FACILITATOR_REMOVED", name + ": " + reason.trim(), by));
        return detail(t, f.getCohortId(), today);
    }

    // ---- delivery check ----

    /**
     * Refuses unless the programme can be delivered at the site for the whole run. Returns a caveat when delivery rests on a conditional
     * or renewal-pending approval, otherwise null.
     */
    private String requireDeliverable(TenantId t, TrainProvProgramme p, TrainProvDeliverySite site, LocalDate start, LocalDate end) {
        if (start == null) {
            return null; // the cohort rules ask for the start date first
        }
        Optional<TrainProvProgrammeOffering> offering = offerings.findLive(t, p.getId(), site.getId());
        boolean accredited = CatalogueRules.ACCREDITED.equals(p.getTrainingActivity());
        CohortRules.Cover atStart = accredited && start != null ? cover(t, p, site.getId(), start) : null;
        CohortRules.Cover atEnd = accredited && end != null ? cover(t, p, site.getId(), end) : null;
        CohortRules.DeliveryFacts facts = new CohortRules.DeliveryFacts("ACTIVE".equals(p.getStatus()), "ACTIVE".equals(site.getStatus()),
                offering.isPresent(), offering.map(TrainProvProgrammeOffering::getOfferedFrom).orElse(null), accredited, atStart, atEnd);
        String problem = CohortRules.deliveryProblem(facts, start, end);
        if (problem != null) {
            throw new HandyFlowException(problem, HttpStatus.CONFLICT, "TP_COHORT_SITE");
        }
        return CohortRules.deliveryCaveat(facts);
    }

    private CohortRules.Cover cover(TenantId t, TrainProvProgramme p, UUID siteId, LocalDate day) {
        var c = accreditation.programmeCoverage(t, p.getId(), p.getCourseId(), siteId, day);
        return new CohortRules.Cover(c.covered(), c.outcome(), c.message());
    }

    // ---- views and lookups ----

    private VenueView venueView(TenantId t, TrainProvVenue v) {
        String siteName = sites.findForTenant(t, v.getSiteId()).map(TrainProvDeliverySite::getName).orElse("");
        return new VenueView(v.getId(), v.getSiteId(), siteName, v.getName(), v.getCapacity(), v.getNotes(), v.getStatus(), v.getInactiveReason());
    }

    private CohortView cohortView(TenantId t, TrainProvCohort c) {
        String title = programmes.findForTenant(t, c.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse("");
        Integer versionNo = versions.findForTenant(t, c.getProgrammeVersionId()).map(TrainProvProgrammeVersion::getVersionNo).orElse(null);
        String siteName = sites.findForTenant(t, c.getSiteId()).map(TrainProvDeliverySite::getName).orElse("");
        return new CohortView(c.getId(), c.getCohortNo(), c.getName(), c.getProgrammeId(), title, versionNo, c.getSiteId(), siteName, c.getStartDate(),
                c.getEndDate(), c.getCapacity(), c.getNotes(), c.getStatus(), c.getStatusReason(), members.countActive(t, c.getId()),
                facilitators.countActive(t, c.getId()), CohortRules.nextStatuses(c.getStatus()).stream().sorted().toList());
    }

    private MemberView memberView(TenantId t, TrainProvCohortMember m) {
        TrainProvLearnerEnrolment e = enrolments.findForTenant(t, m.getEnrolmentId()).orElse(null);
        String name = e == null ? "" : learners.findForTenant(t, e.getLearnerId()).map(l -> l.getFirstNames() + " " + l.getSurname()).orElse("");
        return new MemberView(m.getId(), m.getEnrolmentId(), e == null ? "" : e.getEnrolmentNo(), e == null ? null : e.getLearnerId(), name,
                e == null ? "" : e.getStatus(), m.getStatus(), m.getRemovedReason(), m.getAddedAt());
    }

    private FacilitatorView facilitatorView(TenantId t, TrainProvCohortFacilitator f, LocalDate today) {
        TrainProvPractitioner p = practitioners.findForTenant(t, f.getPractitionerId()).orElse(null);
        PractitionerRules.Standing s = appointments.findForTenant(t, f.getAppointmentId()).map(a -> a.standingOn(today)).orElse(PractitionerRules.Standing.NOT_VERIFIED);
        return new FacilitatorView(f.getId(), f.getPractitionerId(), p == null ? "" : p.getPractitionerNo(), p == null ? "" : p.fullName(), f.getAppointmentId(),
                s.name(), s.note(), f.getStatus(), f.getRemovedReason());
    }

    private TrainProvVenue venue(TenantId t, UUID id) {
        return venues.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvVenue", id.toString()));
    }

    private TrainProvCohort cohort(TenantId t, UUID id) {
        return cohorts.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", id.toString()));
    }

    private TrainProvProgramme programme(TenantId t, UUID id) {
        return programmes.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", id.toString()));
    }

    private TrainProvDeliverySite site(TenantId t, UUID id) {
        return sites.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvDeliverySite", id.toString()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_COHORT_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_COHORT_INVALID");
        }
    }
}
