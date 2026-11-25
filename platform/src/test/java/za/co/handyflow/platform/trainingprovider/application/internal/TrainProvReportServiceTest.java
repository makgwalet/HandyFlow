package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAuthority;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvReportObligation;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvReportPeriod;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvAuthorityRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvReportObligationRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvReportPeriodRepository;
import za.co.handyflow.platform.trainingprovider.dto.ReportDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvReportServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 10);
    private static final String SOURCE = "Taken from the authority's letter of 3 May";

    @Mock TrainProvReportObligationRepository obligations;
    @Mock TrainProvReportPeriodRepository periods;
    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvReportService service;

    private UUID authorityId;

    @BeforeEach
    void setUp() {
        authorityId = UUID.randomUUID();
        TrainProvAuthority a = mock(TrainProvAuthority.class);
        lenient().when(a.getId()).thenReturn(authorityId);
        lenient().when(a.getName()).thenReturn("Some authority");
        lenient().when(authorities.findForTenant(any(), any())).thenReturn(Optional.of(a));
        lenient().when(numbers.nextObligationNumber(any())).thenReturn("OBL-00001");
        lenient().when(numbers.nextReportPeriodNumber(any())).thenReturn("RPT-00001");
    }

    private TrainProvReportObligation obligation() {
        return TrainProvReportObligation.create(TENANT, "OBL-00001", authorityId, null, "Quarterly report", "QUARTERLY", 30, SOURCE, USER);
    }

    @Test
    void createsAnObligationOnlyWithASourceNote() {
        ObligationView v = service.createObligation(TENANT, new ObligationRequest(authorityId, null, "Quarterly report", "QUARTERLY", 30, SOURCE), USER);
        assertThat(v.status()).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> service.createObligation(TENANT, new ObligationRequest(authorityId, null, "Quarterly report", "QUARTERLY", 30, "short"), USER))
                .isInstanceOf(HandyFlowException.class);
        when(obligations.liveNameTaken(any(), any(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createObligation(TENANT, new ObligationRequest(authorityId, null, "Quarterly report", "QUARTERLY", 30, SOURCE), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already an active obligation");
    }

    @Test
    void createsAPeriodWithTheComputedDueDay() {
        TrainProvReportObligation o = obligation();
        when(obligations.findForTenant(any(), any())).thenReturn(Optional.of(o));
        PeriodView v = service.createPeriod(TENANT, new PeriodRequest(o.getId(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31), null, null), USER, TODAY);
        assertThat(v.dueOn()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(v.state()).isEqualTo("OVERDUE");
        assertThat(v.dueOverrideReason()).isNull();
    }

    @Test
    void anOverriddenDueDayNeedsAReason() {
        TrainProvReportObligation o = obligation();
        when(obligations.findForTenant(any(), any())).thenReturn(Optional.of(o));
        PeriodRequest bad = new PeriodRequest(o.getId(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 5, 31), null);
        assertThatThrownBy(() -> service.createPeriod(TENANT, bad, USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("differs");
        PeriodView v = service.createPeriod(TENANT, new PeriodRequest(o.getId(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 5, 31), "Authority extended the deadline"), USER, TODAY);
        assertThat(v.dueOverrideReason()).isEqualTo("Authority extended the deadline");
    }

    @Test
    void refusesADuplicateWindowAndARetiredObligation() {
        TrainProvReportObligation o = obligation();
        when(obligations.findForTenant(any(), any())).thenReturn(Optional.of(o));
        when(periods.windowTaken(any(), any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.createPeriod(TENANT, new PeriodRequest(o.getId(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31), null, null), USER, TODAY))
                .hasMessageContaining("already exists");
        service.retireObligation(TENANT, o.getId(), "Authority no longer requires it", USER);
        assertThatThrownBy(() -> service.createPeriod(TENANT, new PeriodRequest(o.getId(), LocalDate.of(2027, 4, 1), LocalDate.of(2027, 6, 30), null, null), USER, TODAY))
                .hasMessageContaining("retired");
    }

    @Test
    void submitThenAcknowledgeOnceAndVoidWithReason() {
        TrainProvReportPeriod p = TrainProvReportPeriod.create(TENANT, "RPT-00001", UUID.randomUUID(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30), null, USER);
        when(periods.findForTenant(any(), any())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.acknowledge(TENANT, p.getId(), new AcknowledgeRequest(TODAY, "REF1"), TODAY)).hasMessageContaining("submitted period");
        PeriodView v = service.submit(TENANT, p.getId(), new SubmitRequest(TODAY.minusDays(3), "SUB-1", null), USER, TODAY);
        assertThat(v.state()).isEqualTo("SUBMITTED");
        assertThat(service.acknowledge(TENANT, p.getId(), new AcknowledgeRequest(TODAY, "ACK-1"), TODAY).acknowledgedOn()).isEqualTo(TODAY);
        assertThatThrownBy(() -> service.acknowledge(TENANT, p.getId(), new AcknowledgeRequest(TODAY, "ACK-2"), TODAY)).hasMessageContaining("already recorded");
        assertThatThrownBy(() -> service.voidPeriod(TENANT, p.getId(), "short", USER, TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(service.voidPeriod(TENANT, p.getId(), "Submitted against the wrong period", USER, TODAY).status()).isEqualTo("VOID");
    }

    @Test
    void listCanFilterByComputedState() {
        TrainProvReportPeriod late = TrainProvReportPeriod.create(TENANT, "RPT-00001", UUID.randomUUID(), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30), null, USER);
        TrainProvReportPeriod later = TrainProvReportPeriod.create(TENANT, "RPT-00002", UUID.randomUUID(), LocalDate.of(2027, 7, 1), LocalDate.of(2027, 9, 30), LocalDate.of(2027, 12, 1), null, USER);
        when(periods.search(any(), any(), any())).thenReturn(List.of(late, later));
        assertThat(service.listPeriods(TENANT, null, null, "OVERDUE", TODAY, null)).extracting(PeriodView::periodNo).containsExactly("RPT-00001");
        assertThat(service.listPeriods(TENANT, null, null, null, TODAY, null)).hasSize(2);
    }
}
