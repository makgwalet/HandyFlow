package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.PractitionerDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvPractitionerServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID MAKER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHECKER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock TrainProvPractitionerRepository practitioners;
    @Mock TrainProvPractitionerAppointmentRepository appointments;
    @Mock TrainProvPractitionerAppointmentEventRepository events;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvPractitionerService service;

    private TrainProvPractitioner person() {
        return TrainProvPractitioner.create(TENANT, "PRC-00001", "Test", "Person", null, null, "EMPLOYEE", null, null, MAKER);
    }

    private TrainProvProgramme programme() {
        return TrainProvProgramme.create(TENANT, "P1", "TEST programme", "NON_ACCREDITED_COURSE", "NON_ACCREDITED", "CLASSROOM", null, null, null, MAKER);
    }

    private TrainProvPractitionerAppointment appointment(String role) {
        return TrainProvPractitionerAppointment.create(TENANT, UUID.randomUUID(), role, UUID.randomUUID(), null, "REF-123", null, null, null, MAKER);
    }

    @Test
    void appointingRecordsTheCredentialButDoesNotVerifyIt() {
        TrainProvPractitioner p = person();
        TrainProvProgramme prog = programme();
        when(practitioners.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(programmes.findForTenant(TENANT, prog.getId())).thenReturn(Optional.of(prog));
        AppointmentView v = service.appoint(TENANT, p.getId(), new AppointmentRequest("ASSESSOR", prog.getId(), null, "REF-123", null, null, null), MAKER, TODAY);
        assertThat(v.credentialRef()).isEqualTo("REF-123");
        assertThat(v.verifiedOn()).isNull();
        assertThat(v.standing()).isEqualTo("NOT_VERIFIED");
        verify(events).save(any(TrainProvPractitionerAppointmentEvent.class));
    }

    @Test
    void anInactivePersonCannotBeAppointed() {
        TrainProvPractitioner p = person();
        p.deactivate("Left the company", MAKER);
        when(practitioners.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.appoint(TENANT, p.getId(), new AppointmentRequest("FACILITATOR", UUID.randomUUID(), null, null, null, null, null), MAKER, TODAY))
                .isInstanceOf(HandyFlowException.class);
        verify(appointments, never()).save(any());
    }

    @Test
    void theSameRoleOnTheSameProgrammeCannotBeHeldTwice() {
        TrainProvPractitioner p = person();
        TrainProvProgramme prog = programme();
        when(practitioners.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(programmes.findForTenant(TENANT, prog.getId())).thenReturn(Optional.of(prog));
        when(appointments.liveExists(TENANT, p.getId(), "ASSESSOR", prog.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.appoint(TENANT, p.getId(), new AppointmentRequest("ASSESSOR", prog.getId(), null, "REF-123", null, null, null), MAKER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already holds");
    }

    @Test
    void aSecondPersonCanVerifyAnAssessorsCredential() {
        TrainProvPractitionerAppointment a = appointment("ASSESSOR");
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        AppointmentView v = service.verify(TENANT, a.getId(), new VerifyRequest("Authority register entry", TODAY, null), CHECKER, TODAY);
        assertThat(v.standing()).isEqualTo("VERIFIED_IN_FORCE");
        assertThat(v.verificationSource()).isEqualTo("Authority register entry");
    }

    @Test
    void theSamePersonNeedsAWrittenReasonToVerifyAnAssessorsCredential() {
        TrainProvPractitionerAppointment a = appointment("ASSESSOR");
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.verify(TENANT, a.getId(), new VerifyRequest("Authority register entry", TODAY, null), MAKER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("second person");
        assertThat(a.getVerifiedOn()).isNull();
        service.verify(TENANT, a.getId(), new VerifyRequest("Authority register entry", TODAY, "Only person with access to the register"), MAKER, TODAY);
        assertThat(a.getVerifiedOn()).isEqualTo(TODAY);
    }

    @Test
    void aFacilitatorCanBeCheckedByTheSamePerson() {
        TrainProvPractitionerAppointment a = appointment("FACILITATOR");
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        service.verify(TENANT, a.getId(), new VerifyRequest("CV and certificate sighted", TODAY, null), MAKER, TODAY);
        assertThat(a.getVerifiedOn()).isEqualTo(TODAY);
    }

    @Test
    void changingTheCredentialReferenceClearsTheCheck() {
        TrainProvPractitionerAppointment a = appointment("ASSESSOR");
        a.verify("Authority register entry", TODAY, CHECKER, TODAY);
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        AppointmentView v = service.change(TENANT, a.getId(), new AppointmentChange(null, "REF-999", null, null, null), MAKER, TODAY);
        assertThat(v.verifiedOn()).isNull();
        assertThat(v.standing()).isEqualTo("NOT_VERIFIED");
    }

    @Test
    void changingOnlyTheDatesKeepsTheCheck() {
        TrainProvPractitionerAppointment a = appointment("ASSESSOR");
        a.verify("Authority register entry", TODAY, CHECKER, TODAY);
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        service.change(TENANT, a.getId(), new AppointmentChange(null, "REF-123", null, null, TODAY.plusYears(1)), MAKER, TODAY);
        assertThat(a.getVerifiedOn()).isEqualTo(TODAY);
    }

    @Test
    void withdrawingNeedsAReasonAndAWithdrawnAppointmentCannotBeVerified() {
        TrainProvPractitionerAppointment a = appointment("FACILITATOR");
        when(appointments.findForTenant(TENANT, a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.withdraw(TENANT, a.getId(), "short", MAKER, TODAY)).isInstanceOf(HandyFlowException.class);
        service.withdraw(TENANT, a.getId(), "No longer teaches this programme", MAKER, TODAY);
        assertThat(a.getStatus()).isEqualTo("WITHDRAWN");
        assertThatThrownBy(() -> service.verify(TENANT, a.getId(), new VerifyRequest("CV and certificate sighted", TODAY, null), CHECKER, TODAY))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void onlyCheckedInForceAppointmentsOfActivePeopleAreEligible() {
        TrainProvPractitioner ok = person();
        TrainProvPractitioner inactive = TrainProvPractitioner.create(TENANT, "PRC-00002", "Gone", "Person", null, null, "EMPLOYEE", null, null, MAKER);
        inactive.deactivate("Left the company", MAKER);
        UUID prog = UUID.randomUUID();
        TrainProvPractitionerAppointment good = TrainProvPractitionerAppointment.create(TENANT, ok.getId(), "ASSESSOR", prog, null, "REF-1", null, null, null, MAKER);
        good.verify("Authority register entry", TODAY, CHECKER, TODAY);
        TrainProvPractitionerAppointment unchecked = TrainProvPractitionerAppointment.create(TENANT, UUID.randomUUID(), "ASSESSOR", prog, null, "REF-2", null, null, null, MAKER);
        TrainProvPractitionerAppointment expired = TrainProvPractitionerAppointment.create(TENANT, UUID.randomUUID(), "ASSESSOR", prog, null, "REF-3", null, null, TODAY.minusDays(1), MAKER);
        expired.verify("Authority register entry", TODAY.minusDays(40), CHECKER, TODAY);
        TrainProvPractitionerAppointment ofInactive = TrainProvPractitionerAppointment.create(TENANT, inactive.getId(), "ASSESSOR", prog, null, "REF-4", null, null, null, MAKER);
        ofInactive.verify("Authority register entry", TODAY, CHECKER, TODAY);
        when(appointments.findLiveForProgramme(TENANT, prog, "ASSESSOR")).thenReturn(List.of(good, unchecked, expired, ofInactive));
        when(practitioners.findForTenant(TENANT, ok.getId())).thenReturn(Optional.of(ok));
        when(practitioners.findForTenant(TENANT, inactive.getId())).thenReturn(Optional.of(inactive));
        List<EligibleView> found = service.eligible(TENANT, prog, "ASSESSOR", TODAY);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).practitionerId()).isEqualTo(ok.getId());
    }
}
