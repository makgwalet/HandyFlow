package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRequirement;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRequirementRepository;
import za.co.handyflow.platform.compliancetender.dto.CreateComplianceRequirementRequest;
import za.co.handyflow.platform.compliancetender.dto.UpdateComplianceRequirementRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceRequirementServiceTest {

    @Mock
    private ComplianceRequirementRepository requirementRepository;

    private ComplianceRequirementService service() {
        return new ComplianceRequirementService(requirementRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() succeeds for a code that doesn't exist yet")
    void create_newCode_succeeds() {
        when(requirementRepository.findLatestByCode(TENANT, "CSD_ACTIVE")).thenReturn(Optional.empty());

        var req = new CreateComplianceRequirementRequest("csd_active", "Valid CSD Registration",
                "Government Tender", "CSD Registration Report", true);
        var response = service().create(TENANT, req, USER);

        assertThat(response.code()).isEqualTo("CSD_ACTIVE"); // uppercased
        assertThat(response.requirementVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("create() rejects a code that already exists, pointing to the new-version endpoint")
    void create_duplicateCode_rejectsWithClearMessage() {
        ComplianceRequirement existing = ComplianceRequirement.create(TENANT, "CSD_ACTIVE", "Valid CSD Registration",
                null, null, true, USER);
        when(requirementRepository.findLatestByCode(TENANT, "CSD_ACTIVE")).thenReturn(Optional.of(existing));

        var req = new CreateComplianceRequirementRequest("CSD_ACTIVE", "Duplicate attempt", null, null, true);

        assertThatThrownBy(() -> service().create(TENANT, req, USER))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("new version");
    }

    @Test
    @DisplayName("getRequirements returns only the latest version per code, not every historical row")
    void getRequirements_dedupesTolatestVersionPerCode() {
        ComplianceRequirement v1 = ComplianceRequirement.create(TENANT, "CSD_ACTIVE", "Valid CSD Registration (old)",
                null, null, true, USER);
        ComplianceRequirement v2 = v1.newVersion("Valid CSD Registration (updated)", null, null, true, USER);
        ComplianceRequirement otherCode = ComplianceRequirement.create(TENANT, "CIDB_GRADE", "cidb Grading",
                null, null, true, USER);

        when(requirementRepository.findAllForTenant(TENANT)).thenReturn(List.of(v1, v2, otherCode));

        var results = service().getRequirements(TENANT);

        assertThat(results).hasSize(2); // 2 distinct codes, not 3 rows
        var csd = results.stream().filter(r -> r.code().equals("CSD_ACTIVE")).findFirst().orElseThrow();
        assertThat(csd.requirementVersion()).isEqualTo(2);
        assertThat(csd.name()).isEqualTo("Valid CSD Registration (updated)");
    }

    @Test
    @DisplayName("createNewVersion increments the version and leaves the original untouched")
    void createNewVersion_incrementsVersion() {
        ComplianceRequirement v1 = ComplianceRequirement.create(TENANT, "CSD_ACTIVE", "Valid CSD Registration",
                null, null, true, USER);
        when(requirementRepository.findByIdForTenant(TENANT, v1.getId())).thenReturn(Optional.of(v1));
        when(requirementRepository.findLatestByCode(TENANT, "CSD_ACTIVE")).thenReturn(Optional.of(v1));

        var req = new UpdateComplianceRequirementRequest("Valid CSD Registration (2026 rules)", "Government Tender", null, true);
        var response = service().createNewVersion(TENANT, v1.getId(), req, USER);

        assertThat(response.requirementVersion()).isEqualTo(2);
        assertThat(response.name()).isEqualTo("Valid CSD Registration (2026 rules)");
        assertThat(v1.getRequirementVersion()).isEqualTo(1); // original untouched
    }

    @Test
    @DisplayName("createNewVersion rejects a stale view -- someone else already created a newer version")
    void createNewVersion_staleView_rejects() {
        ComplianceRequirement v1 = ComplianceRequirement.create(TENANT, "CSD_ACTIVE", "Valid CSD Registration",
                null, null, true, USER);
        ComplianceRequirement v2 = v1.newVersion("Someone else's update", null, null, true, USER);

        when(requirementRepository.findByIdForTenant(TENANT, v1.getId())).thenReturn(Optional.of(v1));
        when(requirementRepository.findLatestByCode(TENANT, "CSD_ACTIVE")).thenReturn(Optional.of(v2));

        var req = new UpdateComplianceRequirementRequest("My update", null, null, true);

        assertThatThrownBy(() -> service().createNewVersion(TENANT, v1.getId(), req, USER))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("latest");
    }
}
