package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceRegistrationRequest;
import za.co.handyflow.platform.compliancetender.dto.UpdateComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceRegistrationServiceTest {

    @Mock
    private ComplianceRegistrationRepository registrationRepository;

    private ComplianceRegistrationService service() {
        return new ComplianceRegistrationService(registrationRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() saves and returns a response reflecting the request")
    void create_savesAndReturnsResponse() {
        var req = new CreateComplianceRegistrationRequest("PSIRA", "Business Registration",
                "1234567", LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1), "Renewed early");

        var response = service().create(TENANT, req, USER);

        assertThat(response.authority()).isEqualTo("PSIRA");
        assertThat(response.registrationType()).isEqualTo("Business Registration");
        assertThat(response.registrationNumber()).isEqualTo("1234567");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getRegistration() throws ResourceNotFoundException for an unknown id")
    void getRegistration_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getRegistration(TENANT, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update() applies the new fields onto the existing registration")
    void update_appliesNewFields() {
        UUID id = UUID.randomUUID();
        ComplianceRegistration existing = ComplianceRegistration.create(TENANT, "CIDB", "Grade 4GB",
                "OLD-REF", LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), null, USER);
        when(registrationRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.of(existing));

        var req = new UpdateComplianceRegistrationRequest("NEW-REF", "ACTIVE",
                LocalDate.of(2023, 1, 1), LocalDate.of(2028, 1, 1), "Upgraded");

        var response = service().update(TENANT, id, req, USER);

        assertThat(response.registrationNumber()).isEqualTo("NEW-REF");
        assertThat(response.expiryDate()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(response.notes()).isEqualTo("Upgraded");
    }

    @Test
    @DisplayName("delete() throws ResourceNotFoundException for an unknown id rather than silently doing nothing")
    void delete_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(registrationRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(TENANT, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
