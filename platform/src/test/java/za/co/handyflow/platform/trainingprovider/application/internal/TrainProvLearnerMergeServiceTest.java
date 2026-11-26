package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerMerge;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.LearnerMatchDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvLearnerMergeServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock TrainProvLearnerMergeRepository merges;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerApplicationRepository applications;
    @Mock TrainProvLearnerDocumentRepository documents;
    @Mock TrainProvLearnerEventRepository events;
    @InjectMocks TrainProvLearnerMergeService service;

    private TrainProvLearner learner(String no, String idNumber) {
        return TrainProvLearner.create(TENANT, no, "Thandi", "Nkosi", "PASSPORT", idNumber, null, null, null, null, null, null, null, false, null,
                null, ME, TODAY);
    }

    private void found(TrainProvLearner... ls) {
        for (TrainProvLearner l : ls) {
            lenient().when(learners.findForTenant(TENANT, l.getId())).thenReturn(Optional.of(l));
        }
    }

    private TrainProvLearnerMerge proposed(TrainProvLearner keep, TrainProvLearner drop) {
        TrainProvLearnerMerge m = TrainProvLearnerMerge.propose(TENANT, keep.getId(), drop.getId(), "ID_NUMBER", null, false,
                "Same passport on both records", "keep", "drop", ME);
        when(merges.findForTenant(TENANT, m.getId())).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void proposesAMergeOfTwoLiveLearners() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        found(keep, drop);
        MergeView v = service.propose(TENANT, new MergeProposalRequest(keep.getId(), drop.getId(), "Same passport on both records", false), ME);
        assertThat(v.status()).isEqualTo("PROPOSED");
        assertThat(v.matchReasons()).contains("ID_NUMBER");
        verify(merges).save(any(TrainProvLearnerMerge.class));
    }

    @Test
    void refusesToProposeAnArchivedLearner() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        drop.archive("Left the programme for good", ME);
        found(keep, drop);
        assertThatThrownBy(() -> service.propose(TENANT, new MergeProposalRequest(keep.getId(), drop.getId(), "Same passport on both records", false), ME))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("live");
    }

    @Test
    void refusesDifferingIdentityNumbersUntilTheProposerAcceptsThem() {
        TrainProvLearner keep = learner("LRN-1", "A1111111");
        TrainProvLearner drop = learner("LRN-2", "B2222222");
        found(keep, drop);
        assertThatThrownBy(() -> service.propose(TENANT, new MergeProposalRequest(keep.getId(), drop.getId(), "Typo in one passport number", false), ME))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("disagree");
    }

    @Test
    void refusesASecondOpenMergeForTheSameLearner() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        found(keep, drop);
        when(merges.openFor(TENANT, drop.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.propose(TENANT, new MergeProposalRequest(keep.getId(), drop.getId(), "Same passport on both records", false), ME))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already waiting");
    }

    @Test
    void approvingMovesTheRecordsAndArchivesTheMergedLearner() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        found(keep, drop);
        TrainProvLearnerMerge m = proposed(keep, drop);
        when(enrolments.moveLearner(TENANT, drop.getId(), keep.getId())).thenReturn(2);
        when(applications.moveLearner(TENANT, drop.getId(), keep.getId())).thenReturn(1);
        when(documents.moveLearner(TENANT, drop.getId(), keep.getId())).thenReturn(3);
        MergeView v = service.approve(TENANT, m.getId(), new MergeDecisionRequest("Checked both records against the passport", false, null), OTHER);
        assertThat(v.status()).isEqualTo("COMPLETED");
        assertThat(v.movedEnrolments()).isEqualTo(2);
        assertThat(v.movedDocuments()).isEqualTo(3);
        assertThat(drop.getStatus()).isEqualTo("ARCHIVED");
        assertThat(drop.getMergedIntoId()).isEqualTo(keep.getId());
        assertThat(keep.getStatus()).isEqualTo("ACTIVE");
        verify(events, times(2)).save(any());
    }

    @Test
    void theProposerCannotApproveAloneWithoutSayingWhy() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        TrainProvLearnerMerge m = proposed(keep, drop);
        assertThatThrownBy(() -> service.approve(TENANT, m.getId(), new MergeDecisionRequest("Checked both records against the passport", false, null), ME))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("second person");
        verify(enrolments, never()).moveLearner(any(), any(), any());
    }

    @Test
    void refusesWhenBothLearnersAreLiveOnTheSameProgramme() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        found(keep, drop);
        TrainProvLearnerMerge m = proposed(keep, drop);
        UUID programme = UUID.randomUUID();
        when(enrolments.liveProgrammeIds(TENANT, keep.getId())).thenReturn(List.of(programme));
        when(enrolments.liveProgrammeIds(TENANT, drop.getId())).thenReturn(List.of(programme));
        assertThatThrownBy(() -> service.approve(TENANT, m.getId(), new MergeDecisionRequest("Checked both records against the passport", false, null), OTHER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("same programme");
        assertThat(drop.getStatus()).isEqualTo("ACTIVE");
        verify(enrolments, never()).moveLearner(any(), any(), any());
    }

    @Test
    void theDelegateLinkOfTheMergedLearnerMovesWhenTheKeptOneHasNone() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        UUID delegate = UUID.randomUUID();
        drop.relink(delegate);
        found(keep, drop);
        TrainProvLearnerMerge m = proposed(keep, drop);
        MergeView v = service.approve(TENANT, m.getId(), new MergeDecisionRequest("Checked both records against the passport", false, null), OTHER);
        assertThat(keep.getDelegateId()).isEqualTo(delegate);
        assertThat(v.delegateNote()).contains("now sits on");
    }

    @Test
    void aMergedLearnerCannotBeRestored() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        drop.mergeInto(keep.getId(), "Merged into LRN-1: same passport", ME);
        assertThatThrownBy(drop::restore).isInstanceOf(IllegalStateException.class).hasMessageContaining("merged");
    }

    @Test
    void onlyTheProposerCancels() {
        TrainProvLearner keep = learner("LRN-1", "A1234567");
        TrainProvLearner drop = learner("LRN-2", "A1234567");
        TrainProvLearnerMerge m = proposed(keep, drop);
        assertThatThrownBy(() -> service.cancel(TENANT, m.getId(), "Proposed by mistake today", OTHER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.cancel(TENANT, m.getId(), "Proposed by mistake today", ME).status()).isEqualTo("CANCELLED");
    }
}
