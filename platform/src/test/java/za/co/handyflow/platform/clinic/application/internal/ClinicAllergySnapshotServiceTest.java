package za.co.handyflow.platform.clinic.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientAllergy;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientAllergyRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicAllergySnapshotServiceTest {

    @Mock ClinicPatientAllergyRepository allergyRepo;
    @Mock ClinicConsultationRepository consultationRepo;
    @Mock JdbcTemplate jdbc;

    ClinicAllergySnapshotService service;
    TenantId tenant;

    @BeforeEach
    void setUp() {
        tenant = Mockito.mock(TenantId.class, withSettings().lenient());
        lenient().when(tenant.getValue()).thenReturn(UUID.randomUUID());
        service = new ClinicAllergySnapshotService(allergyRepo, consultationRepo, jdbc, new ObjectMapper());
    }

    private ClinicPatientAllergy allergy(String name, String severity) {
        return ClinicPatientAllergy.create(tenant, UUID.randomUUID(), name, "DRUG", "Rash", severity, null, null);
    }

    @Test
    @DisplayName("capture stores only the ACTIVE allergies, and only when nothing was captured yet")
    void captureStoresActiveOnly() {
        var pid = UUID.randomUUID(); var cid = UUID.randomUUID();
        var active = allergy("Penicillin", "SEVERE");
        var resolved = allergy("Latex", "MILD");
        resolved.update(null, null, null, "RESOLVED", null);
        when(allergyRepo.findByPatient(tenant, pid)).thenReturn(List.of(active, resolved));

        service.capture(tenant, cid, pid);

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("allergy_snapshot IS NULL");
        String doc = String.valueOf(args.getValue()[0]);
        assertThat(doc).contains("Penicillin").doesNotContain("Latex");
    }

    @Test
    @DisplayName("capture with no allergies still stores an empty list, which means none were recorded")
    void captureEmptyList() {
        var pid = UUID.randomUUID();
        when(allergyRepo.findByPatient(tenant, pid)).thenReturn(List.of());

        service.capture(tenant, UUID.randomUUID(), pid);

        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), args.capture());
        assertThat(String.valueOf(args.getValue()[0])).contains("\"items\":[]");
    }

    @Test
    @DisplayName("get reports not captured for older consultations")
    void getNotCaptured() {
        var cid = UUID.randomUUID();
        when(consultationRepo.findActiveById(tenant, cid)).thenReturn(Optional.of(
                ClinicConsultation.create(tenant, UUID.randomUUID(), null, null, "x")));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Arrays.asList((Object) null));

        assertThat(service.get(tenant, cid).captured()).isFalse();
    }

    @Test
    @DisplayName("get reads back what was captured")
    @SuppressWarnings("unchecked")
    void getCaptured() {
        var cid = UUID.randomUUID();
        when(consultationRepo.findActiveById(tenant, cid)).thenReturn(Optional.of(
                ClinicConsultation.create(tenant, UUID.randomUUID(), null, null, "x")));
        List<Object> rows = new ArrayList<>();
        rows.add("{\"capturedAt\":\"2026-10-07T10:00:00Z\",\"items\":[{\"allergen\":\"Penicillin\",\"allergenType\":\"DRUG\",\"severity\":\"SEVERE\",\"reaction\":\"Rash\"}]}");
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(rows);

        var r = service.get(tenant, cid);

        assertThat(r.captured()).isTrue();
        assertThat(r.capturedAt()).isEqualTo("2026-10-07T10:00:00Z");
        assertThat(r.items()).extracting(i -> i.allergen()).containsExactly("Penicillin");
    }

    @Test
    @DisplayName("get on an unknown consultation is not found")
    void getUnknown() {
        var cid = UUID.randomUUID();
        when(consultationRepo.findActiveById(tenant, cid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(tenant, cid)).isInstanceOf(ResourceNotFoundException.class);
    }
}
