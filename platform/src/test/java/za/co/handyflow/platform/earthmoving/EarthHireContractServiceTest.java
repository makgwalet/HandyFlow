package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.contracting.application.ContractSummary;
import za.co.handyflow.platform.contracting.application.ContractingFacade;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.earthmoving.application.internal.*;
import za.co.handyflow.platform.earthmoving.domain.model.*;
import za.co.handyflow.platform.earthmoving.domain.repository.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EarthHireContractServiceTest {
    EarthDeploymentRepository deployments = mock(EarthDeploymentRepository.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    ContractingFacade contracting = mock(ContractingFacade.class);
    CrmFacade crm = mock(CrmFacade.class);
    EarthSettingsService settings = mock(EarthSettingsService.class);
    AuditTrailFacade trail = mock(AuditTrailFacade.class);
    Set<String> granted = new HashSet<>();
    EarthmovingAccess access = new EarthmovingAccess() {
        @Override public boolean has(String authority) { return granted.contains(authority); }
    };
    EarthHireContractService service;
    TenantId tenant = TenantId.generate();
    UUID actor = UUID.randomUUID();
    UUID customer = UUID.randomUUID();
    static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    static final LocalDate TO = FROM.plusDays(9);
    EarthAsset asset;
    EarthDeployment deployment;

    @BeforeEach void setUp() {
        service = new EarthHireContractService(deployments, assets, contracting, crm, settings, access, trail);
        asset = EarthAsset.create(tenant, "Bulldozer", "D9-001", "DOZER", "Caterpillar", "D9T", 2020, "SN-77", "GP1",
                "OWN", null, null, null, new BigDecimal("1000"), null, null);
        deployment = EarthDeployment.create(tenant, asset.getId(), "Quarry", "Acme", null, null, FROM, TO, null)
                .withLinks(null, customer, null);
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
        when(deployments.findByTenantAndId(tenant, deployment.getId())).thenReturn(Optional.of(deployment));
        when(contracting.findByReference(any(TenantId.class), any(), any())).thenReturn(List.of());
        when(settings.contractThreshold(tenant)).thenReturn(new BigDecimal("5000"));
        when(crm.findCustomerById(tenant, customer)).thenReturn(Optional.of(
                new CustomerSummary(customer, "Acme Mining", null, null, null, null, null, null)));
        granted.addAll(List.of("EARTHMOVING_READ", "EARTHMOVING_DEPLOY"));
    }

    private ContractSummary summary(String number, String status, UUID parent) {
        return new ContractSummary(UUID.randomUUID(), number, "t", "EQUIPMENT_HIRE", status, FROM, TO, null, false, parent);
    }

    @Test void aBigHireWithNoContractIsExpectedToHaveOne() {
        var r = service.forDeployment(tenant, deployment.getId());
        assertNull(r.contract());
        assertTrue(r.expected());
        assertEquals(new BigDecimal("10000.00"), r.expectedValue());
        assertNotNull(r.warning());
    }
    @Test void aSignedContractClearsTheWarning() {
        ContractSummary c = summary("CON-0001", "SIGNED", null);
        deployment.linkContract(c.id());
        when(contracting.findByReference(any(TenantId.class), eq("EARTHMOVING_DEPLOYMENT"), eq(deployment.getId()))).thenReturn(List.of(c));
        var r = service.forDeployment(tenant, deployment.getId());
        assertEquals("CON-0001", r.contract().number());
        assertNull(r.warning());
    }
    @Test void aDeploymentNotForACustomerIsNeverExpectedToHaveAContract() {
        EarthDeployment typed = EarthDeployment.create(tenant, asset.getId(), "Quarry", "Walk-in", null, null, FROM, TO, null);
        when(deployments.findByTenantAndId(tenant, typed.getId())).thenReturn(Optional.of(typed));
        var r = service.forDeployment(tenant, typed.getId());
        assertFalse(r.expected());
        assertNull(r.warning());
    }
    @Test void anUnknownDeploymentIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.forDeployment(tenant, UUID.randomUUID()));
    }

    @Test void draftingFillsInWhatIsKnownAndLinksIt() {
        ContractSummary made = summary("CON-0002", "DRAFT", null);
        when(contracting.createDraft(any(TenantId.class), any(), any())).thenReturn(made);
        when(contracting.findByReference(any(TenantId.class), any(), any())).thenReturn(List.of(made));
        var r = service.createDraft(tenant, deployment.getId(), actor);
        assertEquals(made.id(), deployment.getContractId());
        assertEquals("CON-0002", r.contract().number());
        var cap = ArgumentCaptor.forClass(ContractingFacade.DraftContract.class);
        verify(contracting).createDraft(any(TenantId.class), cap.capture(), eq(actor));
        var d = cap.getValue();
        assertEquals("EQUIPMENT_HIRE", d.contractType());
        assertEquals("Acme Mining", d.variables().get("hirer_name"));
        assertEquals("SN-77", d.variables().get("serial_number"));
        assertEquals("1000", d.variables().get("daily_rate"));
        assertEquals("10000.00", d.variables().get("total_amount"));
        assertEquals(deployment.getId(), d.refId());
        assertEquals("EARTHMOVING_DEPLOYMENT", d.refType());
        verify(deployments).save(deployment);
        verify(trail).record(any(TenantId.class), eq("EARTHMOVING_HIRE_CONTRACT_DRAFTED"), any(), any(), anyMap(), anyMap());
    }
    @Test void aSecondDraftIsRefused() {
        deployment.linkContract(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> service.createDraft(tenant, deployment.getId(), actor));
        verify(contracting, never()).createDraft(any(TenantId.class), any(), any());
    }
    @Test void aDeploymentWithoutACustomerCannotGetADraft() {
        EarthDeployment typed = EarthDeployment.create(tenant, asset.getId(), "Quarry", "Walk-in", null, null, FROM, TO, null);
        when(deployments.findByTenantAndId(tenant, typed.getId())).thenReturn(Optional.of(typed));
        assertThrows(IllegalArgumentException.class, () -> service.createDraft(tenant, typed.getId(), actor));
    }
    @Test void draftingAndLinkingNeedDeployPermission() {
        granted.remove("EARTHMOVING_DEPLOY");
        assertThrows(HandyFlowException.class, () -> service.createDraft(tenant, deployment.getId(), actor));
        assertThrows(HandyFlowException.class, () -> service.link(tenant, deployment.getId(), UUID.randomUUID(), actor));
        assertThrows(HandyFlowException.class, () -> service.createAddendum(tenant, deployment.getId(), TO.plusDays(3), "Rain delays", actor));
    }

    @Test void linkingNeedsAContractThatExists() {
        UUID id = UUID.randomUUID();
        when(contracting.findById(tenant, id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.link(tenant, deployment.getId(), id, actor));
        assertNull(deployment.getContractId());
    }
    @Test void linkingStoresTheReference() {
        ContractSummary c = summary("CON-0003", "SENT", null);
        when(contracting.findById(tenant, c.id())).thenReturn(Optional.of(c));
        service.link(tenant, deployment.getId(), c.id(), actor);
        assertEquals(c.id(), deployment.getContractId());
        verify(deployments).save(deployment);
    }

    @Test void anExtensionBecomesAnAddendumUnderTheContractAndLeavesTheEndDateAlone() {
        ContractSummary original = summary("CON-0004", "SIGNED", null);
        deployment.linkContract(original.id());
        when(contracting.findById(tenant, original.id())).thenReturn(Optional.of(original));
        ContractSummary add = summary("CON-0005", "DRAFT", original.id());
        when(contracting.createDraft(any(TenantId.class), any(), any())).thenReturn(add);
        when(contracting.findByReference(any(TenantId.class), any(), any())).thenReturn(List.of(original, add));
        var r = service.createAddendum(tenant, deployment.getId(), TO.plusDays(7), "Rain delays", actor);
        assertEquals(1, r.addenda().size());
        assertEquals("CON-0005", r.addenda().get(0).number());
        assertEquals(TO, deployment.getPlannedEndDate());
        var cap = ArgumentCaptor.forClass(ContractingFacade.DraftContract.class);
        verify(contracting).createDraft(any(TenantId.class), cap.capture(), eq(actor));
        assertEquals(original.id(), cap.getValue().parentContractId());
        assertEquals("EQUIPMENT_HIRE_ADDENDUM", cap.getValue().contractType());
        assertTrue(cap.getValue().body().contains("CON-0004"));
    }
    @Test void anExtensionNeedsAContractAReasonAndALaterDate() {
        assertThrows(IllegalStateException.class, () -> service.createAddendum(tenant, deployment.getId(), TO.plusDays(3), "Rain delays", actor));
        ContractSummary original = summary("CON-0004", "SIGNED", null);
        deployment.linkContract(original.id());
        when(contracting.findById(tenant, original.id())).thenReturn(Optional.of(original));
        assertThrows(IllegalArgumentException.class, () -> service.createAddendum(tenant, deployment.getId(), TO.plusDays(3), "no", actor));
        assertThrows(IllegalArgumentException.class, () -> service.createAddendum(tenant, deployment.getId(), TO, "Rain delays", actor));
        assertThrows(IllegalArgumentException.class, () -> service.createAddendum(tenant, deployment.getId(), null, "Rain delays", actor));
        verify(contracting, never()).createDraft(any(TenantId.class), any(), any());
    }
    @Test void anEndedDeploymentCannotBeExtended() {
        deployment.linkContract(UUID.randomUUID());
        deployment.close("Done");
        assertThrows(IllegalStateException.class, () -> service.createAddendum(tenant, deployment.getId(), TO.plusDays(3), "Rain delays", actor));
    }
}
