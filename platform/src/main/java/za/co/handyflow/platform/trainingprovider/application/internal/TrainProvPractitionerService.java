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
import za.co.handyflow.platform.trainingprovider.domain.rules.PractitionerRules;
import za.co.handyflow.platform.trainingprovider.dto.PractitionerDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Practitioners and what each is appointed to do. An assessor's or moderator's credential counts only once someone
 * has recorded where it was checked, and the person who made the appointment needs a second person to check it,
 * or a written reason. Nothing is deleted; people are deactivated and appointments withdrawn with a reason.
 * Whether someone is eligible for a role on a programme is answered from verified, in-force appointments only.
 */
@Service
@RequiredArgsConstructor
public class TrainProvPractitionerService {

    private final TrainProvPractitionerRepository practitioners;
    private final TrainProvPractitionerAppointmentRepository appointments;
    private final TrainProvPractitionerAppointmentEventRepository events;
    private final TrainProvProgrammeRepository programmes;
    private final TrainProvAuthorityRepository authorities;
    private final TrainProvNumberGenerator numbers;

    public List<PractitionerView> list(TenantId t, String status, String search, LocalDate today) {
        String q = search == null || search.isBlank() ? null : search.trim();
        return practitioners.search(t, status, q).stream().<PractitionerView>map(p -> view(t, p, today)).toList();
    }

    public PractitionerDetail detail(TenantId t, UUID id, LocalDate today) {
        TrainProvPractitioner p = practitioner(t, id);
        List<AppointmentView> as = appointments.findForPractitioner(t, id).stream().<AppointmentView>map(a -> appointmentView(t, a, today, true)).toList();
        return new PractitionerDetail(view(t, p, today), as);
    }

    @Transactional
    public PractitionerView create(TenantId t, PractitionerRequest r, UUID by, LocalDate today) {
        TrainProvPractitioner p = guard(() -> TrainProvPractitioner.create(t, numbers.nextPractitionerNumber(t), r.firstNames(), r.surname(),
                r.email(), r.phone(), r.employmentType(), r.userId(), r.notes(), by));
        practitioners.save(p);
        return view(t, p, today);
    }

    @Transactional
    public PractitionerView update(TenantId t, UUID id, PractitionerRequest r, LocalDate today) {
        TrainProvPractitioner p = practitioner(t, id);
        guard(() -> { p.update(r.firstNames(), r.surname(), r.email(), r.phone(), r.employmentType(), r.userId(), r.notes()); return null; });
        practitioners.save(p);
        return view(t, p, today);
    }

    @Transactional
    public PractitionerView deactivate(TenantId t, UUID id, String reason, UUID by, LocalDate today) {
        TrainProvPractitioner p = practitioner(t, id);
        guard(() -> { p.deactivate(reason, by); return null; });
        practitioners.save(p);
        return view(t, p, today);
    }

    @Transactional
    public PractitionerView reactivate(TenantId t, UUID id, LocalDate today) {
        TrainProvPractitioner p = practitioner(t, id);
        guard(() -> { p.reactivate(); return null; });
        practitioners.save(p);
        return view(t, p, today);
    }

    // ---- appointments ----------------------------------------------------------

    @Transactional
    public AppointmentView appoint(TenantId t, UUID practitionerId, AppointmentRequest r, UUID by, LocalDate today) {
        TrainProvPractitioner p = practitioner(t, practitionerId);
        if (!"ACTIVE".equals(p.getStatus())) {
            throw new HandyFlowException("This person is inactive", HttpStatus.CONFLICT, "TP_PRACTITIONER_STATE");
        }
        TrainProvProgramme prog = programme(t, r.programmeId());
        if ("RETIRED".equals(prog.getStatus())) {
            throw new HandyFlowException("A retired programme cannot take new appointments", HttpStatus.CONFLICT, "TP_PRACTITIONER_STATE");
        }
        if (r.authorityId() != null) {
            authorities.findForTenant(t, r.authorityId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuthority", r.authorityId().toString()));
        }
        if (appointments.liveExists(t, practitionerId, r.role(), r.programmeId())) {
            throw new HandyFlowException("This person already holds that role on that programme", HttpStatus.CONFLICT, "TP_PRACTITIONER_STATE");
        }
        TrainProvPractitionerAppointment a = guard(() -> TrainProvPractitionerAppointment.create(t, practitionerId, r.role(), r.programmeId(),
                r.authorityId(), r.credentialRef(), r.credentialNote(), r.validFrom(), r.validTo(), by));
        appointments.save(a);
        events.save(TrainProvPractitionerAppointmentEvent.of(t, a.getId(), "CREATED", null, by));
        return appointmentView(t, a, today, true);
    }

    @Transactional
    public AppointmentView change(TenantId t, UUID appointmentId, AppointmentChange r, UUID by, LocalDate today) {
        TrainProvPractitionerAppointment a = appointment(t, appointmentId);
        if (r.authorityId() != null) {
            authorities.findForTenant(t, r.authorityId()).orElseThrow(() -> new ResourceNotFoundException("TrainProvAuthority", r.authorityId().toString()));
        }
        boolean wasVerified = a.getVerifiedOn() != null;
        guard(() -> { a.changeCredential(r.credentialRef(), r.credentialNote(), r.authorityId(), r.validFrom(), r.validTo()); return null; });
        appointments.save(a);
        boolean cleared = wasVerified && a.getVerifiedOn() == null;
        events.save(TrainProvPractitionerAppointmentEvent.of(t, appointmentId, "CHANGED", cleared ? "Credential reference changed, so the earlier check no longer applies" : null, by));
        return appointmentView(t, a, today, true);
    }

    @Transactional
    public AppointmentView verify(TenantId t, UUID appointmentId, VerifyRequest r, UUID by, LocalDate today) {
        TrainProvPractitionerAppointment a = appointment(t, appointmentId);
        boolean sameSetter = PractitionerRules.needsCredential(a.getRole()) && by.equals(a.getCreatedBy());
        if (sameSetter && !PractitionerRules.longEnough(r.overrideReason(), PractitionerRules.MIN_REASON)) {
            throw new HandyFlowException("A second person should check an assessor's or moderator's credential. If no one else can, say why in at least "
                    + PractitionerRules.MIN_REASON + " characters", HttpStatus.CONFLICT, "TP_VERIFY_SECOND_PERSON");
        }
        guard(() -> { a.verify(r.verificationSource(), r.verifiedOn(), by, today); return null; });
        appointments.save(a);
        String note = r.verificationSource().trim() + (sameSetter ? " (checked without a second person: " + r.overrideReason().trim() + ")" : "");
        events.save(TrainProvPractitionerAppointmentEvent.of(t, appointmentId, "VERIFIED", note, by));
        return appointmentView(t, a, today, true);
    }

    @Transactional
    public AppointmentView withdraw(TenantId t, UUID appointmentId, String reason, UUID by, LocalDate today) {
        TrainProvPractitionerAppointment a = appointment(t, appointmentId);
        guard(() -> { a.withdraw(reason, by); return null; });
        appointments.save(a);
        events.save(TrainProvPractitionerAppointmentEvent.of(t, appointmentId, "WITHDRAWN", reason, by));
        return appointmentView(t, a, today, true);
    }

    /** Active people with a checked, in-force appointment for the role on the programme on the day. */
    public List<EligibleView> eligible(TenantId t, UUID programmeId, String role, LocalDate on) {
        return appointments.findLiveForProgramme(t, programmeId, role).stream()
                .filter(a -> a.standingOn(on) == PractitionerRules.Standing.VERIFIED_IN_FORCE)
                .<EligibleView>map(a -> {
                    TrainProvPractitioner p = practitioner(t, a.getPractitionerId());
                    return "ACTIVE".equals(p.getStatus())
                            ? new EligibleView(p.getId(), p.getPractitionerNo(), p.fullName(), a.getRole(), a.getId(), a.getVerifiedOn(), p.getUserId() != null) : null;
                })
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(EligibleView::name))
                .toList();
    }

    // ---- views and helpers -----------------------------------------------------

    private PractitionerView view(TenantId t, TrainProvPractitioner p, LocalDate today) {
        List<TrainProvPractitionerAppointment> mine = appointments.findForPractitioner(t, p.getId());
        List<String> verifiedRoles = mine.stream()
                .filter(a -> a.standingOn(today) == PractitionerRules.Standing.VERIFIED_IN_FORCE)
                .map(TrainProvPractitionerAppointment::getRole).distinct().sorted().toList();
        long live = mine.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
        return new PractitionerView(p.getId(), p.getPractitionerNo(), p.getFirstNames(), p.getSurname(), p.getEmail(), p.getPhone(),
                p.getEmploymentType(), p.getUserId(), p.getNotes(), p.getStatus(), p.getInactiveReason(), live, verifiedRoles);
    }

    private AppointmentView appointmentView(TenantId t, TrainProvPractitionerAppointment a, LocalDate today, boolean withHistory) {
        String title = programmes.findForTenant(t, a.getProgrammeId()).map(TrainProvProgramme::getTitle).orElse("");
        String authority = a.getAuthorityId() == null ? null : authorities.findForTenant(t, a.getAuthorityId()).map(TrainProvAuthority::getName).orElse(null);
        PractitionerRules.Standing s = a.standingOn(today);
        List<AppointmentEvent> history = withHistory
                ? events.findForAppointment(t, a.getId()).stream().<AppointmentEvent>map(e -> new AppointmentEvent(e.getId(), e.getAction(), e.getNote(), e.getActedBy(), e.getActedAt())).toList()
                : List.of();
        return new AppointmentView(a.getId(), a.getPractitionerId(), a.getRole(), a.getProgrammeId(), title, a.getAuthorityId(), authority, a.getCredentialRef(),
                a.getCredentialNote(), a.getValidFrom(), a.getValidTo(), a.getVerificationSource(), a.getVerifiedOn(), a.getStatus(), a.getWithdrawnReason(),
                s.name(), s.note(), history);
    }

    private TrainProvPractitioner practitioner(TenantId t, UUID id) {
        return practitioners.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitioner", id.toString()));
    }

    private TrainProvPractitionerAppointment appointment(TenantId t, UUID id) {
        return appointments.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvPractitionerAppointment", id.toString()));
    }

    private TrainProvProgramme programme(TenantId t, UUID id) {
        return programmes.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvProgramme", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_PRACTITIONER_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_PRACTITIONER_INVALID");
        }
    }
}
