package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPersonnel;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPersonnelRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.dto.AddTenderPersonnelRequest;
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
 * Regression test for TenderPersonnelService — the first real
 * cross-module reference in compliancetender. Confirms both halves of
 * the reference-not-copy design: a write-time check that the reference
 * actually resolves (can't add a non-existent employee), and a
 * read-time tolerance for a reference that no longer resolves (the
 * employee was since removed from HR) — a tender shouldn't become
 * unreadable because of that.
 */
@ExtendWith(MockitoExtension.class)
class TenderPersonnelServiceTest {

    @Mock private TenderPersonnelRepository personnelRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private HrFacade hrFacade;

    private TenderPersonnelService service() {
        return new TenderPersonnelService(personnelRepository, tenderRepository, hrFacade);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static EmployeeResponse employee(UUID id, String fullName, String employeeNumber) {
        return new EmployeeResponse(id, employeeNumber, "Jane", "Doe", fullName, null, null, null,
                null, null, "jane@example.com", null, "PERMANENT", "Project Manager", null,
                null, null, "ACTIVE", null, null, null, null, null, null, null, null);
    }

    private Tender sampleTender() {
        return Tender.create(TENANT, "TND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
    }

    @Test
    @DisplayName("addPersonnel rejects an employeeId that doesn't exist in HR")
    void addPersonnel_unknownEmployee_throws() {
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(sampleTender()));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty());

        var req = new AddTenderPersonnelRequest(employeeId, "Project Manager");

        assertThatThrownBy(() -> service().addPersonnel(TENANT, tenderId, req, USER))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    @DisplayName("addPersonnel rejects a tender that doesn't belong to this tenant")
    void addPersonnel_unknownTender_throws() {
        UUID tenderId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.empty());

        var req = new AddTenderPersonnelRequest(UUID.randomUUID(), "Project Manager");

        assertThatThrownBy(() -> service().addPersonnel(TENANT, tenderId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("addPersonnel succeeds and returns the employee's current HR details, not a copy")
    void addPersonnel_validEmployee_returnsEnrichedResponse() {
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(sampleTender()));
        when(hrFacade.findEmployeeById(TENANT, employeeId))
                .thenReturn(Optional.of(employee(employeeId, "Thabo Mokoena", "EMP-0042")));

        var req = new AddTenderPersonnelRequest(employeeId, "Project Manager");
        var response = service().addPersonnel(TENANT, tenderId, req, USER);

        assertThat(response.employeeFound()).isTrue();
        assertThat(response.employeeFullName()).isEqualTo("Thabo Mokoena");
        assertThat(response.employeeNumber()).isEqualTo("EMP-0042");
        assertThat(response.role()).isEqualTo("Project Manager");
    }

    @Test
    @DisplayName("getPersonnel returns employeeFound=false, not an exception, for a since-deleted employee")
    void getPersonnel_deletedEmployee_returnsNotFoundFlagRatherThanThrowing() {
        UUID tenderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        TenderPersonnel personnel = TenderPersonnel.create(TENANT, tenderId, employeeId, "Site Agent", USER);

        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(sampleTender()));
        when(personnelRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(personnel));
        when(hrFacade.findEmployeeById(TENANT, employeeId)).thenReturn(Optional.empty()); // employee since removed

        var results = service().getPersonnel(TENANT, tenderId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).employeeFound()).isFalse();
        assertThat(results.get(0).employeeFullName()).isNull();
        assertThat(results.get(0).role()).isEqualTo("Site Agent"); // the role itself is still ours, unaffected
    }
}
