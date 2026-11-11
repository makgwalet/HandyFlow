package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceEvidence;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.model.FuelLegalEntity;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ComplianceLinkageDtos.*;
import za.co.handyflow.platform.fuel.dto.ComplianceRequest;
import za.co.handyflow.platform.fuel.dto.ComplianceResponse;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelLicenceLinkageServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Johannesburg"));

    @Mock FuelComplianceItemRepository items;
    @Mock FuelComplianceRenewalRepository renewals;
    @Mock FuelTankerRepository tankers;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    @Mock HrFacade hr;
    @Mock FuelLegalEntityRepository legalEntities;
    @Mock FuelComplianceEvidenceRepository evidence;
    @InjectMocks FuelComplianceService service;

    FuelLegalEntity acme, beta;

    @BeforeEach
    void setUp() {
        acme = FuelLegalEntity.create(T, "Acme Fuels (Pty) Ltd", null, null, null, null, USER);
        beta = FuelLegalEntity.create(T, "Beta Transport (Pty) Ltd", null, null, null, null, USER);
        when(legalEntities.findAllActive(T)).thenReturn(List.of(acme, beta));
        when(legalEntities.findActiveById(T, acme.getId())).thenReturn(Optional.of(acme));
        when(items.save(any())).thenAnswer(i -> i.getArgument(0));
        when(evidence.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ComplianceRequest req(UUID entity, String activity) {
        return new ComplianceRequest("LICENCE", "Wholesale licence", "WL-1", "COMPANY", null, null, null, TODAY.plusDays(90), null, entity, activity, "Regulator X");
    }

    @Test @DisplayName("creating a licence links the entity, activity and authority and returns the entity's name")
    void createLinked() {
        ComplianceResponse r = service.create(T, req(acme.getId(), "WHOLESALE"));
        assertEquals(acme.getId(), r.legalEntityId());
        assertEquals("Acme Fuels (Pty) Ltd", r.legalEntityName());
        assertEquals("WHOLESALE", r.licensedActivity());
        assertEquals("Regulator X", r.issuingAuthority());
    }

    @Test @DisplayName("a legal entity from another tenant (or unknown) is a 404 and nothing is saved")
    void foreignEntityRefused() {
        UUID other = UUID.randomUUID();
        when(legalEntities.findActiveById(T, other)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, req(other, "WHOLESALE")));
        verify(items, never()).save(any());
    }

    @Test @DisplayName("the old request shape (no linkage) still works and links nothing")
    void oldRequestShape() {
        ComplianceResponse r = service.create(T, new ComplianceRequest("LICENCE", "Old licence", null, "COMPANY", null, null, null, TODAY.plusDays(10), null));
        assertNull(r.legalEntityId()); assertNull(r.licensedActivity());
    }

    @Test @DisplayName("coverage counts licences per entity by status, lists activities, and counts unlinked licences; other item types are ignored")
    void coverage() {
        FuelComplianceItem valid = FuelComplianceItem.create(T, "LICENCE", "A", null, "COMPANY", null, "Company", null, TODAY.plusDays(200), null);
        valid.applyScope(acme.getId(), "WHOLESALE", null);
        FuelComplianceItem expiring = FuelComplianceItem.create(T, "LICENCE", "B", null, "COMPANY", null, "Company", null, TODAY.plusDays(5), null);
        expiring.applyScope(acme.getId(), "STORAGE", null);
        FuelComplianceItem expired = FuelComplianceItem.create(T, "LICENCE", "C", null, "COMPANY", null, "Company", null, TODAY.minusDays(2), null);
        expired.applyScope(acme.getId(), "WHOLESALE", null);
        FuelComplianceItem unlinked = FuelComplianceItem.create(T, "LICENCE", "D", null, "COMPANY", null, "Company", null, TODAY.plusDays(100), null);
        FuelComplianceItem cert = FuelComplianceItem.create(T, "CERTIFICATE", "E", null, "COMPANY", null, "Company", null, TODAY.plusDays(100), null);
        cert.applyScope(beta.getId(), null, null);
        when(items.findAllActive(T)).thenReturn(List.of(valid, expiring, expired, unlinked, cert));
        Coverage c = service.coverage(T);
        CoverageRow a = c.entities().stream().filter(r -> r.name().startsWith("Acme")).findFirst().orElseThrow();
        assertEquals(1, a.valid()); assertEquals(1, a.expiring()); assertEquals(1, a.expired());
        assertEquals(List.of("STORAGE", "WHOLESALE"), a.activities());
        CoverageRow b = c.entities().stream().filter(r -> r.name().startsWith("Beta")).findFirst().orElseThrow();
        assertEquals(0, b.valid() + b.expiring() + b.expired());
        assertEquals(1, c.unlinkedLicences());
    }

    @Test @DisplayName("evidence can be added to an existing item only, and replaced with a reason")
    void evidenceFlow() {
        FuelComplianceItem i = FuelComplianceItem.create(T, "LICENCE", "A", null, "COMPANY", null, "Company", null, TODAY.plusDays(200), null);
        UUID itemId = i.getId();
        when(items.findActiveById(T, itemId)).thenReturn(Optional.of(i));
        EvidenceView v = service.addEvidence(T, itemId, new EvidenceRequest("scans/a.pdf", null), USER);
        assertEquals("ACTIVE", v.status());
        assertThrows(ResourceNotFoundException.class, () -> service.addEvidence(T, UUID.randomUUID(), new EvidenceRequest("scans/a.pdf", null), USER));
        FuelComplianceEvidence stored = FuelComplianceEvidence.create(T, itemId, "scans/a.pdf", null, USER);
        UUID evId = stored.getId();
        when(evidence.findByIdForUpdate(T, evId)).thenReturn(Optional.of(stored));
        assertThrows(IllegalArgumentException.class, () -> service.supersedeEvidence(T, evId, "no", USER));
        assertEquals("SUPERSEDED", service.supersedeEvidence(T, evId, "Wrong scan uploaded", USER).status());
    }

    @Test @DisplayName("an unknown item has no evidence list (404)")
    void unknownItem() {
        assertThrows(ResourceNotFoundException.class, () -> service.listEvidence(T, UUID.randomUUID()));
    }
}
