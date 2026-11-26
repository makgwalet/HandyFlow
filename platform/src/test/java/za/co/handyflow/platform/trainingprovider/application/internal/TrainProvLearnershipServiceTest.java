package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnershipAgreement;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnershipAgreementRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.dto.LearnershipDtos.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvLearnershipServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);

    @Mock TrainProvLearnershipAgreementRepository agreements;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvClientRepository clients;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvLearnershipService service;

    private TrainProvLearnerEnrolment enrolment;
    private TrainProvLearnershipAgreement draft;

    @BeforeEach
    void setUp() {
        enrolment = mock(TrainProvLearnerEnrolment.class);
        lenient().when(enrolment.getId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(enrolment.getStartDate()).thenReturn(START);
        lenient().when(enrolment.getLearnerId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getProgrammeId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.of(enrolment));
        lenient().when(numbers.nextLearnershipNumber(any())).thenReturn("LSH-00001");
        draft = TrainProvLearnershipAgreement.create(TENANT, "LSH-00001", enrolment.getId(), null, "Acme Stores", null, START, null, USER);
        lenient().when(agreements.findForTenant(any(), any())).thenReturn(Optional.of(draft));
    }

    @Test
    void createsADraftWhenNoLiveAgreementExists() {
        when(agreements.liveExists(any(), any())).thenReturn(false);
        AgreementView v = service.create(TENANT, new AgreementRequest(enrolment.getId(), null, "Acme Stores", null, START, null), USER);
        assertThat(v.status()).isEqualTo("DRAFT");
        assertThat(v.agreementNo()).isEqualTo("LSH-00001");
        verify(agreements).save(any());
    }

    @Test
    void refusesASecondLiveAgreement() {
        when(agreements.liveExists(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, new AgreementRequest(enrolment.getId(), null, "Acme Stores", null, START, null), USER)).isInstanceOf(HandyFlowException.class);
        verify(agreements, never()).save(any());
    }

    @Test
    void signingNeedsAllThreeDaysAndLocksTheDetails() {
        assertThatThrownBy(() -> service.sign(TENANT, draft.getId(), new SignRequest(START, START, TODAY.plusDays(1)), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(service.sign(TENANT, draft.getId(), new SignRequest(START, START, START), USER, TODAY).status()).isEqualTo("SIGNED");
        assertThatThrownBy(() -> service.update(TENANT, draft.getId(), new AgreementUpdate(null, "Other Co", null, START, null))).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void registrationReferenceIsKeptAsEnteredAndNeverCalledRegistered() {
        service.sign(TENANT, draft.getId(), new SignRequest(START, START, START), USER, TODAY);
        AgreementView v = service.recordRegistration(TENANT, draft.getId(), new RegistrationRequest("REG/123", TODAY), USER, TODAY);
        assertThat(v.registrationRef()).isEqualTo("REG/123");
        assertThat(v.summary()).contains("has not been checked against any register").contains("does not confirm that the learnership is registered or approved");
        assertThatThrownBy(() -> service.recordRegistration(TENANT, draft.getId(), new RegistrationRequest("REG/999", TODAY), USER, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void cannotCancelOnceAReferenceIsRecordedButCanEndWithAReason() {
        service.sign(TENANT, draft.getId(), new SignRequest(START, START, START), USER, TODAY);
        service.recordRegistration(TENANT, draft.getId(), new RegistrationRequest("REG/123", TODAY), USER, TODAY);
        assertThatThrownBy(() -> service.cancel(TENANT, draft.getId(), "Signed against the wrong learner")).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.end(TENANT, draft.getId(), new EndRequest(TODAY, "short"), TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(service.end(TENANT, draft.getId(), new EndRequest(TODAY, "Learner completed the term"), TODAY).status()).isEqualTo("ENDED");
    }

    @Test
    void aDraftIsCancelledWithAReason() {
        assertThatThrownBy(() -> service.cancel(TENANT, draft.getId(), "short")).isInstanceOf(HandyFlowException.class);
        assertThat(service.cancel(TENANT, draft.getId(), "Employer pulled out").status()).isEqualTo("CANCELLED");
    }
}
