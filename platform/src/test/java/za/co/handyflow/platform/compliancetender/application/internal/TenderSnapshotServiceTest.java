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

import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

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
    @Mock private TenderPricingService pricingService;
    @Mock private za.co.handyflow.platform.compliancetender.domain.repository.TenderPackageRepository packageRepository;
    @Mock private za.co.handyflow.platform.compliancetender.application.internal.submission.PackageInputsProvider packageInputs;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private TenderSnapshotService service() {
        return new TenderSnapshotService(tenderRepository, requirementRepository, personnelRepository,
                snapshotRepository, hrFacade, pricingService, packageRepository, packageInputs, objectMapper);
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
    @DisplayName("the price schedule is frozen into the snapshot and survives the JSON round trip with its amounts")
    void captureSnapshot_includesPricing() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        var pricing = new za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse(
                tenderId, "READY_TO_SUBMIT", true, true, null,
                new za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse.Settings(
                        new java.math.BigDecimal("10.00"), java.math.BigDecimal.ZERO, new java.math.BigDecimal("5.00"), true, new java.math.BigDecimal("15.00"), "n"),
                List.of(new za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse.LineResponse(
                        UUID.randomUUID(), "Roadworks", "1.1", "Kerbing", "m", new java.math.BigDecimal("10.000"), new java.math.BigDecimal("25.50"), new java.math.BigDecimal("255.00"), 1)),
                new za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse.Breakdown(
                        new java.math.BigDecimal("255.00"), new java.math.BigDecimal("25.50"), java.math.BigDecimal.ZERO.setScale(2), new java.math.BigDecimal("14.03"),
                        new java.math.BigDecimal("294.53"), new java.math.BigDecimal("44.18"), new java.math.BigDecimal("338.71"), new java.math.BigDecimal("4.76"), List.of()));
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);
        when(pricingService.snapshotOf(TENANT, tenderId)).thenReturn(pricing);

        var response = service().captureSnapshot(TENANT, tenderId, USER);

        assertThat(response.data().pricing()).isNotNull();
        assertThat(response.data().pricing().breakdown().priceInclVat()).isEqualByComparingTo("338.71");
        assertThat(response.data().pricing().lines()).hasSize(1);
        assertThat(response.data().pricing().settings().vatRatePct()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("a tender that was never priced snapshots with no pricing, and an old snapshot without the pricing field still reads")
    void captureSnapshot_withoutPricing() throws Exception {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);
        when(pricingService.snapshotOf(TENANT, tenderId)).thenReturn(null);

        assertThat(service().captureSnapshot(TENANT, tenderId, USER).data().pricing()).isNull();

        String old = "{\"tenderId\":\"" + tenderId + "\",\"tenderNumber\":\"T\",\"name\":\"N\",\"requirements\":[],\"personnel\":[]}";
        var parsed = objectMapper.readValue(old, za.co.handyflow.platform.compliancetender.dto.TenderSnapshotData.class);
        assertThat(parsed.pricing()).isNull();
        assertThat(parsed.name()).isEqualTo("N");
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

    @Test
    @DisplayName("two submissions racing for the same snapshot number: the loser gets a clear conflict, not a raw database error, and nothing is half done")
    void captureSnapshot_collision_isAClearConflict() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);
        when(snapshotRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> service().captureSnapshot(TENANT, tenderId, USER))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at the same moment")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the snapshot is written and flushed inside the transaction, so a collision surfaces here and not later at commit")
    void captureSnapshot_flushesTheSnapshot() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);

        service().captureSnapshot(TENANT, tenderId, USER);

        verify(snapshotRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("the snapshot points at the newest package, says whether the tender had changed since it was built, and keeps outside personnel")
    void captureSnapshot_linksPackageAndExternalPersonnel() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        var built = new za.co.handyflow.platform.compliancetender.application.internal.submission.PackageInputs("d0", "r", "p", "k");
        var nowInputs = new za.co.handyflow.platform.compliancetender.application.internal.submission.PackageInputs("d1", "r", "p", "k");
        var newest = za.co.handyflow.platform.compliancetender.domain.model.TenderPackage.create(UUID.randomUUID(), TENANT, tenderId, 3, true, false,
                null, "{}", "[]", "a".repeat(64), "TND-00001-v3.pdf", "b".repeat(64), "application/pdf", 8, 1, "key", USER, "Sam");
        newest.recordInputs(built.encode());
        var older = za.co.handyflow.platform.compliancetender.domain.model.TenderPackage.create(UUID.randomUUID(), TENANT, tenderId, 2, false, false,
                null, "{}", "[]", "c".repeat(64), "TND-00001-v2.pdf", "d".repeat(64), "application/pdf", 8, 1, "key2", USER, "Sam");

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(
                TenderPersonnel.createExternal(TENANT, tenderId, "CONSULTANT", "Dr A Naidoo", "Naidoo Geotech", "Geotechnical engineer", USER)));
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);
        when(packageRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(newest, older));
        when(packageInputs.current(TENANT, tender)).thenReturn(nowInputs);

        var data = service().captureSnapshot(TENANT, tenderId, USER).data();

        assertThat(data.submittedPackage().versionNo()).isEqualTo(3);
        assertThat(data.submittedPackage().packageHash()).isEqualTo("a".repeat(64));
        assertThat(data.submittedPackage().outOfDate()).isTrue();
        assertThat(data.submittedPackage().outOfDateReasons()).containsExactly("The tender details changed");
        assertThat(data.personnel().get(0).employeeId()).isNull();
        assertThat(data.personnel().get(0).employeeFullName()).isEqualTo("Dr A Naidoo");
        assertThat(data.personnel().get(0).personType()).isEqualTo("CONSULTANT");
    }

    @Test
    @DisplayName("a tender submitted with no package built has no package reference")
    void captureSnapshot_noPackage() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(snapshotRepository.countByTender(TENANT, tenderId)).thenReturn(0L);

        assertThat(service().captureSnapshot(TENANT, tenderId, USER).data().submittedPackage()).isNull();
    }
}
