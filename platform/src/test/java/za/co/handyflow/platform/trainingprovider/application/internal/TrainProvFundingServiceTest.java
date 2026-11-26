package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvEnrolmentFunding;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvFunder;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerEnrolment;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvClientRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvEnrolmentFundingRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvFunderRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.dto.FundingDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvFundingServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);

    @Mock TrainProvFunderRepository funders;
    @Mock TrainProvEnrolmentFundingRepository links;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvClientRepository clients;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvFundingService service;

    private TrainProvLearnerEnrolment enrolment;
    private TrainProvFunder funder;

    @BeforeEach
    void setUp() {
        enrolment = mock(TrainProvLearnerEnrolment.class);
        lenient().when(enrolment.getId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(enrolment.getStartDate()).thenReturn(START);
        lenient().when(enrolment.getLearnerId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.of(enrolment));
        lenient().when(numbers.nextFunderNumber(any())).thenReturn("FND-00001");
        lenient().when(links.activeForFunder(any(), any())).thenReturn(0L);
        funder = TrainProvFunder.create(TENANT, "FND-00001", "Acme Stores", "EMPLOYER", null, null, null, null, null, USER);
        lenient().when(funders.findForTenant(any(), any())).thenReturn(Optional.of(funder));
    }

    @Test
    void addsAFunderWhenTheNameIsFree() {
        when(funders.nameTaken(any(), any(), any())).thenReturn(false);
        FunderView v = service.createFunder(TENANT, new FunderRequest("Acme Stores", "EMPLOYER", null, null, "hr@acme.co.za", null, null), USER);
        assertThat(v.funderNo()).isEqualTo("FND-00001");
        verify(funders).save(any());
    }

    @Test
    void refusesADuplicateLiveName() {
        when(funders.nameTaken(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createFunder(TENANT, new FunderRequest("Acme Stores", "EMPLOYER", null, null, null, null, null), USER)).isInstanceOf(HandyFlowException.class);
        verify(funders, never()).save(any());
    }

    @Test
    void linksAFunderToAnOpenEnrolmentOnce() {
        when(links.liveExists(any(), any(), any())).thenReturn(false);
        LinkView v = service.link(TENANT, new LinkRequest(enrolment.getId(), funder.getId(), START, "AGR-1", null, null), USER, TODAY);
        assertThat(v.funderName()).isEqualTo("Acme Stores");
        assertThat(v.status()).isEqualTo("ACTIVE");
        when(links.liveExists(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.link(TENANT, new LinkRequest(enrolment.getId(), funder.getId(), START, null, null, null), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(links, times(1)).save(any());
    }

    @Test
    void cannotLinkARetiredFunderOrACancelledEnrolment() {
        funder.retire("Stopped sponsoring learners", 0, USER);
        assertThatThrownBy(() -> service.link(TENANT, new LinkRequest(enrolment.getId(), funder.getId(), START, null, null, null), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        when(enrolment.getStatus()).thenReturn("CANCELLED");
        assertThatThrownBy(() -> service.link(TENANT, new LinkRequest(enrolment.getId(), funder.getId(), START, null, null, null), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(links, never()).save(any());
    }

    @Test
    void cannotRetireAFunderWithActiveLinksAndNeedsAReason() {
        when(links.activeForFunder(any(), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.retireFunder(TENANT, funder.getId(), "Stopped sponsoring learners", USER)).isInstanceOf(HandyFlowException.class);
        when(links.activeForFunder(any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> service.retireFunder(TENANT, funder.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.retireFunder(TENANT, funder.getId(), "Stopped sponsoring learners", USER).status()).isEqualTo("RETIRED");
    }

    @Test
    void endsALinkWithAReasonAndSummaryNeverClaimsApproval() {
        TrainProvEnrolmentFunding l = TrainProvEnrolmentFunding.link(TENANT, enrolment.getId(), funder.getId(), START, null, null, null, USER);
        when(links.findForTenant(any(), any())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.end(TENANT, l.getId(), new EndRequest(TODAY, "short"), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(service.end(TENANT, l.getId(), new EndRequest(TODAY, "Employer withdrew sponsorship"), USER, TODAY).status()).isEqualTo("ENDED");
        when(links.search(any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.forEnrolment(TENANT, enrolment.getId()).summary()).isEqualTo("No funder is recorded for this enrolment.");
    }
}
