package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeVersion;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeWorkplaceHours;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeVersionRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvProgrammeWorkplaceHoursRepository;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeHoursDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvProgrammeHoursServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Mock private TrainProvProgrammeWorkplaceHoursRepository hours;
    @Mock private TrainProvProgrammeVersionRepository versions;
    @InjectMocks private TrainProvProgrammeHoursService service;

    private static HoursRequest request() {
        return new HoursRequest(new BigDecimal("120"), "Placement at a host employer", "CURRICULUM", "Curriculum document", LocalDate.of(2026, 1, 5), "Section 4", LocalDate.of(2026, 2, 1),
                "Sam Dube", LocalDate.of(2027, 3, 1));
    }

    private TrainProvProgrammeVersion draft() {
        TrainProvProgrammeVersion v = TrainProvProgrammeVersion.draft(TENANT, UUID.randomUUID(), 1, USER);
        when(versions.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        return v;
    }

    @Test
    void recordsHoursOnADraftVersion() {
        TrainProvProgrammeVersion v = draft();
        when(hours.findLive(TENANT, v.getId())).thenReturn(Optional.empty());
        service.record(TENANT, v.getId(), request(), USER, TODAY);
        verify(hours).save(any(TrainProvProgrammeWorkplaceHours.class));
    }

    @Test
    void refusesHoursWithoutASource() {
        TrainProvProgrammeVersion v = draft();
        HoursRequest r = new HoursRequest(new BigDecimal("120"), null, "CURRICULUM", " ", null, "Section 4", LocalDate.of(2026, 2, 1), "Sam Dube", LocalDate.of(2027, 3, 1));
        assertThatThrownBy(() -> service.record(TENANT, v.getId(), r, USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("document or reference");
        verify(hours, never()).save(any());
    }

    @Test
    void approvedVersionIsFrozen() {
        TrainProvProgrammeVersion v = draft();
        v.approve(UUID.randomUUID(), null);
        assertThatThrownBy(() -> service.record(TENANT, v.getId(), request(), USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("frozen");
        assertThatThrownBy(() -> service.withdraw(TENANT, v.getId(), "Entered against the wrong version", USER)).isInstanceOf(HandyFlowException.class);
        verify(hours, never()).save(any());
    }

    @Test
    void withdrawKeepsTheRecordWithItsReason() {
        TrainProvProgrammeVersion v = draft();
        TrainProvProgrammeWorkplaceHours h = TrainProvProgrammeWorkplaceHours.create(TENANT, v.getId(),
                new za.co.handyflow.platform.trainingprovider.domain.rules.ProgrammeHoursRules.Entry(new BigDecimal("120"), "CURRICULUM", "Doc", null, "p1", TODAY, "Sam", TODAY), null, USER);
        when(hours.findLive(TENANT, v.getId())).thenReturn(Optional.of(h));
        service.withdraw(TENANT, v.getId(), "Entered against the wrong version", USER);
        assertThat(h.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(h.getWithdrawnReason()).isEqualTo("Entered against the wrong version");
        verify(hours, never()).delete(any());
    }

    @Test
    void withdrawWithoutHoursIsAConflict() {
        TrainProvProgrammeVersion v = draft();
        when(hours.findLive(TENANT, v.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdraw(TENANT, v.getId(), "Entered against the wrong version", USER)).isInstanceOf(HandyFlowException.class).hasMessageContaining("records no workplace hours");
    }
}
