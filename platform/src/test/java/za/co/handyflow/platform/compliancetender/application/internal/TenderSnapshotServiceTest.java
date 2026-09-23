package za.co.handyflow.platform.compliancetender.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPersonnel;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRequirement;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPersonnelRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderSubmissionSnapshotRepository;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for TenderSnapshotService — confirms the actual
 * serialize/deserialize round trip works (a plain `new ObjectMapper()`
 * without JavaTimeModule registered would fail on this record's Instant/
 * LocalDate fields — same gotcha PopiaExportService's own ObjectMapper
 * construction already works around; Spring's auto-configured bean
 * registers this automatically in the real running app, so the test
 * mirrors that rather than assuming a bare ObjectMapper is equivalent),
 * and that personnel snapshots bake in the employee's name at capture
 * time rather than leaving it as a live reference.
 */
@ExtendWith(MockitoExtension.class)
class TenderSnapshotServiceTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderRequirementRepository requirementRepository;
    @Mock private TenderPersonnelRepository personnelRepository;
    @Mock private TenderSubmissionSnapshotRepository snapshotRepository;
    @Mock private HrFacade hrFacade;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private TenderSnapshotService service() {
        return new TenderSnapshotService(tenderRepository, requirementRepository, personnelRepository,
                snapshotRepository, hrFacade, objectMapper);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static EmployeeResponse employee(UUID id, String fullName) {
        return new EmployeeResponse(id, "EMP-001", "Thabo", "Mokoena", fullName, null, null, null,
                null, null, null, null, "PERMANENT", "Project Manager", null, null, null, "ACTIVE",
                null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("captureSnapshot round-trips through real JSON serialization correctly")
    void captureSnapshot_serializesAndReturnsCorrectData() {
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Tender tender = Tender.create(TENANT, "TND-00001", "Municipal Road Upgrade", "City of Cape Town",
                null, null, null, null, null, "Construction", null, USER);
        TenderRequirement requirement = TenderRequirement.create(TENANT, tenderId, null,
                "Valid CSD registration", "COMPLIANCE", USER);
        TenderPersonnel personnel = TenderPersonnel.create(TENANT, tenderId, employeeId, "Project Manager", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(requirement));
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.of(employee(employeeId, "Thabo Mokoena")));
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.snapshotNumber()).isEqualTo(1);
        assertThat(response.data().name()).isEqualTo("Municipal Road Upgrade");
        assertThat(response.data().requirements()).hasSize(1);
        assertThat(response.data().requirements().get(0).description()).isEqualTo("Valid CSD registration");
        assertThat(response.data().personnel()).hasSize(1);
        assertThat(response.data().personnel().get(0).employeeFullName()).isEqualTo("Thabo Mokoena");
    }

    @Test
    @DisplayName("a second submission of the same tender gets snapshotNumber 2, not overwriting the first")
    void captureSnapshot_secondSubmission_incrementsNumber() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(1L); // one already exists

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.snapshotNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("a personnel reference to a since-deleted employee still produces a snapshot, with a clear placeholder")
    void captureSnapshot_deletedEmployee_usesPlaceholderRatherThanFailing() {
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        TenderPersonnel personnel = TenderPersonnel.create(TENANT, tenderId, employeeId, "Site Agent", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.data().personnel().get(0).employeeFullName())
                .isEqualTo("(employee record no longer available)");
    }
}
