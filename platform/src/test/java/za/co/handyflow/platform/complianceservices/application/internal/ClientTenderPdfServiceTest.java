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
    @Mock private ClientTenderPricingService pricingService;

    private ClientTenderPdfService service() {
        return new ClientTenderPdfService(tenderRepository, requirementRepository, personnelRepository,
                clientRepository, tenantFacade, hrFacade, pricingService);
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

    @Test
    @DisplayName("a logo stored as a data: URI (the only way the app stores one) is decoded")
    void decodeLogo_dataUri_isDecoded() throws Exception {
        byte[] bytes = service().decodeLogoBytes("data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));

        assertThat(bytes).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("a logo URL is NEVER fetched by the server: http, https, file and internal metadata addresses are all refused without any network call")
    void decodeLogo_urls_areRefused() {
        for (String url : new String[] {"http://169.254.169.254/latest/meta-data/", "https://example.com/logo.png", "file:///etc/passwd", "ftp://example.com/logo.png", "//example.com/logo.png"}) {
            assertThatThrownBy(() -> service().decodeLogoBytes(url)).as(url).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("never fetched");
        }
    }

    @Test
    @DisplayName("a malformed data: URI is refused")
    void decodeLogo_malformedDataUri_isRefused() {
        assertThatThrownBy(() -> service().decodeLogoBytes("data:image/png;base64")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Malformed");
    }

    private static String text(byte[] pdf) throws Exception {
        try (var doc = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(new java.io.ByteArrayInputStream(pdf)))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) sb.append(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(i))).append('\n');
            return sb.toString();
        }
    }

    private UUID pricedTender() {
        UUID clientId = UUID.randomUUID();
        UUID tenderId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00004", "Priced Tender", null, null, null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        when(clientRepository.findByIdForTenant(TENANT, tender.getClientId())).thenReturn(Optional.of(
                ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER)));
        when(requirementRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(tenantFacade.findTenantDetails(TENANT)).thenReturn(Optional.of(tenantDetails("Zeta Compliance Consultants")));
        return tenderId;
    }

    private static za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse pricing(boolean withLines) {
        return new za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse(UUID.randomUUID(), "IN_PREPARATION", true, true, null,
                new za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse.Settings(new java.math.BigDecimal("10.00"), java.math.BigDecimal.ZERO,
                        new java.math.BigDecimal("5.00"), true, new java.math.BigDecimal("15.00"), null),
                withLines ? List.of(new za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse.LineResponse(UUID.randomUUID(), "Roadworks", "1.1", "Kerbing", "m",
                        new java.math.BigDecimal("10.000"), new java.math.BigDecimal("25.50"), new java.math.BigDecimal("255.00"), 1)) : List.of(),
                new za.co.handyflow.platform.complianceservices.dto.ClientTenderPricingResponse.Breakdown(new java.math.BigDecimal("255.00"), new java.math.BigDecimal("25.50"),
                        java.math.BigDecimal.ZERO.setScale(2), new java.math.BigDecimal("14.03"), new java.math.BigDecimal("294.53"), new java.math.BigDecimal("44.18"),
                        new java.math.BigDecimal("338.71"), new java.math.BigDecimal("4.76"), List.of()));
    }

    @Test
    @DisplayName("the default summary never contains pricing, and does not even load it")
    void withoutPricing() throws Exception {
        UUID tenderId = pricedTender();
        String t = text(service().generateTenderSummaryPdf(TENANT, tenderId));
        org.assertj.core.api.Assertions.assertThat(t).doesNotContain("Price Schedule").doesNotContain("338.71");
        org.mockito.Mockito.verifyNoInteractions(pricingService);
    }

    @Test
    @DisplayName("with pricing, the summary carries the schedule, how the price is built and the internal-use warning")
    void withPricing() throws Exception {
        UUID tenderId = pricedTender();
        when(pricingService.getPricing(TENANT, tenderId)).thenReturn(pricing(true));
        String t = text(service().generateTenderSummaryPdf(TENANT, tenderId, true));
        org.assertj.core.api.Assertions.assertThat(t).contains("Price Schedule").contains("Roadworks").contains("Kerbing").contains("R 255.00")
                .contains("Overhead 10%").contains("R 338.71").contains("do not send it to the client");
    }

    @Test
    @DisplayName("with pricing but nothing priced yet, it says so")
    void withPricing_noLines() throws Exception {
        UUID tenderId = pricedTender();
        when(pricingService.getPricing(TENANT, tenderId)).thenReturn(pricing(false));
        org.assertj.core.api.Assertions.assertThat(text(service().generateTenderSummaryPdf(TENANT, tenderId, true))).contains("No pricing has been entered yet");
    }
}
