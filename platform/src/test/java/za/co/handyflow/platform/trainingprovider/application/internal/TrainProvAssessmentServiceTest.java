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
import za.co.handyflow.platform.trainingprovider.dto.AssessmentDtos.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvAssessmentServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID VERSION = UUID.randomUUID();
    private static final UUID PROGRAMME = UUID.randomUUID();

    @Mock TrainProvAssessmentInstrumentRepository instruments;
    @Mock TrainProvAssessmentInstrumentEventRepository events;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvProgrammeModuleRepository modules;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvNumberGenerator numbers;
    @InjectMocks TrainProvAssessmentService service;

    private TrainProvProgrammeVersion version;

    @BeforeEach
    void setUp() {
        version = mock(TrainProvProgrammeVersion.class);
        lenient().when(version.getId()).thenReturn(VERSION);
        lenient().when(version.getProgrammeId()).thenReturn(PROGRAMME);
        lenient().when(version.getVersionNo()).thenReturn(1);
        lenient().when(version.getStatus()).thenReturn("APPROVED");
        lenient().when(versions.findForTenant(TENANT, VERSION)).thenReturn(Optional.of(version));
        lenient().when(programmes.findForTenant(TENANT, PROGRAMME)).thenReturn(Optional.empty());
        lenient().when(numbers.nextInstrumentNumber(TENANT)).thenReturn("ASI-00001");
        lenient().when(instruments.liveTitleTaken(any(), any(), anyString(), any())).thenReturn(false);
        lenient().when(events.findForInstrument(any(), any())).thenReturn(java.util.List.of());
    }

    private InstrumentRequest request(UUID moduleId, BigDecimal max) {
        return new InstrumentRequest(VERSION, moduleId, "Safety theory test", "KNOWLEDGE", max, null, null);
    }

    private TrainProvAssessmentInstrument draft() {
        TrainProvAssessmentInstrument i = TrainProvAssessmentInstrument.create(TENANT, "ASI-00001", VERSION, null, "Safety theory test", "KNOWLEDGE", null, null, true, AUTHOR);
        when(instruments.findForTenant(TENANT, i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void createsADraftAndWritesHistory() {
        InstrumentView v = service.create(TENANT, request(null, new BigDecimal("50")), AUTHOR);
        assertThat(v.status()).isEqualTo("DRAFT");
        assertThat(v.instrumentNo()).isEqualTo("ASI-00001");
        assertThat(v.requiresModeration()).isTrue();
        verify(events).save(any(TrainProvAssessmentInstrumentEvent.class));
    }

    @Test
    void refusesASupersededVersion() {
        when(version.getStatus()).thenReturn("SUPERSEDED");
        assertThatThrownBy(() -> service.create(TENANT, request(null, null), AUTHOR)).isInstanceOf(HandyFlowException.class);
        verify(instruments, never()).save(any());
    }

    @Test
    void refusesAModuleFromAnotherVersion() {
        UUID moduleId = UUID.randomUUID();
        TrainProvProgrammeModule m = mock(TrainProvProgrammeModule.class);
        when(m.getProgrammeVersionId()).thenReturn(UUID.randomUUID());
        when(modules.findForTenant(TENANT, moduleId)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.create(TENANT, request(moduleId, null), AUTHOR)).isInstanceOf(HandyFlowException.class);
        verify(instruments, never()).save(any());
    }

    @Test
    void refusesALiveDuplicateTitle() {
        when(instruments.liveTitleTaken(any(), any(), anyString(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(TENANT, request(null, null), AUTHOR)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void refusesAZeroMaxMarkAsABadRequest() {
        assertThatThrownBy(() -> service.create(TENANT, request(null, BigDecimal.ZERO), AUTHOR))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("maximum mark");
    }

    @Test
    void authorCannotApproveOwnWithoutAReason() {
        TrainProvAssessmentInstrument i = draft();
        assertThatThrownBy(() -> service.approve(TENANT, i.getId(), null, AUTHOR)).isInstanceOf(HandyFlowException.class);
        assertThat(i.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void authorMayApproveWithAReasonAndItIsKept() {
        TrainProvAssessmentInstrument i = draft();
        InstrumentView v = service.approve(TENANT, i.getId(), "Only qualified person available this week", AUTHOR);
        assertThat(v.status()).isEqualTo("APPROVED");
        assertThat(v.approvalOverrideReason()).isEqualTo("Only qualified person available this week");
    }

    @Test
    void secondPersonApprovesWithoutAReason() {
        TrainProvAssessmentInstrument i = draft();
        InstrumentView v = service.approve(TENANT, i.getId(), null, OTHER);
        assertThat(v.status()).isEqualTo("APPROVED");
        assertThat(v.approvalOverrideReason()).isNull();
    }

    @Test
    void approvedInstrumentIsNotEdited() {
        TrainProvAssessmentInstrument i = draft();
        service.approve(TENANT, i.getId(), null, OTHER);
        assertThatThrownBy(() -> service.update(TENANT, i.getId(), new InstrumentUpdate(null, "New title", "PRACTICAL", null, null, true), AUTHOR))
                .isInstanceOf(HandyFlowException.class);
        assertThat(i.getTitle()).isEqualTo("Safety theory test");
    }

    @Test
    void retireNeedsAReasonAndKeepsTheRow() {
        TrainProvAssessmentInstrument i = draft();
        assertThatThrownBy(() -> service.retire(TENANT, i.getId(), "short", OTHER)).isInstanceOf(HandyFlowException.class);
        InstrumentView v = service.retire(TENANT, i.getId(), "Replaced by the revised instrument", OTHER);
        assertThat(v.status()).isEqualTo("RETIRED");
        assertThat(v.retiredReason()).isEqualTo("Replaced by the revised instrument");
        verify(instruments, never()).delete(any());
    }
}
