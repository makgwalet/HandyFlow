package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PortfolioDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CompliancePortfolioServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceClientRepository clients;
    @Mock ClientComplianceDeadlineRepository deadlines;
    @Mock ClientComplianceDocumentRepository documents;
    @Mock ComplianceCaseRepository cases;
    @Mock ComplianceRegImpactRepository regImpacts;
    @Mock ComplianceAgreementRepository agreements;

    private CompliancePortfolioService service;
    private ComplianceClient acme;
    private ComplianceClient quiet;

    @BeforeEach
    void setUp() {
        service = new CompliancePortfolioService(clients, deadlines, documents, cases, regImpacts, agreements);
        acme = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        quiet = ComplianceClient.create(TENANT, "Quiet", null, null, null, null, USER);
        lenient().when(clients.findAll(eq(TENANT), eq("ACTIVE"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(acme, quiet)));
        lenient().when(deadlines.findPendingDueBy(any(), any())).thenReturn(List.of());
        lenient().when(documents.findNeedingAttention(any(), any())).thenReturn(List.of());
        lenient().when(documents.findRejectedSince(any(), any())).thenReturn(List.of());
        lenient().when(cases.findLiveForTenant(any())).thenReturn(List.of());
        lenient().when(regImpacts.findLive(any())).thenReturn(List.of());
        lenient().when(agreements.findAllForTenant(any())).thenReturn(List.of());
    }

    @Test @DisplayName("with nothing recorded, no client is flagged")
    void nothing() {
        Summary s = service.earlyWarning(TENANT, TODAY);
        assertThat(s.activeClients()).isEqualTo(2);
        assertThat(s.flaggedClients()).isEqualTo(0);
        assertThat(s.clients().isEmpty()).isTrue();
    }

    @Test @DisplayName("overdue deadlines flag the client they belong to, and only active clients are counted")
    void overdue() {
        ClientComplianceDeadline late = ClientComplianceDeadline.generated(TENANT, acme.getId(), UUID.randomUUID(), 1, "CONSULTANT", "Annual return", TODAY.minusDays(5), USER);
        ClientComplianceDeadline stranger = ClientComplianceDeadline.generated(TENANT, UUID.randomUUID(), UUID.randomUUID(), 1, "CONSULTANT", "Other", TODAY.minusDays(9), USER);
        lenient().when(deadlines.findPendingDueBy(any(), any())).thenReturn(List.of(late, stranger));
        Summary s = service.earlyWarning(TENANT, TODAY);
        assertThat(s.flaggedClients()).isEqualTo(1);
        assertThat(s.clients().get(0).clientName()).isEqualTo("Acme");
        assertThat(s.clients().get(0).signals().get(0).code()).isEqualTo("OVERDUE_DEADLINES");
        assertThat(s.clients().get(0).signals().get(0).detail().contains("oldest by 5 days")).isTrue();
    }
}
