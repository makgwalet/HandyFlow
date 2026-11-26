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
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvWorkplaceLog;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvWorkplacePlacement;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerEnrolmentRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeWorkplaceHoursRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvWorkplaceLogRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvWorkplacePlacementRepository;
import za.co.handyflow.platform.trainingprovider.dto.WorkplaceDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvWorkplaceServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final LocalDate START = LocalDate.of(2027, 6, 1);

    @Mock TrainProvWorkplacePlacementRepository placements;
    @Mock TrainProvWorkplaceLogRepository logs;
    @Mock TrainProvLearnerEnrolmentRepository enrolments;
    @Mock TrainProvLearnerRepository learners;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeWorkplaceHoursRepository programmeHours;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvWorkplaceService service;

    private TrainProvLearnerEnrolment enrolment;

    @BeforeEach
    void setUp() {
        enrolment = mock(TrainProvLearnerEnrolment.class);
        lenient().when(enrolment.getId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getStatus()).thenReturn("IN_PROGRESS");
        lenient().when(enrolment.getStartDate()).thenReturn(START);
        lenient().when(enrolment.getLearnerId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolment.getProgrammeId()).thenReturn(UUID.randomUUID());
        lenient().when(enrolments.findForTenant(any(), any())).thenReturn(Optional.of(enrolment));
        lenient().when(numbers.nextPlacementNumber(any())).thenReturn("WPL-00001");
        lenient().when(logs.forPlacement(any(), any())).thenReturn(List.of());
    }

    private TrainProvWorkplacePlacement planned() {
        return TrainProvWorkplacePlacement.create(TENANT, "WPL-00001", enrolment.getId(), "Acme", null, "Ms Mentor", null, null, START, null, USER);
    }

    private TrainProvWorkplacePlacement active() {
        TrainProvWorkplacePlacement p = planned();
        p.start("IN_PROGRESS", START, TODAY);
        return p;
    }

    @Test
    void addsAPlannedPlacementOnlyForAnOpenEnrolment() {
        PlacementView v = service.create(TENANT, new PlacementRequest(enrolment.getId(), "Acme", null, "Ms Mentor", null, null, START, null), USER);
        assertThat(v.status()).isEqualTo("PLANNED");
        when(enrolment.getStatus()).thenReturn("CANCELLED");
        assertThatThrownBy(() -> service.create(TENANT, new PlacementRequest(enrolment.getId(), "Acme", null, "Ms Mentor", null, null, START, null), USER))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void onlyOneActivePlacementPerEnrolment() {
        TrainProvWorkplacePlacement p = planned();
        when(placements.findForTenant(any(), any())).thenReturn(Optional.of(p));
        when(placements.activeExists(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.start(TENANT, p.getId(), TODAY, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already has an active placement");
        when(placements.activeExists(any(), any())).thenReturn(false);
        assertThat(service.start(TENANT, p.getId(), TODAY, TODAY).status()).isEqualTo("ACTIVE");
    }

    @Test
    void logsOnlyWhileActiveAndOncePerDay() {
        TrainProvWorkplacePlacement planned = planned();
        when(placements.findForTenant(any(), any())).thenReturn(Optional.of(planned));
        LogRequest r = new LogRequest(TODAY, BigDecimal.valueOf(8), "Assisted with stock counts and shelving");
        assertThatThrownBy(() -> service.addLog(TENANT, planned.getId(), r, USER, TODAY)).hasMessageContaining("placement is active");
        TrainProvWorkplacePlacement a = active();
        when(placements.findForTenant(any(), any())).thenReturn(Optional.of(a));
        assertThat(service.addLog(TENANT, a.getId(), r, USER, TODAY).hours()).isEqualByComparingTo("8");
        when(logs.dayTaken(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.addLog(TENANT, a.getId(), r, USER, TODAY)).hasMessageContaining("already an entry");
    }

    @Test
    void totalsSplitConfirmedAndUnconfirmedAndSkipVoid() {
        TrainProvWorkplacePlacement a = active();
        TrainProvWorkplaceLog c = TrainProvWorkplaceLog.record(TENANT, a.getId(), TODAY.minusDays(2), BigDecimal.valueOf(8), "Assisted with stock counts and shelving", USER);
        c.confirm(TODAY, "Ms Mentor", USER, TODAY);
        TrainProvWorkplaceLog u = TrainProvWorkplaceLog.record(TENANT, a.getId(), TODAY.minusDays(1), BigDecimal.valueOf(4), "Assisted with stock counts and shelving", USER);
        TrainProvWorkplaceLog v = TrainProvWorkplaceLog.record(TENANT, a.getId(), TODAY, BigDecimal.valueOf(6), "Assisted with stock counts and shelving", USER);
        v.voidIt("Entered against the wrong day", USER);
        when(logs.forPlacement(any(), any())).thenReturn(List.of(c, u, v));
        when(placements.findForTenant(any(), any())).thenReturn(Optional.of(a));
        PlacementDetail d = service.get(TENANT, a.getId());
        assertThat(d.placement().confirmedHours()).isEqualByComparingTo("8");
        assertThat(d.placement().unconfirmedHours()).isEqualByComparingTo("4");
        assertThat(d.placement().summary()).contains("not a competency decision");
    }

    @Test
    void confirmationNeedsTheMentorNameAndHappensOnce() {
        TrainProvWorkplaceLog l = TrainProvWorkplaceLog.record(TENANT, UUID.randomUUID(), TODAY.minusDays(1), BigDecimal.valueOf(8), "Assisted with stock counts and shelving", USER);
        when(logs.findForTenant(any(), any())).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> service.confirm(TENANT, l.getId(), new ConfirmRequest(TODAY, " "), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(service.confirm(TENANT, l.getId(), new ConfirmRequest(TODAY, "Ms Mentor"), USER, TODAY).confirmedByName()).isEqualTo("Ms Mentor");
        assertThatThrownBy(() -> service.confirm(TENANT, l.getId(), new ConfirmRequest(TODAY, "Ms Mentor"), USER, TODAY)).hasMessageContaining("already confirmed");
        assertThatThrownBy(() -> service.voidLog(TENANT, l.getId(), "short", USER)).isInstanceOf(HandyFlowException.class);
        assertThat(service.voidLog(TENANT, l.getId(), "Entered against the wrong day", USER).status()).isEqualTo("VOID");
    }

    @Test
    void completesAndCancelsWithRules() {
        TrainProvWorkplacePlacement a = active();
        when(placements.findForTenant(any(), any())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.cancel(TENANT, a.getId(), "short")).isInstanceOf(HandyFlowException.class);
        assertThat(service.complete(TENANT, a.getId(), TODAY, TODAY).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.cancel(TENANT, a.getId(), "Host withdrew the offer")).hasMessageContaining("cannot be cancelled");
    }
}
