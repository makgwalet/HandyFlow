package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.clinic.domain.model.ClinicPrescription;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPrescriptionRepository;
import za.co.handyflow.platform.clinic.dto.FillDtos.FillRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicDispensingServiceTest {

    @Mock ClinicPrescriptionRepository prescriptionRepo;
    @Mock JdbcTemplate jdbc;
    @InjectMocks ClinicDispensingService service;

    TenantId tenant;

    @BeforeEach
    void setUp() {
        tenant = Mockito.mock(TenantId.class, withSettings().lenient());
        lenient().when(tenant.getValue()).thenReturn(UUID.randomUUID());
    }

    private ClinicPrescription rx(int repeats, int quantity) {
        return ClinicPrescription.create(tenant, UUID.randomUUID(), UUID.randomUUID(), null,
                "Amoxicillin", "500mg", "TDS", "7 days", quantity, repeats, null);
    }

    @Test
    @DisplayName("records fill 1, defaults the quantity to the prescribed quantity, and logs it")
    void recordsFirstFill() {
        var p = rx(1, 21);
        when(prescriptionRepo.findForUpdate(tenant, p.getId())).thenReturn(Optional.of(p));

        var r = service.recordFill(tenant, p.getId(), null);

        assertThat(r.fillNumber()).isEqualTo(1);
        assertThat(r.quantity()).isEqualTo(21);
        verify(prescriptionRepo).save(p);
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("refuses a fill once every authorised fill is used, and logs nothing")
    void refusesWhenExhausted() {
        var p = rx(0, 10);
        p.recordFill();
        when(prescriptionRepo.findForUpdate(tenant, p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.recordFill(tenant, p.getId(), new FillRequest(null, null)))
                .isInstanceOf(IllegalStateException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("a zero or negative quantity is refused before anything changes")
    void badQuantity() {
        var p = rx(1, 10);
        when(prescriptionRepo.findForUpdate(tenant, p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.recordFill(tenant, p.getId(), new FillRequest(0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(p.getFillsUsed()).isZero();
    }

    @Test
    @DisplayName("an unknown prescription is not found")
    void notFound() {
        var id = UUID.randomUUID();
        when(prescriptionRepo.findForUpdate(tenant, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordFill(tenant, id, null)).isInstanceOf(ResourceNotFoundException.class);
    }
}
