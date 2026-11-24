package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientFieldSource;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientFieldSourceRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientProfileDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceClientProfileServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceClientFieldSourceRepository sources;

    private ComplianceClientProfileService service;
    private ComplianceClient client;

    @BeforeEach
    void setUp() {
        service = new ComplianceClientProfileService(clients, sources);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        when(sources.findForClient(eq(TENANT), eq(client.getId()))).thenReturn(List.of());
    }

    private static SaveProfileRequest req(String reg, String vat, Integer emp) {
        return new SaveProfileRequest("Acme (Pty) Ltd", null, reg, "PTY_LTD", "9012345678", vat, null, null, null, emp, null, null, null);
    }

    @Test @DisplayName("a registration number already on another client is refused")
    void duplicateRegistration() {
        ComplianceClient other = ComplianceClient.create(TENANT, "Other", null, null, null, null, USER);
        when(clients.findByRegistrationNumber(TENANT, "2015/123456/07")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.save(TENANT, client.getId(), req("2015/123456/07", null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already on file");
        verify(clients, never()).save(any());
    }

    @Test @DisplayName("the client's own registration number is not a duplicate")
    void ownRegistration() {
        when(clients.findByRegistrationNumber(TENANT, "2015/123456/07")).thenReturn(Optional.of(client));
        when(sources.findForField(eq(TENANT), eq(client.getId()), any())).thenReturn(Optional.empty());
        service.save(TENANT, client.getId(), req("2015/123456/07", null, null), USER);
        verify(clients).save(client);
    }

    @Test @DisplayName("a bad VAT number becomes a 400 and nothing is saved")
    void badVat() {
        assertThatThrownBy(() -> service.save(TENANT, client.getId(), req(null, "9012345678", null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("starting with 4");
        verify(clients, never()).save(any());
    }

    @Test @DisplayName("each newly filled fact gets a staff-entered source record")
    void recordsSources() {
        when(clients.findByRegistrationNumber(any(), any())).thenReturn(Optional.empty());
        when(sources.findForField(eq(TENANT), eq(client.getId()), any())).thenReturn(Optional.empty());
        service.save(TENANT, client.getId(), req("2015/123456/07", "4123456789", 5), USER);
        ArgumentCaptor<ComplianceClientFieldSource> cap = ArgumentCaptor.forClass(ComplianceClientFieldSource.class);
        verify(sources, org.mockito.Mockito.times(6)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(ComplianceClientFieldSource::getFieldName)
                .containsExactlyInAnyOrder("legalName", "registrationNumber", "entityType", "taxNumber", "vatNumber", "employeeCount");
        assertThat(cap.getAllValues()).allMatch(x -> x.getSource().equals("STAFF_ENTERED") && !x.isVerified());
    }

    @Test @DisplayName("changing a confirmed fact starts it again as unconfirmed")
    void changeResetsConfirmation() {
        when(clients.findByRegistrationNumber(any(), any())).thenReturn(Optional.empty());
        when(sources.findForField(eq(TENANT), eq(client.getId()), any())).thenReturn(Optional.empty());
        service.save(TENANT, client.getId(), req(null, null, 5), USER);
        ComplianceClientFieldSource confirmed = ComplianceClientFieldSource.record(TENANT, client.getId(), "employeeCount",
                "DOCUMENT", null, null, null, USER);
        confirmed.confirm(UUID.randomUUID(), null);
        when(sources.findForField(TENANT, client.getId(), "employeeCount")).thenReturn(Optional.of(confirmed));
        service.save(TENANT, client.getId(), req(null, null, 9), USER);
        assertThat(confirmed.isVerified()).isFalse();
        assertThat(confirmed.getSource()).isEqualTo("STAFF_ENTERED");
    }

    @Test @DisplayName("clearing a fact removes its source record")
    void clearingDeletesSource() {
        when(clients.findByRegistrationNumber(any(), any())).thenReturn(Optional.empty());
        when(sources.findForField(eq(TENANT), eq(client.getId()), any())).thenReturn(Optional.empty());
        service.save(TENANT, client.getId(), req(null, null, 5), USER);
        ComplianceClientFieldSource row = ComplianceClientFieldSource.record(TENANT, client.getId(), "employeeCount",
                "STAFF_ENTERED", null, null, null, USER);
        when(sources.findForField(TENANT, client.getId(), "employeeCount")).thenReturn(Optional.of(row));
        service.save(TENANT, client.getId(), req(null, null, null), USER);
        verify(sources).delete(row);
    }

    @Test @DisplayName("a source cannot be recorded for a fact that is empty")
    void sourceForEmptyFact() {
        assertThatThrownBy(() -> service.saveSource(TENANT, client.getId(), "vatNumber",
                new SaveSourceRequest("DOCUMENT", null, null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no vatNumber");
    }

    @Test @DisplayName("an unknown field name is refused")
    void unknownField() {
        assertThatThrownBy(() -> service.confirm(TENANT, client.getId(), "shoeSize", null, USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Unknown profile field");
    }

    @Test @DisplayName("confirming your own entry without a reason is a 400, with a reason it works")
    void selfConfirm() {
        ComplianceClientFieldSource row = ComplianceClientFieldSource.record(TENANT, client.getId(), "employeeCount",
                "STAFF_ENTERED", null, null, null, USER);
        when(sources.findForField(TENANT, client.getId(), "employeeCount")).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> service.confirm(TENANT, client.getId(), "employeeCount", null, USER))
                .isInstanceOf(HandyFlowException.class);
        service.confirm(TENANT, client.getId(), "employeeCount", "Only one person works in this practice", USER);
        assertThat(row.isVerified()).isTrue();
        verify(sources).save(row);
    }
}
