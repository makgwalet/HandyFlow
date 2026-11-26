package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.PriceDtos.PriceRequest;
import za.co.handyflow.platform.trainingprovider.dto.PriceDtos.PriceView;
import za.co.handyflow.platform.trainingprovider.dto.PriceDtos.Suggestion;

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
class TrainProvPriceServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final String NOTE = "Price list of 2027 as approved";

    @Mock private TrainProvPriceLineRepository lines;
    @Mock private TrainProvProgrammeVersionRepository versions;
    @Mock private TrainProvFunderRepository funders;
    @Mock private TrainProvLearnerEnrolmentRepository enrolments;
    @Mock private TrainProvEnrolmentFundingRepository funding;
    @InjectMocks private TrainProvPriceService service;

    private final UUID versionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(versions.findForTenant(TENANT, versionId)).thenReturn(Optional.of(mock(TrainProvProgrammeVersion.class)));
        lenient().when(lines.save(any(TrainProvPriceLine.class))).thenAnswer(i -> i.getArgument(0));
    }

    private PriceRequest request(LocalDate from, LocalDate to) {
        return new PriceRequest(versionId, null, "TUITION", null, new BigDecimal("1500.00"), true, from, to, NOTE);
    }

    @Test
    void recordsAFeeWithItsSource() {
        when(lines.liveForSameFee(TENANT, versionId, null, "TUITION")).thenReturn(List.of());
        PriceView v = service.create(TENANT, request(TODAY, null), USER, TODAY);
        assertThat(v.amount()).isEqualByComparingTo("1500.00");
        assertThat(v.sourceNote()).isEqualTo(NOTE);
        assertThat(v.inForceToday()).isTrue();
    }

    @Test
    void refusesAFeeThatOverlapsALiveOneForTheSameFee() {
        TrainProvPriceLine existing = TrainProvPriceLine.create(TENANT, versionId, null, "TUITION", null, new BigDecimal("1400.00"), true, TODAY.minusDays(30), null, NOTE, USER);
        when(lines.liveForSameFee(TENANT, versionId, null, "TUITION")).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(TENANT, request(TODAY, null), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("already covers part of that period");
        verify(lines, never()).save(any(TrainProvPriceLine.class));
    }

    @Test
    void allowsANewFeeThatStartsAfterTheOldOneEnds() {
        TrainProvPriceLine existing = TrainProvPriceLine.create(TENANT, versionId, null, "TUITION", null, new BigDecimal("1400.00"), true, TODAY.minusDays(30), TODAY.minusDays(1), NOTE, USER);
        when(lines.liveForSameFee(TENANT, versionId, null, "TUITION")).thenReturn(List.of(existing));
        assertThat(service.create(TENANT, request(TODAY, null), USER, TODAY).validFrom()).isEqualTo(TODAY);
    }

    @Test
    void aBadFeeIsRefusedInPlainWords() {
        PriceRequest bad = new PriceRequest(versionId, null, "TUITION", null, BigDecimal.ZERO, true, TODAY, null, NOTE);
        assertThatThrownBy(() -> service.create(TENANT, bad, USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("more than 0");
    }

    @Test
    void withdrawingKeepsTheLineAndNeedsAReason() {
        TrainProvPriceLine p = TrainProvPriceLine.create(TENANT, versionId, null, "TUITION", null, new BigDecimal("1400.00"), true, TODAY, null, NOTE, USER);
        when(lines.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.withdraw(TENANT, p.getId(), "short", USER, TODAY)).isInstanceOf(HandyFlowException.class);
        PriceView v = service.withdraw(TENANT, p.getId(), "Replaced by the new price list", USER, TODAY);
        assertThat(v.status()).isEqualTo("WITHDRAWN");
        assertThat(v.inForceToday()).isFalse();
        verify(lines, never()).delete(any(TrainProvPriceLine.class));
    }

    @Test
    void suggestsGeneralLinesAndOnlyTheLinesOfAFunderPayingForTheEnrolment() {
        UUID enrolmentId = UUID.randomUUID();
        UUID payingFunder = UUID.randomUUID();
        UUID otherFunder = UUID.randomUUID();
        TrainProvLearnerEnrolment e = mock(TrainProvLearnerEnrolment.class);
        when(e.getProgrammeVersionId()).thenReturn(versionId);
        when(enrolments.findForTenant(TENANT, enrolmentId)).thenReturn(Optional.of(e));
        TrainProvEnrolmentFunding link = mock(TrainProvEnrolmentFunding.class);
        when(link.getFunderId()).thenReturn(payingFunder);
        when(funding.search(TENANT, enrolmentId, null, "ACTIVE")).thenReturn(List.of(link));
        TrainProvPriceLine general = TrainProvPriceLine.create(TENANT, versionId, null, "TUITION", null, new BigDecimal("1500.00"), true, TODAY, null, NOTE, USER);
        TrainProvPriceLine paying = TrainProvPriceLine.create(TENANT, versionId, payingFunder, "TUITION", null, new BigDecimal("1300.00"), true, TODAY, null, NOTE, USER);
        TrainProvPriceLine other = TrainProvPriceLine.create(TENANT, versionId, otherFunder, "TUITION", null, new BigDecimal("900.00"), true, TODAY, null, NOTE, USER);
        when(lines.inForce(TENANT, versionId, TODAY)).thenReturn(List.of(general, paying, other));
        TrainProvFunder f = mock(TrainProvFunder.class);
        when(f.getName()).thenReturn("Skills fund");
        when(funders.findForTenant(TENANT, payingFunder)).thenReturn(Optional.of(f));

        Suggestion s = service.suggest(TENANT, enrolmentId, TODAY);

        assertThat(s.rows()).hasSize(2);
        assertThat(s.rows()).extracting(r -> r.amount().toPlainString()).containsExactlyInAnyOrder("1500.00", "1300.00");
        assertThat(s.note()).contains("not an invoice");
    }

    @Test
    void saysSoWhenNothingIsInForce() {
        UUID enrolmentId = UUID.randomUUID();
        TrainProvLearnerEnrolment e = mock(TrainProvLearnerEnrolment.class);
        when(e.getProgrammeVersionId()).thenReturn(versionId);
        when(enrolments.findForTenant(TENANT, enrolmentId)).thenReturn(Optional.of(e));
        when(funding.search(TENANT, enrolmentId, null, "ACTIVE")).thenReturn(List.of());
        when(lines.inForce(TENANT, versionId, TODAY)).thenReturn(List.of());
        Suggestion s = service.suggest(TENANT, enrolmentId, TODAY);
        assertThat(s.rows()).isEmpty();
        assertThat(s.note()).contains("Nothing is suggested");
    }
}
