package za.co.handyflow.platform.compliancetender.application.internal;

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
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regression test for TenderPdfService — the tender document builder.
 * Generates a real PDF (loads the actual embedded fonts from the
 * classpath, same as production) rather than mocking iText away, so this
 * confirms the fonts genuinely load and the document genuinely closes
 * without throwing, not just that the code compiles.
 */
@ExtendWith(MockitoExtension.class)
class TenderPdfServiceTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderRequirementRepository requirementRepository;
    @Mock private TenderPersonnelRepository personnelRepository;
    @Mock private TenantFacade tenantFacade;
    @Mock private HrFacade hrFacade;

    private TenderPdfService service() {
        return new TenderPdfService(tenderRepository, requirementRepository, personnelRepository, tenantFacade, hrFacade);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static TenantDetails tenantDetails(String companyName) {
        return new TenantDetails(UUID.randomUUID(), companyName, "zeta", null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("generates a real, non-empty PDF for a tender with requirements and personnel")
    void generatesRealPdf_withData() {
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Tender tender = Tender.create(TENANT, "TND-00001", "Municipal Road Upgrade", "City of Cape Town",
                "CCT-2026-045", null, null, null, new BigDecimal("12500000"), "Construction",
                "cidb Grade 6GB", USER);
        TenderRequirement requirement = TenderRequirement.create(TENANT, tenderId, null,
                "Valid CSD registration", "COMPLIANCE", USER);
        TenderPersonnel personnel = TenderPersonnel.create(TENANT, tenderId, employeeId, "Project Manager", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(requirement));
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(tenantFacade.findTenantDetails(TENANT)).thenReturn(Optional.of(tenantDetails("Zeta Earthmoving (Pty) Ltd")));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty()); // exercise the "no longer available" path too

        byte[] pdf = service().generateTenderSummaryPdf(TENANT, tenderId);

        assertThat(pdf).isNotEmpty();
        // A real PDF file always starts with this exact magic header
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("generates a real PDF even with no requirements or personnel yet (empty-state handling)")
    void generatesRealPdf_emptyState() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00002", "New Tender", null, null,
                null, null, null, null, null, null, USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(tenantFacade.findTenantDetails(TENANT)).thenReturn(Optional.of(tenantDetails("Zeta Earthmoving (Pty) Ltd")));

        byte[] pdf = service().generateTenderSummaryPdf(TENANT, tenderId);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("throws ResourceNotFoundException for a tender that doesn't belong to this tenant")
    void unknownTender_throws() {
        UUID tenderId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().generateTenderSummaryPdf(TENANT, tenderId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("throws ResourceNotFoundException if the tenant's own details can't be resolved")
    void missingTenantDetails_throws() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00003", "Test Tender", null, null,
                null, null, null, null, null, null, USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(tenantFacade.findTenantDetails(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().generateTenderSummaryPdf(TENANT, tenderId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
