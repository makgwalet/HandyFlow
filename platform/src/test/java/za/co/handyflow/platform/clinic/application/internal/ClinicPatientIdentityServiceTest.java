package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicPatientIdentityServiceTest {

    @Mock ClinicPatientRepository patientRepo;
    @Mock JdbcTemplate            jdbc;

    @InjectMocks ClinicPatientIdentityService service;

    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = TenantId.of(TENANT_UUID);
    }

    private ClinicPatient existing() {
        return ClinicPatient.create(TENANT, "Sipho", "Nkosi", "8001015009087",
                LocalDate.of(1980, 1, 1), "MALE", null, null, null, null);
    }

    @Test
    @DisplayName("a malformed 13-digit ID is rejected before any lookup")
    void rejectsBadChecksum() {
        assertThatThrownBy(() -> service.assertCanRegister(TENANT, "8001015009088", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("check digit");
        verifyNoInteractions(patientRepo);
    }

    @Test
    @DisplayName("a date of birth that disagrees with the ID is rejected")
    void rejectsDobMismatch() {
        assertThatThrownBy(() -> service.assertCanRegister(TENANT, "8001015009087", LocalDate.of(1981, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("an ID already held by an active patient is a conflict")
    void rejectsDuplicateId() {
        when(patientRepo.findActiveByIdNumber(TENANT, "8001015009087")).thenReturn(List.of(existing()));

        assertThatThrownBy(() -> service.assertCanRegister(TENANT, "8001015009087", LocalDate.of(1980, 1, 1)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Sipho Nkosi");
    }

    @Test
    @DisplayName("a valid new ID, a passport number and a blank ID all pass")
    void acceptsNewAndNonSaIds() {
        when(patientRepo.findActiveByIdNumber(TENANT, "8001015009087")).thenReturn(List.of());
        when(patientRepo.findActiveByIdNumber(TENANT, "A1234567")).thenReturn(List.of());

        assertThatCode(() -> {
            service.assertCanRegister(TENANT, "8001015009087", null);
            service.assertCanRegister(TENANT, "A1234567", null);
            service.assertCanRegister(TENANT, "  ", null);
            service.assertCanRegister(TENANT, null, null);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("candidates list an ID match once even when name and birth date also match")
    void candidatesAreNotRepeated() {
        ClinicPatient p = existing();
        when(patientRepo.findActiveByIdNumber(TENANT, "8001015009087")).thenReturn(List.of(p));
        when(patientRepo.findActiveByNameAndDob(TENANT, "Sipho", "Nkosi", LocalDate.of(1980, 1, 1))).thenReturn(List.of(p));

        var out = service.findCandidates(TENANT, "8001015009087", "Sipho", "Nkosi", LocalDate.of(1980, 1, 1));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).matchReason()).isEqualTo("Same ID number");
    }

    @Test
    @DisplayName("patient numbers are zero-padded")
    void formatsNumber() {
        assertThat(ClinicPatientIdentityService.format(7)).isEqualTo("P000007");
        assertThat(ClinicPatientIdentityService.format(1234567)).isEqualTo("P1234567");
    }
}
