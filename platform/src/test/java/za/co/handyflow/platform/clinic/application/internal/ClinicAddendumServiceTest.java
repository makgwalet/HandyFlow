package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationAddendumRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicAddendumServiceTest {

    @Mock ClinicConsultationRepository         consultationRepo;
    @Mock ClinicConsultationAddendumRepository addendumRepo;

    @InjectMocks ClinicAddendumService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = Mockito.mock(TenantId.class);
        Mockito.when(TENANT.getValue()).thenReturn(TENANT_UUID);
    }

    private ClinicConsultation signed() {
        ClinicConsultation c = ClinicConsultation.createDraft(TENANT, UUID.randomUUID(), null, null, "Cough");
        c.sign();
        when(consultationRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @DisplayName("an addendum on a signed consultation is saved trimmed and the consultation is untouched")
    void addsToSigned() {
        ClinicConsultation c = signed();
        String before = c.getChiefComplaint();

        var out = service.add(TENANT, c.getId(), "  Lab result received  ");

        assertThat(out.text()).isEqualTo("Lab result received");
        assertThat(c.getChiefComplaint()).isEqualTo(before);
        assertThat(c.getStatus()).isEqualTo("SIGNED");
        verify(addendumRepo).save(any());
    }

    @Test
    @DisplayName("a locked consultation accepts addenda")
    void addsToLocked() {
        ClinicConsultation c = signed();
        c.lock();

        service.add(TENANT, c.getId(), "Patient called back");

        verify(addendumRepo).save(any());
    }

    @Test
    @DisplayName("drafts and handoff states refuse addenda: edit the draft instead")
    void refusesUnsigned() {
        ClinicConsultation c = ClinicConsultation.createDraft(TENANT, UUID.randomUUID(), null, null, "Cough");
        when(consultationRepo.findActiveById(TENANT, c.getId())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.add(TENANT, c.getId(), "note"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DRAFT");
        verify(addendumRepo, never()).save(any());
    }

    @Test
    @DisplayName("empty and oversized addenda are rejected before any lookup")
    void validatesText() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.add(TENANT, id, "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(TENANT, id, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(TENANT, id, "x".repeat(5001)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("5000");
        verifyNoInteractions(consultationRepo, addendumRepo);
    }

    @Test
    @DisplayName("unknown consultation is a 404")
    void unknownConsultation() {
        UUID id = UUID.randomUUID();
        when(consultationRepo.findActiveById(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(TENANT, id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
