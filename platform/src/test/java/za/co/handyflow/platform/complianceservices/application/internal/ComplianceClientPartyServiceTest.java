package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientParty;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClientSite;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientPartyRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientSiteRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientPartyDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceClientPartyServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceClientPartyRepository parties;
    @Mock ComplianceClientSiteRepository sites;

    private ComplianceClientPartyService service;
    private UUID clientId;

    @BeforeEach
    void setUp() {
        service = new ComplianceClientPartyService(clients, parties, sites);
        ComplianceClient c = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        clientId = c.getId();
        when(clients.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(c));
    }

    private ComplianceClientParty holder(String name, String idNumber, String role, String pct) {
        return ComplianceClientParty.create(TENANT, clientId, new ComplianceClientParty.Details(name, null, "PASSPORT", idNumber, role,
                pct == null ? null : new BigDecimal(pct), null, null, null), USER);
    }

    private static SavePartyRequest req(String name, String idNumber, String role, String pct) {
        return new SavePartyRequest(name, null, "PASSPORT", idNumber, role, pct == null ? null : new BigDecimal(pct), null, null, null);
    }

    @Test @DisplayName("an unknown client is not found")
    void unknownClient() {
        UUID other = UUID.randomUUID();
        when(clients.findByIdForTenant(TENANT, other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.parties(TENANT, other)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("the same person cannot hold the same role twice")
    void duplicateRole() {
        when(parties.findForClient(TENANT, clientId)).thenReturn(List.of(holder("A", "P1", "DIRECTOR", null)));
        assertThatThrownBy(() -> service.addParty(TENANT, clientId, req("A again", "P1", "DIRECTOR", null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already recorded");
        verify(parties, never()).save(any());
    }

    @Test @DisplayName("the same person can be a director and a shareholder")
    void differentRoles() {
        when(parties.findForClient(TENANT, clientId)).thenReturn(List.of(holder("A", "P1", "DIRECTOR", null)));
        service.addParty(TENANT, clientId, req("A", "P1", "SHAREHOLDER", "30"), USER);
        verify(parties).save(any());
    }

    @Test @DisplayName("shareholdings cannot add up to more than 100%")
    void over100() {
        when(parties.findForClient(TENANT, clientId)).thenReturn(List.of(holder("A", "P1", "SHAREHOLDER", "70")));
        assertThatThrownBy(() -> service.addParty(TENANT, clientId, req("B", "P2", "SHAREHOLDER", "40"), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("more than 100%");
        service.addParty(TENANT, clientId, req("B", "P2", "SHAREHOLDER", "30"), USER);
        verify(parties).save(any());
    }

    @Test @DisplayName("a former shareholder's share does not count")
    void formerHolderIgnored() {
        ComplianceClientParty gone = holder("A", "P1", "SHAREHOLDER", "70");
        gone.resign(null, USER);
        when(parties.findForClient(TENANT, clientId)).thenReturn(List.of(gone));
        service.addParty(TENANT, clientId, req("B", "P2", "SHAREHOLDER", "100"), USER);
        verify(parties).save(any());
    }

    @Test @DisplayName("a second head office is refused, a branch is fine")
    void headOffice() {
        ComplianceClientSite head = ComplianceClientSite.create(TENANT, clientId, "Head", "HEAD_OFFICE", null, null, USER);
        when(sites.findForClient(TENANT, clientId)).thenReturn(List.of(head));
        assertThatThrownBy(() -> service.addSite(TENANT, clientId, new SaveSiteRequest("Other", "HEAD_OFFICE", null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("head office");
        service.addSite(TENANT, clientId, new SaveSiteRequest("Branch", "BRANCH", null, null), USER);
        verify(sites).save(any());
    }

    @Test @DisplayName("a bad ID number becomes a 400")
    void badId() {
        assertThatThrownBy(() -> service.addParty(TENANT, clientId,
                new SavePartyRequest("A", null, "SA_ID", "1234567890123", "DIRECTOR", null, null, null, null), USER))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not valid");
    }
}
