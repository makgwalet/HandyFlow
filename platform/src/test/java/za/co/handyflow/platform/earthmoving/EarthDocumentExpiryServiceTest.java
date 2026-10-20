package za.co.handyflow.platform.earthmoving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.earthmoving.application.internal.EarthAlertService;
import za.co.handyflow.platform.earthmoving.application.internal.EarthDocumentExpiryService;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EarthDocumentExpiryServiceTest {
    DocumentsFacade documents = mock(DocumentsFacade.class);
    EarthAssetRepository assets = mock(EarthAssetRepository.class);
    EarthAlertService alerts = mock(EarthAlertService.class);
    EarthDocumentExpiryService service;
    TenantId tenant = TenantId.generate();
    LocalDate today = LocalDate.of(2026, 10, 20);
    EarthAsset asset;

    @BeforeEach
    void setUp() {
        service = new EarthDocumentExpiryService(documents, assets, alerts);
        asset = EarthAsset.create(tenant, "CAT D9", "D9-001", "DOZER", null, null, null, null, null, "OWN", null, null, null, null, null, null);
        when(assets.findActiveById(tenant, asset.getId())).thenReturn(Optional.of(asset));
    }

    DocumentInfo doc(String category, LocalDate expires, String ownerType) {
        return new DocumentInfo(UUID.randomUUID(), ownerType, asset.getId(), category, "Policy 77", null, expires, "ACTIVE", null, 1, null, Instant.now(), null);
    }

    @Test void documentsNearExpiryRaiseOneAlertEachAtTheirStage() {
        DocumentInfo soon = doc("WARRANTY", today.plusDays(20), "EARTH_ASSET"), week = doc("INSURANCE", today.plusDays(3), "EARTH_ASSET");
        when(documents.expiring(eq(tenant), any(), any())).thenReturn(List.of(soon, week));
        service.scan(tenant, today);
        verify(alerts).raise(eq(tenant), eq("DOCUMENT_EXPIRY"), eq("WARNING"), eq(asset.getId()), eq("DOCUMENT"), eq(soon.id()), any(), any(),
                eq("DOC_EXPIRY:" + soon.id() + ":" + soon.expiresOn() + ":DUE_30"), any());
        verify(alerts).raise(eq(tenant), eq("DOCUMENT_EXPIRY"), eq("WARNING"), eq(asset.getId()), eq("DOCUMENT"), eq(week.id()), any(), any(),
                eq("DOC_EXPIRY:" + week.id() + ":" + week.expiresOn() + ":DUE_7"), any());
    }

    @Test void anExpiredInsurancePolicyIsCriticalAndSettlesTheEarlierWarnings() {
        DocumentInfo gone = doc("INSURANCE", today.minusDays(2), "EARTH_ASSET");
        when(documents.expiring(eq(tenant), any(), any())).thenReturn(List.of(gone));
        service.scan(tenant, today);
        verify(alerts).raise(eq(tenant), eq("DOCUMENT_EXPIRY"), eq("CRITICAL"), any(), any(), eq(gone.id()), any(), any(), contains(":EXPIRED"), any());
        verify(alerts).resolveByKey(eq(tenant), contains(":DUE_30"), any(), any());
        verify(alerts).resolveByKey(eq(tenant), contains(":DUE_7"), any(), any());
    }

    @Test void anOperatorCertificateAlertHasNoMachine() {
        DocumentInfo cert = doc("OPERATOR_CERTIFICATE", today.plusDays(2), "EARTH_OPERATOR");
        when(documents.expiring(eq(tenant), any(), any())).thenReturn(List.of(cert));
        service.scan(tenant, today);
        verify(alerts).raise(eq(tenant), eq("DOCUMENT_EXPIRY"), eq("WARNING"), isNull(), eq("DOCUMENT"), eq(cert.id()), any(), any(), any(), any());
    }

    @Test void oneCompanyFailingDoesNotStopTheOthers() {
        TenantId bad = TenantId.generate();
        when(documents.tenantsWithExpiring(any(), any())).thenReturn(List.of(bad, tenant));
        when(documents.expiring(eq(bad), any(), any())).thenThrow(new RuntimeException("boom"));
        when(documents.expiring(eq(tenant), any(), any())).thenReturn(List.of(doc("WARRANTY", today.plusDays(5), "EARTH_ASSET")));
        org.junit.jupiter.api.Assertions.assertEquals(1, service.sweep(today));
    }
}
