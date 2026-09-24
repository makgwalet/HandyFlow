package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderRequirement;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPersonnelRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
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
 * Regression test for ClientTenderPdfService. Generates a real PDF (loads
 * the actual embedded fonts from the classpath, same as production)
 * rather than mocking iText away, same discipline as
 * compliancetender.TenderPdfServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class ClientTenderPdfServiceTest {

    @Mock private ClientTenderRepository tenderRepository;
    @Mock private ClientTenderRequirementRepository requirementRepository;
    @Mock private ClientTenderPersonnelRepository personnelRepository;
    @Mock private ComplianceClientRepository clientRepository;
    @Mock private TenantFacade tenantFacade;
    @Mock private HrFacade hrFacade;

    private ClientTenderPdfService service() {
        return new ClientTenderPdfService(tenderRepository, requirementRepository, personnelRepository,
                clientRepository, tenantFacade, hrFacade);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static TenantDetails tenantDetails(String companyName) {
        return new TenantDetails(UUID.randomUUID(), companyName, "zeta", null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("generates a real, non-empty PDF for a client tender with requirements and personnel")
    void generatesRealPdf_withData() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Municipal Road Upgrade",
                "City of Cape Town", "CCT-2026-045", null, null, null, new BigDecimal("12500000"),
                "Construction", "cidb Grade 6GB", USER);
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        ClientTenderRequirement requirement = ClientTenderRequirement.create(TENANT, tenderId, null,
                "Valid CSD registration", "COMPLIANCE", USER);
        ClientTenderPersonnel personnel = ClientTenderPersonnel.create(TENANT, tenderId, employeeId, "Project Manager", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(requirement));
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(tenantFacade.findTenantDetails(TENANT)).thenReturn(Optional.of(tenantDetails("Zeta Compliance Consultants")));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty());

        byte[] pdf = service().generateTenderSummaryPdf(TENANT, tenderId);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("generates a real PDF even with no requirements or personnel yet")
    void generatesRealPdf_emptyState() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00002", "New Tender", null, null,
                null, null, null, null, null, null, USER);
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(tenantFacade.findTenantDetails(TENANT)).thenReturn(Optional.of(tenantDetails("Zeta Compliance Consultants")));

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
    @DisplayName("throws ResourceNotFoundException if the tender's client can no longer be found")
    void missingClient_throws() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00003", "Test Tender", null, null,
                null, null, null, null, null, null, USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().generateTenderSummaryPdf(TENANT, tenderId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
