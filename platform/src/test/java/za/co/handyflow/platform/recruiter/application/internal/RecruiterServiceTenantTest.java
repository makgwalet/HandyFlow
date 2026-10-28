package za.co.handyflow.platform.recruiter.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import za.co.handyflow.platform.recruiter.domain.model.RecApplication;
import za.co.handyflow.platform.recruiter.domain.model.RecInterview;
import za.co.handyflow.platform.recruiter.domain.repository.*;
import za.co.handyflow.platform.recruiter.dto.RecordInterviewOutcomeRequest;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Another company's job, application or interview must read as not found, and a finished application cannot take a result. */
class RecruiterServiceTenantTest {

    private final RecJobRepository jobs = mock(RecJobRepository.class);
    private final RecApplicationRepository applications = mock(RecApplicationRepository.class);
    private final RecInterviewRepository interviews = mock(RecInterviewRepository.class);
    private final TenantId mine = TenantId.of(UUID.randomUUID());
    private final UUID appId = UUID.randomUUID(), intId = UUID.randomUUID(), jobId = UUID.randomUUID();
    private RecruiterService service;

    @BeforeEach
    void setUp() {
        service = new RecruiterService(jobs, mock(RecApplicantRepository.class), applications, interviews, mock(RecStageHistoryRepository.class),
                null, null, null, null, null, null, mock(RecInterviewPanelistRepository.class), mock(RecJobInterviewRoundRepository.class), null, null);
    }

    @Test
    void listingApplicationsForAJobOfAnotherCompanyIsNotFoundAndReadsNothing() {
        when(jobs.findByIdAndTenantId(jobId, mine)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getApplications(mine, jobId, null, PageRequest.of(0, 10))).isInstanceOf(ResourceNotFoundException.class);
        verify(applications, never()).findByJob(any(), any(), any(), any());
    }

    @Test
    void anOutcomeForAnApplicationOfAnotherCompanyIsNotFound() {
        when(applications.findByIdAndTenantId(appId, mine)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordOutcome(mine, appId, intId, new RecordInterviewOutcomeRequest("PASSED", null, 70)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(interviews, never()).save(any());
    }

    @Test
    void anOutcomeForAnInterviewOfADifferentApplicationIsNotFound() {
        var app = mock(RecApplication.class);
        when(applications.findByIdAndTenantId(appId, mine)).thenReturn(Optional.of(app));
        var interview = mock(RecInterview.class);
        when(interview.getApplicationId()).thenReturn(UUID.randomUUID());
        when(interviews.findById(intId)).thenReturn(Optional.of(interview));
        assertThatThrownBy(() -> service.recordOutcome(mine, appId, intId, new RecordInterviewOutcomeRequest("PASSED", null, 70)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(interview, never()).recordOutcome(any(), any(), any());
    }

    @Test
    void aWithdrawnApplicationTakesNoResult() {
        var app = mock(RecApplication.class);
        when(app.isActive()).thenReturn(false);
        when(applications.findByIdAndTenantId(appId, mine)).thenReturn(Optional.of(app));
        var interview = mock(RecInterview.class);
        when(interview.getApplicationId()).thenReturn(appId);
        when(interview.getOutcome()).thenReturn("PENDING");
        when(interviews.findById(intId)).thenReturn(Optional.of(interview));
        assertThatThrownBy(() -> service.recordOutcome(mine, appId, intId, new RecordInterviewOutcomeRequest("PASSED", null, 70)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no longer active");
        verify(interview, never()).recordOutcome(any(), any(), any());
    }

    @Test
    void aSecondResultIsRefused() {
        var app = mock(RecApplication.class);
        when(app.isActive()).thenReturn(true);
        when(applications.findByIdAndTenantId(appId, mine)).thenReturn(Optional.of(app));
        var interview = mock(RecInterview.class);
        when(interview.getApplicationId()).thenReturn(appId);
        when(interview.getOutcome()).thenReturn("PASSED");
        when(interviews.findById(intId)).thenReturn(Optional.of(interview));
        assertThatThrownBy(() -> service.recordOutcome(mine, appId, intId, new RecordInterviewOutcomeRequest("FAILED", null, 10)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has a result");
    }

    @Test
    void aValidResultIsRecordedOnce() {
        var app = mock(RecApplication.class);
        when(app.isActive()).thenReturn(true);
        when(applications.findByIdAndTenantId(appId, mine)).thenReturn(Optional.of(app));
        var interview = mock(RecInterview.class);
        when(interview.getApplicationId()).thenReturn(appId);
        when(interview.getOutcome()).thenReturn("PENDING");
        when(interviews.findById(intId)).thenReturn(Optional.of(interview));
        service.recordOutcome(mine, appId, intId, new RecordInterviewOutcomeRequest("passed", "Good", 80));
        verify(interview).recordOutcome("PASSED", "Good", 80);
        verify(interviews).save(interview);
    }
}
