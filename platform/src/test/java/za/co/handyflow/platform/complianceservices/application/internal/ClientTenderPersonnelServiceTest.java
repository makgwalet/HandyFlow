package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPersonnel;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderPersonnelRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.dto.AddClientTenderPersonnelRequest;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.hr.dto.EmployeeResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regression test for ClientTenderPersonnelService — mirrors
 * compliancetender.TenderPersonnelServiceTest exactly: confirms both
 * halves of the reference-not-copy design (write-time existence check,
 * read-time tolerance for a since-deleted employee), scoped to a
 * client-tender in addition to a tender.
 */
@ExtendWith(MockitoExtension.class)
class ClientTenderPersonnelServiceTest {

    @Mock private ClientTenderPersonnelRepository personnelRepository;
    @Mock private ClientTenderRepository tenderRepository;
    @Mock private HrFacade hrFacade;

    private ClientTenderPersonnelService service() {
        return new ClientTenderPersonnelService(personnelRepository, tenderRepository, hrFacade);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static EmployeeResponse employee(UUID id, String fullName, String employeeNumber) {
        return new EmployeeResponse(id, employeeNumber, "Jane", "Doe", fullName, null, null, null,
                null, null, "jane@example.com", null, "PERMANENT", "Project Manager", null,
                null, null, "ACTIVE", null, null, null, null, null, null, null, null);
    }

    private ClientTender sampleTender(UUID clientId) {
        return ClientTender.create(TENANT, clientId, "CTND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
    }

    @Test
    @DisplayName("addPersonnel rejects an employeeId that doesn't exist in this tenant's HR")
    void addPersonnel_unknownEmployee_throws() {
        UUID tenderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(sampleTender(clientId)));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty());

        var req = new AddClientTenderPersonnelRequest(employeeId, "Project Manager");

        assertThatThrownBy(() -> service().addPersonnel(TENANT, tenderId, req, USER))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    @DisplayName("addPersonnel rejects a client tender that doesn't belong to this tenant")
    void addPersonnel_unknownTender_throws() {
        UUID tenderId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.empty());

        var req = new AddClientTenderPersonnelRequest(UUID.randomUUID(), "Project Manager");

        assertThatThrownBy(() -> service().addPersonnel(TENANT, tenderId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("addPersonnel succeeds and returns the employee's current HR details, not a copy")
    void addPersonnel_validEmployee_returnsEnrichedResponse() {
        UUID tenderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(sampleTender(clientId)));
        when(hrFacade.findEmployeeById(TENANT, employeeId))
                .thenReturn(Optional.of(employee(employeeId, "Thabo Mokoena", "EMP-0042")));

        var req = new AddClientTenderPersonnelRequest(employeeId, "Project Manager");
        var response = service().addPersonnel(TENANT, tenderId, req, USER);

        assertThat(response.employeeFound()).isTrue();
        assertThat(response.employeeFullName()).isEqualTo("Thabo Mokoena");
        assertThat(response.employeeNumber()).isEqualTo("EMP-0042");
    }

    @Test
    @DisplayName("getPersonnel returns employeeFound=false, not an exception, for a since-deleted employee")
    void getPersonnel_deletedEmployee_returnsNotFoundFlagRatherThanThrowing() {
        UUID tenderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        ClientTenderPersonnel personnel = ClientTenderPersonnel.create(TENANT, tenderId, employeeId, "Site Agent", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(sampleTender(clientId)));
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty());

        var results = service().getPersonnel(TENANT, tenderId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).employeeFound()).isFalse();
        assertThat(results.get(0).role()).isEqualTo("Site Agent");
    }
}
