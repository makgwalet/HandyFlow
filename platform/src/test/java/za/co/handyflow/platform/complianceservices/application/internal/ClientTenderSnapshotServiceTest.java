package za.co.handyflow.platform.complianceservices.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPersonnelRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderSubmissionSnapshotRepository;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for ClientTenderSnapshotService. Same real
 * serialize/deserialize round trip discipline as
 * compliancetender.TenderSnapshotServiceTest — a bare
 * `new ObjectMapper()` doesn't handle Instant/LocalDate without
 * JavaTimeModule registered, confirmed there against PopiaExportService's
 * own construction; the same fix applies here.
 */
@ExtendWith(MockitoExtension.class)
class ClientTenderSnapshotServiceTest {

    @Mock private ClientTenderRepository tenderRepository;
    @Mock private ClientTenderRequirementRepository requirementRepository;
    @Mock private ClientTenderPersonnelRepository personnelRepository;
    @Mock private ClientTenderSubmissionSnapshotRepository snapshotRepository;
    @Mock private HrFacade hrFacade;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ClientTenderSnapshotService service() {
        return new ClientTenderSnapshotService(tenderRepository, requirementRepository, personnelRepository,
                snapshotRepository, hrFacade, objectMapper);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("captureSnapshot round-trips through real JSON serialization correctly")
    void captureSnapshot_serializesAndReturnsCorrectData() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();

        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Municipal Road Upgrade",
                "City of Cape Town", null, null, null, null, null, "Construction", null, USER);
        ClientTenderRequirement requirement = ClientTenderRequirement.create(TENANT, tenderId, null,
                "Valid CSD registration", "COMPLIANCE", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(requirement));
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.snapshotNumber()).isEqualTo(1);
        assertThat(response.data().name()).isEqualTo("Municipal Road Upgrade");
        assertThat(response.data().clientId()).isEqualTo(clientId);
        assertThat(response.data().requirements()).hasSize(1);
        assertThat(response.data().requirements().get(0).description()).isEqualTo("Valid CSD registration");
    }

    @Test
    @DisplayName("a second submission gets snapshotNumber 2, not overwriting the first")
    void captureSnapshot_secondSubmission_incrementsNumber() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(1L);

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.snapshotNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("captureSnapshot bakes in the employee's current name at capture time -- the one deliberate copy in this module")
    void captureSnapshot_capturesPersonnelWithBakedInName() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Municipal Road Upgrade",
                null, null, null, null, null, null, null, null, USER);
        ClientTenderPersonnel personnel = ClientTenderPersonnel.create(TENANT, tenderId, employeeId, "Project Manager", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.of(
                new EmployeeResponse(employeeId, "EMP-001", "Thabo", "Mokoena", "Thabo Mokoena", null, null, null,
                        null, null, null, null, "PERMANENT", "Project Manager", null, null, null, "ACTIVE",
                        null, null, null, null, null, null, null, null)));
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.data().personnel()).hasSize(1);
        assertThat(response.data().personnel().get(0).employeeFullName()).isEqualTo("Thabo Mokoena");
        assertThat(response.data().personnel().get(0).role()).isEqualTo("Project Manager");
    }
}
