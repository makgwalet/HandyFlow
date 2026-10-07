package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.clinic.domain.model.ClinicLabResult;
import za.co.handyflow.platform.clinic.domain.repository.ClinicLabResultRepository;
import za.co.handyflow.platform.clinic.dto.lab.LabMarkerDtos.MarkerInput;
import za.co.handyflow.platform.clinic.dto.lab.LabMarkerDtos.SaveMarkersRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicLabMarkerServiceTest {

    @Mock ClinicLabResultRepository labRepo;
    @Mock JdbcTemplate jdbc;

    @InjectMocks ClinicLabMarkerService service;

    static final TenantId TENANT = TenantId.of(UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f"));

    private ClinicLabResult result() {
        return ClinicLabResult.create(TENANT, "AMPATH", "key", "k.pdf", "Nkosi S", "AMP-1");
    }

    private static MarkerInput potassium(String value) {
        return new MarkerInput("Potassium", value, "mmol/L", new BigDecimal("3.5"), new BigDecimal("5.1"),
                new BigDecimal("2.5"), new BigDecimal("6.5"), null);
    }

    @Test
    @DisplayName("stores the markers with flags worked out from the lab's own limits")
    void storesFlaggedMarkers() {
        var r = result();
        when(labRepo.findByIdAndTenant(TENANT, r.getId())).thenReturn(Optional.of(r));

        var out = service.saveMarkers(TENANT, r.getId(), new SaveMarkersRequest(List.of(potassium("6.8"))));

        assertThat(out.hasCritical()).isTrue();
        assertThat(out.hasAbnormal()).isTrue();
        assertThat(out.parsedMarkersJson()).contains("\"marker\":\"Potassium\"").contains("\"flag\":\"CRITICAL\"");
        assertThat(r.getMarkersEnteredAt()).isNotNull();
        verify(labRepo).save(r);
    }

    @Test
    @DisplayName("a normal result is neither abnormal nor critical")
    void normalResult() {
        var r = result();
        when(labRepo.findByIdAndTenant(TENANT, r.getId())).thenReturn(Optional.of(r));

        var out = service.saveMarkers(TENANT, r.getId(), new SaveMarkersRequest(List.of(potassium("4.2"))));

        assertThat(out.hasCritical()).isFalse();
        assertThat(out.hasAbnormal()).isFalse();
    }

    @Test
    @DisplayName("an empty list clears the markers and the flags")
    void clearsMarkers() {
        var r = result();
        when(labRepo.findByIdAndTenant(TENANT, r.getId())).thenReturn(Optional.of(r));
        service.saveMarkers(TENANT, r.getId(), new SaveMarkersRequest(List.of(potassium("6.8"))));

        var out = service.saveMarkers(TENANT, r.getId(), new SaveMarkersRequest(List.of()));

        assertThat(out.parsedMarkersJson()).isNull();
        assertThat(out.hasCritical()).isFalse();
    }

    @Test
    @DisplayName("a result that has been reviewed can no longer be changed")
    void refusesAfterReview() {
        var r = result();
        r.markReviewed(UUID.randomUUID());
        when(labRepo.findByIdAndTenant(TENANT, r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.saveMarkers(TENANT, r.getId(), new SaveMarkersRequest(List.of(potassium("4.2")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can no longer be changed");
        verify(labRepo, never()).save(any());
    }

    @Test
    @DisplayName("an unknown result is not found")
    void unknownResult() {
        var id = UUID.randomUUID();
        when(labRepo.findByIdAndTenant(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveMarkers(TENANT, id, new SaveMarkersRequest(List.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("nonsense input is refused before anything is saved")
    void refusesNonsense() {
        var r = result();
        when(labRepo.findByIdAndTenant(TENANT, r.getId())).thenReturn(Optional.of(r));
        var bad = new MarkerInput("Potassium", "4", "mmol/L", new BigDecimal("5"), new BigDecimal("3"), null, null, null);

        assertThatThrownBy(() -> service.saveMarkers(TENANT, r.getId(), new SaveMarkersRequest(List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(labRepo, never()).save(any());
    }
}
