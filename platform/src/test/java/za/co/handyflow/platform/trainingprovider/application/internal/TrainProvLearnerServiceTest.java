package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.LearnerDtos.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvLearnerServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvClientRepository clients;
    @Mock TrainProvDelegateRepository delegates;
    @Mock TrainProvEnrolmentService enrolmentService;
    @Mock TrainProvNumberGenerator numbers;
    @Mock TrainProvLearnerEventRepository events;
    @InjectMocks TrainProvLearnerService service;

    private LearnerRequest request(String idType, String idNumber) {
        return new LearnerRequest("Test", "Learner", idType, idNumber, null, null, null, null, null, null, null, false, null, null);
    }

    private TrainProvLearner existing() {
        return TrainProvLearner.create(TENANT, "LRN-00001", "Test", "Learner", "PASSPORT", "A1234567", null, null, null, null, null, null, null,
                false, null, null, USER, TODAY);
    }

    @Test
    void createsALearnerWithAGeneratedNumber() {
        when(numbers.nextLearnerNumber(TENANT)).thenReturn("LRN-00001");
        LearnerView v = service.create(TENANT, request("PASSPORT", "A1234567"), USER, TODAY);
        assertThat(v.learnerNo()).isEqualTo("LRN-00001");
        assertThat(v.status()).isEqualTo("ACTIVE");
        verify(learners).save(any(TrainProvLearner.class));
    }

    @Test
    void refusesASecondLiveLearnerWithTheSameIdentityDocument() {
        when(learners.identityTaken(TENANT, "PASSPORT", "A1234567", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, request("PASSPORT", "A1234567"), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("identity document");
        verify(learners, never()).save(any());
    }

    @Test
    void anInvalidSaIdIsRefusedAs400() {
        when(numbers.nextLearnerNumber(TENANT)).thenReturn("LRN-00002");
        assertThatThrownBy(() -> service.create(TENANT, request("SA_ID", "1234567890123"), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(learners, never()).save(any());
    }

    @Test
    void theListHidesTheFullIdentityNumberButTheDetailShowsIt() {
        TrainProvLearner l = existing();
        when(learners.search(TENANT, null, null, null)).thenReturn(java.util.List.of(l));
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        LearnerView listed = service.list(TENANT, null, null, " ").get(0);
        assertThat(listed.idNumber()).isNull();
        assertThat(listed.idNumberMasked()).isEqualTo("****4567");
        assertThat(service.detail(TENANT, l.getId()).learner().idNumber()).isEqualTo("A1234567");
    }

    @Test
    void cannotArchiveALearnerWithALiveEnrolment() {
        TrainProvLearner l = existing();
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        when(enrolments.countLiveForLearner(TENANT, l.getId())).thenReturn(1L);
        assertThatThrownBy(() -> service.archive(TENANT, l.getId(), "Left the area for good", USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("live enrolment");
        assertThat(l.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void archivingNeedsAReasonAndKeepsTheRecord() {
        TrainProvLearner l = existing();
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.archive(TENANT, l.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        service.archive(TENANT, l.getId(), "Left the area for good", USER);
        assertThat(l.getStatus()).isEqualTo("ARCHIVED");
        assertThat(l.getArchivedReason()).isEqualTo("Left the area for good");
        verify(learners, never()).delete(any());
    }

    @Test
    void cannotRestoreIntoSomeoneElsesIdentityDocument() {
        TrainProvLearner l = existing();
        l.archive("Left the area for good", USER);
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        when(learners.identityTaken(TENANT, "PASSPORT", "A1234567", l.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.restore(TENANT, l.getId())).isInstanceOf(HandyFlowException.class);
        assertThat(l.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void anArchivedLearnerCannotBeEdited() {
        TrainProvLearner l = existing();
        l.archive("Left the area for good", USER);
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.update(TENANT, l.getId(), request("PASSPORT", "A1234567"), TODAY, USER)).isInstanceOf(HandyFlowException.class);
    }

    private static final UUID DELEGATE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private LearnerRequest withDelegate(UUID delegateId, String idNumber, String linkReason) {
        return new LearnerRequest("Test", "Learner", idNumber == null ? "NONE" : "PASSPORT", idNumber, null, null, null, null, null, null, delegateId,
                false, null, null, linkReason);
    }

    private za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate delegate(String idNumber) {
        za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate d = mock(za.co.handyflow.platform.trainingprovider.domain.model.TrainProvDelegate.class);
        lenient().when(d.getIdNumber()).thenReturn(idNumber);
        return d;
    }

    @Test
    void linkingADelegateIsRecordedInTheHistory() {
        when(numbers.nextLearnerNumber(TENANT)).thenReturn("LRN-00001");
        when(delegates.findActiveById(TENANT, DELEGATE)).thenReturn(Optional.of(delegate(null)));
        when(learners.liveForDelegate(TENANT, DELEGATE)).thenReturn(java.util.List.of());
        service.create(TENANT, withDelegate(DELEGATE, null, null), USER, TODAY);
        verify(events).save(any(za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEvent.class));
    }

    @Test
    void aDelegateCannotBeLinkedToTwoLiveLearners() {
        when(delegates.findActiveById(TENANT, DELEGATE)).thenReturn(Optional.of(delegate(null)));
        when(learners.liveForDelegate(TENANT, DELEGATE)).thenReturn(java.util.List.of(existing()));
        assertThatThrownBy(() -> service.create(TENANT, withDelegate(DELEGATE, null, null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already linked");
        verify(learners, never()).save(any());
    }

    @Test
    void aDelegateWithADifferentIdentityNumberNeedsAWrittenReason() {
        when(delegates.findActiveById(TENANT, DELEGATE)).thenReturn(Optional.of(delegate("A9999999")));
        when(learners.liveForDelegate(TENANT, DELEGATE)).thenReturn(java.util.List.of());
        assertThatThrownBy(() -> service.create(TENANT, withDelegate(DELEGATE, "A1234567", null), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("differ");
    }

    @Test
    void takingTheLinkOffNeedsAReason() {
        TrainProvLearner l = existing();
        l.relink(DELEGATE);
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.update(TENANT, l.getId(), withDelegate(null, "A1234567", "short"), TODAY, USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Say why");
        verify(learners, never()).save(any());
    }

    @Test
    void takingTheLinkOffIsRecordedWithItsReason() {
        TrainProvLearner l = existing();
        l.relink(DELEGATE);
        when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        service.update(TENANT, l.getId(), withDelegate(null, "A1234567", "Linked to the wrong delegate record"), TODAY, USER);
        verify(events).save(any(za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEvent.class));
        assertThat(l.getDelegateId()).isNull();
    }
}
